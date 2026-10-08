package me.skaffy.protocol.asset;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

import me.skaffy.protocol.io.PacketReader;
import me.skaffy.protocol.io.PacketWriter;

public final class AssetHash {
	public static final int LENGTH = 32;

	private final byte[] bytes;

	private AssetHash(byte[] bytes) {
		this.bytes = bytes;
	}

	public static AssetHash of(byte[] data) {
		return of(data, 0, data.length);
	}

	public static AssetHash of(byte[] data, int offset, int length) {
		MessageDigest digest = sha256();
		digest.update(data, offset, length);
		return new AssetHash(digest.digest());
	}

	public static AssetHash fromBytes(byte[] raw) {
		if (raw.length != LENGTH) {
			throw new IllegalArgumentException("Hash must be " + LENGTH + " bytes, got " + raw.length);
		}

		return new AssetHash(raw.clone());
	}

	public static AssetHash fromHex(String hex) {
		try {
			return fromBytes(HexFormat.of().parseHex(hex));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid hash " + hex, e);
		}
	}

	public static AssetHash read(PacketReader reader) {
		return new AssetHash(reader.readRawBytes(LENGTH));
	}

	public void write(PacketWriter writer) {
		writer.writeRawBytes(bytes, 0, LENGTH);
	}

	public byte[] toBytes() {
		return bytes.clone();
	}

	public String toHex() {
		return HexFormat.of().formatHex(bytes);
	}

	public static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	public boolean matches(byte[] data) {
		return MessageDigest.isEqual(bytes, sha256().digest(data));
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof AssetHash hash && Arrays.equals(bytes, hash.bytes);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(bytes);
	}

	@Override
	public String toString() {
		return toHex();
	}
}
