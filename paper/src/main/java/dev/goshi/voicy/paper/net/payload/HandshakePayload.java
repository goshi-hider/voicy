package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

public record HandshakePayload(
		TransportMode transportMode,
		int udpPort,
		int sampleRate,
		int frameDurationMs,
		byte[] sessionSecret,
		String udpHost
) {

	public enum TransportMode {
		FABRIC_PAYLOAD,
		UDP_FALLBACK
	}

	public void encode(VoicyByteBuf buf) {
		buf.writeString(transportMode.name());
		buf.writeVarInt(udpPort);
		buf.writeVarInt(sampleRate);
		buf.writeVarInt(frameDurationMs);
		buf.writeByteArray(sessionSecret);
		buf.writeString(udpHost);
	}

	public static HandshakePayload decode(VoicyByteBuf buf) {
		TransportMode mode = TransportMode.valueOf(buf.readString());
		int udpPort = buf.readVarInt();
		int sampleRate = buf.readVarInt();
		int frameDurationMs = buf.readVarInt();
		byte[] secret = buf.readByteArray();
		String udpHost = buf.readString();
		return new HandshakePayload(mode, udpPort, sampleRate, frameDurationMs, secret, udpHost);
	}
}
