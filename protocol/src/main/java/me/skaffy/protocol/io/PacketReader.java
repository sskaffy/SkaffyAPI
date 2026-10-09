package me.skaffy.protocol.io;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import me.skaffy.protocol.ProtocolException;

public final class PacketReader {
	private final byte[] data;
	private final int end;
	private int position;

	public PacketReader(byte[] data) {
		this(data, 0, data.length);
	}

	public PacketReader(byte[] data, int offset, int length) {
		if (offset < 0 || length < 0 || offset + length > data.length) {
			throw new IllegalArgumentException("Range outside of array");
		}

		this.data = data;
		this.position = offset;
		this.end = offset + length;
	}

	public byte readByte() {
		require(1);
		return data[position++];
	}

	public int readUnsignedByte() {
		return readByte() & 0xFF;
	}

	public boolean readBoolean() {
		int value = readUnsignedByte();

		if (value > 1) {
			throw new ProtocolException("Invalid boolean " + value);
		}

		return value == 1;
	}

	public int readVarInt() {
		int value = 0;

		for (int i = 0; i < 5; i++) {
			int b = readUnsignedByte();
			value |= (b & 0x7F) << (i * 7);

			if ((b & 0x80) == 0) {
				return value;
			}
		}

		throw new ProtocolException("VarInt is too big");
	}

	public long readVarLong() {
		long value = 0;

		for (int i = 0; i < 10; i++) {
			int b = readUnsignedByte();
			value |= (long) (b & 0x7F) << (i * 7);

			if ((b & 0x80) == 0) {
				return value;
			}
		}

		throw new ProtocolException("VarLong is too big");
	}

	public int readVarInt(int min, int max) {
		int value = readVarInt();

		if (value < min || value > max) {
			throw new ProtocolException("Value " + value + " is outside of " + min + ".." + max);
		}

		return value;
	}

	public int readShortUnsigned() {
		return readUnsignedByte() << 8 | readUnsignedByte();
	}

	public int readInt() {
		require(4);
		int value = (data[position] & 0xFF) << 24
				| (data[position + 1] & 0xFF) << 16
				| (data[position + 2] & 0xFF) << 8
				| data[position + 3] & 0xFF;
		position += 4;
		return value;
	}

	public long readLong() {
		return (long) readInt() << 32 | readInt() & 0xFFFFFFFFL;
	}

	public float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	public String readString(int maxLength) {
		int byteLength = readVarInt(0, maxLength * 3);
		require(byteLength);
		String value = new String(data, position, byteLength, StandardCharsets.UTF_8);
		position += byteLength;

		if (value.length() > maxLength) {
			throw new ProtocolException("String is " + value.length() + " characters, max is " + maxLength);
		}

		return value;
	}

	public byte[] readBytes(int maxLength) {
		return readRawBytes(readVarInt(0, maxLength));
	}

	public byte[] readRawBytes(int length) {
		require(length);
		byte[] value = new byte[length];
		System.arraycopy(data, position, value, 0, length);
		position += length;
		return value;
	}

	public UUID readUuid() {
		return new UUID(readLong(), readLong());
	}

	public <E> E readEnum(E[] values, String what) {
		int ordinal = readUnsignedByte();

		if (ordinal >= values.length) {
			throw new ProtocolException("Unknown " + what + " " + ordinal);
		}

		return values[ordinal];
	}

	public <T> List<T> readList(int maxSize, Function<PacketReader, T> elementReader) {
		int count = readVarInt(0, maxSize);
		List<T> values = new ArrayList<>(Math.min(count, 1024));

		for (int i = 0; i < count; i++) {
			values.add(elementReader.apply(this));
		}

		return Collections.unmodifiableList(values);
	}

	public int remaining() {
		return end - position;
	}

	public void expectEnd() {
		if (position != end) {
			throw new ProtocolException(remaining() + " unexpected bytes at the end of the packet");
		}
	}

	private void require(int bytes) {
		if (bytes < 0 || bytes > end - position) {
			throw new ProtocolException("Packet ended early");
		}
	}
}
