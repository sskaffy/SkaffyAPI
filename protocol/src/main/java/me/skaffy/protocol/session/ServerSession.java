package me.skaffy.protocol.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import me.skaffy.protocol.Protocol;
import me.skaffy.protocol.ProtocolException;
import me.skaffy.protocol.asset.CachedAsset;
import me.skaffy.protocol.asset.ServerAsset;
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

public final class ServerSession {
	public enum State {
		REGISTERING,
		LOADING,
		READY,
		FAILED
	}

	public interface Listener {
		void onReady(ServerSession session);

		void onFailed(ServerSession session, String reason);
	}

	private static final int WINDOW = 4 * 1024 * 1024;

	private final Hello hello;
	private final List<FeatureVersion> features;
	private final PacketSink sink;
	private final Listener listener;

	private State state = State.REGISTERING;
	private String failureReason;
	private volatile long lastActivity = System.nanoTime();

	private int nextQueryId;
	private final Map<Integer, PendingQuery> queries = new HashMap<>();

	private final ArrayDeque<Push> pushQueue = new ArrayDeque<>();
	private Push currentPush;
	private long sentBytes;
	private long ackedBytes;
	private boolean endRequested;

	private ServerSession(Hello hello, List<FeatureVersion> features, PacketSink sink, Listener listener) {
		this.hello = hello;
		this.features = features;
		this.sink = sink;
		this.listener = listener;
	}

	public static ServerSession start(Hello hello, List<FeatureRange> serverFeatures, PacketSink sink, Listener listener) {
		if (hello.protocolVersion() != Protocol.VERSION) {
			ServerSession session = new ServerSession(hello, List.of(), sink, listener);
			session.send(new Welcome(Protocol.VERSION, List.of()));
			session.fail("Client uses protocol version " + hello.protocolVersion() + ", server uses " + Protocol.VERSION);
			return session;
		}

		ServerSession session = new ServerSession(hello, FeatureVersion.negotiate(hello.features(), serverFeatures), sink, listener);
		session.send(new Welcome(Protocol.VERSION, session.features));
		return session;
	}

	public synchronized void sendDefinition(String channel, byte[] data) {
		requireRegistering();
		sink.send(channel, data);
	}

	public synchronized CompletableFuture<Map<String, CachedAsset>> query(Collection<String> ids, boolean includeHashes) {
		requireRegistering();
		List<String> all = List.copyOf(ids);
		List<CompletableFuture<Map<String, CachedAsset>>> parts = new ArrayList<>();

		for (int start = 0; start < Math.max(all.size(), 1); start += Protocol.MAX_QUERY_IDS) {
			List<String> part = all.subList(start, Math.min(all.size(), start + Protocol.MAX_QUERY_IDS));
			PendingQuery pending = new PendingQuery(part.isEmpty() ? null : new HashSet<>(part));
			int queryId = nextQueryId++;
			queries.put(queryId, pending);
			parts.add(pending.future);
			send(new AssetQuery(queryId, part, includeHashes));
		}

		touch();
		return CompletableFuture.allOf(parts.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
			Map<String, CachedAsset> result = new HashMap<>();
			parts.forEach(part -> result.putAll(part.join()));
			return result;
		});
	}

	public synchronized void push(String id, byte[] data) {
		requireRegistering();

		if (!Protocol.isValidAssetId(id) || data.length > Protocol.MAX_ASSET_SIZE) {
			throw new IllegalArgumentException("Invalid asset " + id + " (" + data.length + " bytes)");
		}

		pushQueue.add(new Push(id, data));
		send(new AssetTransfer(data.length));
		touch();
		pump();
	}

	public synchronized void delete(Collection<String> ids) {
		requireRegistering();
		List<String> all = List.copyOf(ids);

		for (int start = 0; start < all.size(); start += Protocol.MAX_DELETE_IDS) {
			send(new AssetDelete(all.subList(start, Math.min(all.size(), start + Protocol.MAX_DELETE_IDS))));
		}
	}

	public CompletableFuture<Void> syncAssets(Map<String, ServerAsset> assets) {
		return query(assets.keySet(), true).thenAccept(cached -> {
			for (Map.Entry<String, ServerAsset> asset : assets.entrySet()) {
				CachedAsset existing = cached.get(asset.getKey());
				ServerAsset wanted = asset.getValue();

				if (existing == null || existing.size() != wanted.data().length || !wanted.hash().equals(existing.hash())) {
					push(asset.getKey(), wanted.data());
				}
			}
		});
	}

	public synchronized void endRegistration() {
		if (state != State.REGISTERING) {
			return;
		}

		endRequested = true;
		pump();
	}

	public void handle(byte[] data) {
		ServerboundCorePacket packet;

		try {
			packet = CoreCodec.decodeServerbound(data);
		} catch (ProtocolException e) {
			fail("Malformed packet from client: " + e.getMessage());
			return;
		}

		handle(packet);
	}

	public void handle(ServerboundCorePacket packet) {
		boolean ready = false;

		synchronized (this) {
			if (state == State.READY || state == State.FAILED) {
				return;
			}

			touch();

			switch (packet) {
				case Hello ignored -> {
				}
				case AssetReport report -> onReport(report);
				case AssetAck ack -> onAck(ack);
				case Ready ignored -> {
					if (state != State.LOADING) {
						fail("Client sent Ready before registration ended");
						return;
					}

					state = State.READY;
					ready = true;
				}
				case Failed failed -> fail("Client: " + failed.reason());
			}
		}

		if (ready) {
			listener.onReady(this);
		}
	}

	public void fail(String reason) {
		List<PendingQuery> pending;

		synchronized (this) {
			if (state == State.READY || state == State.FAILED) {
				return;
			}

			state = State.FAILED;
			failureReason = reason;
			pending = new ArrayList<>(queries.values());
			queries.clear();
			pushQueue.clear();
			currentPush = null;
		}

		pending.forEach(query -> query.future.completeExceptionally(new CancellationException(reason)));
		listener.onFailed(this, reason);
	}

	private void onReport(AssetReport report) {
		PendingQuery pending = queries.get(report.queryId());

		if (pending == null) {
			fail("Client answered unknown query " + report.queryId());
			return;
		}

		for (CachedAsset asset : report.assets()) {
			if (pending.ids == null || pending.ids.contains(asset.id())) {
				pending.found.put(asset.id(), asset);
			}
		}

		if (report.last()) {
			queries.remove(report.queryId());
			pending.future.complete(pending.found);
		}
	}

	private void onAck(AssetAck ack) {
		if (ack.receivedBytes() < ackedBytes || ack.receivedBytes() > sentBytes) {
			fail("Client acknowledged " + ack.receivedBytes() + " bytes, but " + sentBytes + " were sent");
			return;
		}

		ackedBytes = ack.receivedBytes();
		pump();
	}

	private void pump() {
		while (state == State.REGISTERING && sentBytes - ackedBytes < WINDOW) {
			if (currentPush == null) {
				currentPush = pushQueue.poll();

				if (currentPush == null) {
					break;
				}
			}

			Push push = currentPush;
			int length = Math.min(Protocol.MAX_CHUNK_SIZE, push.data.length - push.offset);
			byte[] chunk = new byte[length];
			System.arraycopy(push.data, push.offset, chunk, 0, length);
			send(new AssetData(push.id, push.data.length, push.offset, chunk));
			push.offset += length;
			sentBytes += length;

			if (push.offset == push.data.length) {
				currentPush = null;
			}
		}

		if (state == State.REGISTERING && endRequested && currentPush == null && pushQueue.isEmpty()) {
			send(new RegistrationEnd());
			state = State.LOADING;
			touch();
		}
	}

	private void requireRegistering() {
		if (state != State.REGISTERING) {
			throw new IllegalStateException("Registration is over (session is " + state + ")");
		}
	}

	private void send(ClientboundCorePacket packet) {
		sink.send(Protocol.CORE_CHANNEL, CoreCodec.encode(packet));
	}

	private void touch() {
		lastActivity = System.nanoTime();
	}

	public Hello hello() {
		return hello;
	}

	public List<FeatureVersion> features() {
		return features;
	}

	public synchronized State state() {
		return state;
	}

	public synchronized String failureReason() {
		return failureReason;
	}

	public synchronized boolean isWaitingForClient() {
		return state == State.LOADING || state == State.REGISTERING && (!queries.isEmpty() || ackedBytes < sentBytes);
	}

	public long idleMillis() {
		return (System.nanoTime() - lastActivity) / 1_000_000;
	}

	private static final class PendingQuery {
		final Set<String> ids;
		final Map<String, CachedAsset> found = new HashMap<>();
		final CompletableFuture<Map<String, CachedAsset>> future = new CompletableFuture<>();

		PendingQuery(Set<String> ids) {
			this.ids = ids;
		}
	}

	private static final class Push {
		final String id;
		final byte[] data;
		int offset;

		Push(String id, byte[] data) {
			this.id = id;
			this.data = data;
		}
	}
}
