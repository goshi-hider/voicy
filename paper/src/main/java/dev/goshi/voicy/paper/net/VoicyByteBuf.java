package dev.goshi.voicy.paper.net;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class VoicyByteBuf {
	private byte[] data;
	private int writerIndex;
	private int readerIndex;

	public VoicyByteBuf() {
		this(32);
	}

	public VoicyByteBuf(int initialCapacity) {
		this.data = new byte[Math.max(16, initialCapacity)];
	}

	public static VoicyByteBuf wrap(byte[] existing) {
		VoicyByteBuf buf = new VoicyByteBuf(0);
		buf.data = existing;
		buf.writerIndex = existing.length;
		buf.readerIndex = 0;
		return buf;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(data, writerIndex);
	}

	public int readableBytes() {
		return writerIndex - readerIndex;
	}

	private void ensureCapacity(int extra) {
		int required = writerIndex + extra;
		if (required <= data.length) {
			return;
		}
		int newCap = Math.max(data.length, 16);
		while (newCap < required) {
			newCap *= 2;
		}
		data = Arrays.copyOf(data, newCap);
	}

	private void requireReadable(int length) {
		if (readableBytes() < length) {
			throw new IllegalStateException("Not enough bytes to read: need " + length + ", have " + readableBytes());
		}
	}

	public void writeByte(int value) {
		ensureCapacity(1);
		data[writerIndex++] = (byte) value;
	}

	public int readByte() {
		requireReadable(1);
		return data[readerIndex++] & 0xFF;
	}

	public void writeBoolean(boolean value) {
		writeByte(value ? 1 : 0);
	}

	public boolean readBoolean() {
		return readByte() != 0;
	}

	public void writeInt(int value) {
		ensureCapacity(4);
		data[writerIndex++] = (byte) (value >>> 24);
		data[writerIndex++] = (byte) (value >>> 16);
		data[writerIndex++] = (byte) (value >>> 8);
		data[writerIndex++] = (byte) value;
	}

	public int readInt() {
		requireReadable(4);
		int value = ((data[readerIndex] & 0xFF) << 24)
				| ((data[readerIndex + 1] & 0xFF) << 16)
				| ((data[readerIndex + 2] & 0xFF) << 8)
				| (data[readerIndex + 3] & 0xFF);
		readerIndex += 4;
		return value;
	}

	public void writeLong(long value) {
		ensureCapacity(8);
		for (int shift = 56; shift >= 0; shift -= 8) {
			data[writerIndex++] = (byte) (value >>> shift);
		}
	}

	public long readLong() {
		requireReadable(8);
		long value = 0L;
		for (int i = 0; i < 8; i++) {
			value = (value << 8) | (data[readerIndex + i] & 0xFFL);
		}
		readerIndex += 8;
		return value;
	}

	public void writeFloat(float value) {
		writeInt(Float.floatToIntBits(value));
	}

	public float readFloat() {
		return Float.intBitsToFloat(readInt());
	}

	public void writeDouble(double value) {
		writeLong(Double.doubleToLongBits(value));
	}

	public double readDouble() {
		return Double.longBitsToDouble(readLong());
	}

	public void writeVarInt(int value) {
		while (true) {
			if ((value & ~0x7F) == 0) {
				writeByte(value);
				return;
			}
			writeByte((value & 0x7F) | 0x80);
			value >>>= 7;
		}
	}

	public int readVarInt() {
		int value = 0;
		int shift = 0;
		while (true) {
			byte b = (byte) readByte();
			value |= (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
			shift += 7;
			if (shift >= 32) {
				throw new IllegalStateException("VarInt too big");
			}
		}
	}

	public void writeVarLong(long value) {
		while (true) {
			if ((value & ~0x7FL) == 0) {
				writeByte((int) value);
				return;
			}
			writeByte((int) ((value & 0x7F) | 0x80));
			value >>>= 7;
		}
	}

	public long readVarLong() {
		long value = 0L;
		int shift = 0;
		while (true) {
			byte b = (byte) readByte();
			value |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return value;
			}
			shift += 7;
			if (shift >= 64) {
				throw new IllegalStateException("VarLong too big");
			}
		}
	}

	public void writeString(String value) {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		writeVarInt(bytes.length);
		ensureCapacity(bytes.length);
		System.arraycopy(bytes, 0, data, writerIndex, bytes.length);
		writerIndex += bytes.length;
	}

	public String readString() {
		int length = readVarInt();
		requireReadable(length);
		String value = new String(data, readerIndex, length, StandardCharsets.UTF_8);
		readerIndex += length;
		return value;
	}

	public void writeByteArray(byte[] value) {
		writeVarInt(value.length);
		ensureCapacity(value.length);
		System.arraycopy(value, 0, data, writerIndex, value.length);
		writerIndex += value.length;
	}

	public byte[] readByteArray() {
		int length = readVarInt();
		requireReadable(length);
		byte[] value = Arrays.copyOfRange(data, readerIndex, readerIndex + length);
		readerIndex += length;
		return value;
	}

	public void writeUuid(UUID value) {
		writeLong(value.getMostSignificantBits());
		writeLong(value.getLeastSignificantBits());
	}

	public UUID readUuid() {
		long msb = readLong();
		long lsb = readLong();
		return new UUID(msb, lsb);
	}

	public void writeUuidAsString(UUID value) {
		writeString(value.toString());
	}

	public UUID readUuidAsString() {
		return UUID.fromString(readString());
	}
}
