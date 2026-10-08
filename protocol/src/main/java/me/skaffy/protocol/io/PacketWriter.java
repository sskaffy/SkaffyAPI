package me.skaffy.protocol.io;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.function.BiConsumer;

import me.skaffy.protocol.ProtocolException;

public final class PacketWriter {
	private byte[] buffer;
	private int size;

	public PacketWriter() {
		this(64);
	}

	public PacketWriter(int initialCapacity) {
		this.buffer = new byte[Math.max(16, initialCapacity)];
	}

	public PacketWriter writeByte(int value) {
		ensureCapacity(1);
		buffer[size++] = (byte) value;
		return this;
	}

	public PacketWriter writeBoolean(boolean value) {
		return writeByte(value ? 1 : 0);
	}

	public PacketWriter writeVarInt(int value) {
		while ((value & ~0x7F) != 0) {
			writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}

		return writeByte(value);
	}

	public PacketWriter writeVarLong(long value) {
		while ((value & ~0x7FL) != 0) {
			writeByte((int) (value & 0x7F) | 0x80);
			value >>>= 7;
		}

		return writeByte((int) value);
	}

	public PacketWriter writeShort(int value) {
		writeByte(value >>> 8);
		return writeByte(value);
	}

	public PacketWriter writeInt(int value) {
		ensureCapacity(4);
		buffer[size++] = (byte) (value >>> 24);
		buffer[size++] = (byte) (value >>> 16);
		buffer[size++] = (byte) (value >>> 8);
		buffer[size++] = (byte) value;
		return this;
	}

	public PacketWriter writeLong(long value) {
		writeInt((int) (value >>> 32));
		return writeInt((int) value);
	}

	public PacketWriter writeFloat(float value) {
		return writeInt(Float.floatToIntBits(value));
	}

	public PacketWriter writeDouble(double value) {
		return writeLong(Double.doubleToLongBits(value));
	}

	public PacketWriter writeString(String value, int maxLength) {
		if (value.length() > maxLength) {
			throw new ProtocolException("String is " + value.length() + " characters, max is " + maxLength);
		}

		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

		if (bytes.length > maxLength * 3) {
			throw new ProtocolException("String is " + bytes.length + " bytes, max is " + maxLength * 3);
		}

		writeVarInt(bytes.length);
		return writeRawBytes(bytes, 0, bytes.length);
	}

	public PacketWriter writeBytes(byte[] value) {
		return writeBytes(value, 0, value.length);
	}

	public PacketWriter writeBytes(byte[] value, int offset, int length) {
		writeVarInt(length);
		return writeRawBytes(value, offset, length);
	}

	public PacketWriter writeRawBytes(byte[] value, int offset, int length) {
		ensureCapacity(length);
		System.arraycopy(value, offset, buffer, size, length);
		size += length;
		return this;
	}

	public PacketWriter writeUuid(UUID value) {
		writeLong(value.getMostSignificantBits());
		return writeLong(value.getLeastSignificantBits());
	}

	public <T> PacketWriter writeList(Collection<T> values, BiConsumer<PacketWriter, T> elementWriter) {
		writeVarInt(values.size());

		for (T value : values) {
			elementWriter.accept(this, value);
		}

		return this;
	}

	public int size() {
		return size;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(buffer, size);
	}

	private void ensureCapacity(int extra) {
		if (size + extra > buffer.length) {
			buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, size + extra));
		}
	}
}
