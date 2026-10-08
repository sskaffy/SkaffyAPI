package me.skaffy.protocol.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.asset.AssetHash;
import me.skaffy.protocol.asset.AssetStore;
import me.skaffy.protocol.asset.CachedAsset;
import me.skaffy.protocol.core.ClientboundCorePacket;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetData;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetDelete;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetQuery;
import me.skaffy.protocol.core.ClientboundCorePacket.AssetTransfer;
import me.skaffy.protocol.core.ClientboundCorePacket.RegistrationEnd;
import me.skaffy.protocol.core.ClientboundCorePacket.Welcome;
import me.skaffy.protocol.core.CoreCodec;
import me.skaffy.protocol.core.FeatureRange;
import me.skaffy.protocol.core.FeatureVersion;
import me.skaffy.protocol.core.ServerboundCorePacket;
import me.skaffy.protocol.core.ServerboundCorePacket.AssetAck;
import me.skaffy.protocol.core.ServerboundCorePacket.AssetReport;
import me.skaffy.protocol.core.ServerboundCorePacket.Failed;
import me.skaffy.protocol.core.ServerboundCorePacket.Hello;
import me.skaffy.protocol.core.ServerboundCorePacket.Ready;

public final class ClientSession {
	public enum State {
		HELLO_SENT,
		REGISTERING,
		LOADING,
		READY,
		FAILED
	}

	public interface Listener {
		default void onWelcome(List<FeatureVersion> features) {
		}

		default void onProgress(long receivedBytes, long expectedBytes) {
		}

		void onRegistrationEnd();

		void onFailed(String reason);
	}

	private static final int REPORT_BUDGET = Protocol.MAX_SERVERBOUND_PAYLOAD - 512;

	private final List<FeatureRange> offeredFeatures;
	private final AssetStore store;
	private final PacketSink sink;
	private final Listener listener;

	private State state = State.HELLO_SENT;
	private String failureReason;
	private List<FeatureVersion> features = List.of();

	private Transfer transfer;
	private long receivedBytes;
	private long expectedBytes;

	public ClientSession(List<FeatureRange> offeredFeatures, AssetStore store, PacketSink sink, Listener listener) {
		this.offeredFeatures = List.copyOf(offeredFeatures);
		this.store = store;
		this.sink = sink;
		this.listener = listener;
	}

	public void start(String modVersion) {
		send(new Hello(Protocol.VERSION, modVersion, offeredFeatures));
	}

	public void handle(byte[] data) {
		ClientboundCorePacket packet;

		try {
			packet = CoreCodec.decodeClientbound(data);
		} catch (ProtocolException e) {
			fail("Malformed packet from server: " + e.getMessage());
			return;
		}

		handle(packet);
	}

	public synchronized void handle(ClientboundCorePacket packet) {
		if (state == State.READY || state == State.FAILED) {
			return;
		}

		try {
			switch (packet) {
				case Welcome welcome -> onWelcome(welcome);
				case AssetQuery query -> onQuery(query);
				case AssetTransfer announced -> onTransfer(announced);
				case AssetData data -> onData(data);
				case AssetDelete delete -> onDelete(delete);
				case RegistrationEnd ignored -> onRegistrationEnd();
			}
		} catch (ProtocolException e) {
			fail(e.getMessage());
		}
	}

	public synchronized void ready() {
		if (state != State.LOADING) {
			return;
		}

		state = State.READY;
		send(new Ready());
	}

	public synchronized void keepAlive() {
		if (state == State.LOADING) {
			send(new AssetAck(receivedBytes));
		}
	}

	public void fail(String reason) {
		synchronized (this) {
			if (!abandon(reason)) {
				return;
			}

			send(new Failed(reason));
		}

		listener.onFailed(reason);
	}

	public synchronized boolean abandon(String reason) {
		if (state == State.READY || state == State.FAILED) {
			return false;
		}

		state = State.FAILED;
		failureReason = reason;
		transfer = null;
		return true;
	}

	private void onWelcome(Welcome welcome) {
		expectState(State.HELLO_SENT, "Welcome");

		if (welcome.protocolVersion() != Protocol.VERSION) {
			throw new ProtocolException("Server uses protocol version " + welcome.protocolVersion() + ", client uses " + Protocol.VERSION);
		}

		for (FeatureVersion feature : welcome.features()) {
			boolean offered = offeredFeatures.stream().anyMatch(range -> range.id().equals(feature.id()) && range.supports(feature.version()));

			if (!offered) {
				throw new ProtocolException("Server enabled feature " + feature.id() + " v" + feature.version() + " which the client didn't offer");
			}
		}

		features = welcome.features();
		state = State.REGISTERING;
		listener.onWelcome(features);
	}

	private void onQuery(AssetQuery query) {
		expectState(State.REGISTERING, "AssetQuery");
		List<String> ids = query.ids().isEmpty() ? store.ids() : query.ids();
		List<CachedAsset> part = new ArrayList<>();
		int partSize = 0;

		for (String id : ids) {
			long size = store.size(id);

			if (size < 0 || size > Protocol.MAX_ASSET_SIZE) {
				continue;
			}

			AssetHash hash = null;

			if (query.includeHashes()) {
				try {
					hash = store.hash(id);
				} catch (IOException e) {
					continue;
				}
			}

			int entrySize = id.length() + AssetHash.LENGTH + 8;

			if (partSize + entrySize > REPORT_BUDGET) {
				send(new AssetReport(query.queryId(), part, false));
				part = new ArrayList<>();
				partSize = 0;
			}

			part.add(new CachedAsset(id, (int) size, hash));
			partSize += entrySize;
		}

		send(new AssetReport(query.queryId(), part, true));
	}

	private void onTransfer(AssetTransfer announced) {
		expectState(State.REGISTERING, "AssetTransfer");
		expectedBytes += announced.bytes();
		listener.onProgress(receivedBytes, expectedBytes);
	}

	private void onData(AssetData data) {
		expectState(State.REGISTERING, "AssetData");

		if (data.offset() == 0) {
			if (transfer != null) {
				throw new ProtocolException("Server started " + data.id() + " before finishing " + transfer.id);
			}

			long existing = store.size(data.id());

			if (store.totalSize() - Math.max(existing, 0) + data.size() > Protocol.MAX_CACHE_SIZE) {
				throw new ProtocolException("Server files would be over the " + Protocol.MAX_CACHE_SIZE / 1024 / 1024 + " MiB limit");
			}

			if (existing < 0 && store.fileCount() >= Protocol.MAX_CACHE_FILES) {
				throw new ProtocolException("Server files would be over the " + Protocol.MAX_CACHE_FILES + " file limit");
			}

			transfer = new Transfer(data.id(), data.size());
		} else if (transfer == null || !transfer.id.equals(data.id()) || transfer.data.length != data.size() || transfer.received != data.offset()) {
			throw new ProtocolException("Server sent unexpected bytes " + data.offset() + "+" + data.data().length + " of " + data.id());
		}

		System.arraycopy(data.data(), 0, transfer.data, transfer.received, data.data().length);
		transfer.received += data.data().length;
		receivedBytes += data.data().length;

		if (transfer.received == transfer.data.length) {
			try {
				store.write(transfer.id, transfer.data);
			} catch (IOException e) {
				throw new ProtocolException("Could not save " + transfer.id + ": " + e.getMessage());
			}

			transfer = null;
		}

		send(new AssetAck(receivedBytes));
		listener.onProgress(receivedBytes, Math.max(expectedBytes, receivedBytes));
	}

	private void onDelete(AssetDelete delete) {
		expectState(State.REGISTERING, "AssetDelete");

		for (String id : delete.ids()) {
			try {
				store.delete(id);
			} catch (IOException e) {
				throw new ProtocolException("Could not delete " + id + ": " + e.getMessage());
			}
		}
	}

	private void onRegistrationEnd() {
		expectState(State.REGISTERING, "RegistrationEnd");

		if (transfer != null) {
			throw new ProtocolException("Registration ended in the middle of " + transfer.id);
		}

		state = State.LOADING;
		listener.onRegistrationEnd();
	}

	private void expectState(State expected, String packet) {
		if (state != expected) {
			throw new ProtocolException("Server sent " + packet + " while " + state);
		}
	}

	private void send(ServerboundCorePacket packet) {
		sink.send(Protocol.CORE_CHANNEL, CoreCodec.encode(packet));
	}

	public synchronized State state() {
		return state;
	}

	public synchronized String failureReason() {
		return failureReason;
	}

	public synchronized List<FeatureVersion> features() {
		return features;
	}

	public synchronized int featureVersion(String id) {
		return features.stream().filter(feature -> feature.id().equals(id)).mapToInt(FeatureVersion::version).findFirst().orElse(0);
	}

	private static final class Transfer {
		final String id;
		final byte[] data;
		int received;

		Transfer(String id, int size) {
			this.id = id;
			this.data = new byte[size];
		}
	}
}
