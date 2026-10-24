package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record HandshakePayload(
		TransportMode transportMode,
		int udpPort,
		int sampleRate,
		int frameDurationMs,
		byte[] sessionSecret,
		String udpHost
) implements CustomPayload {
	public enum TransportMode {
		FABRIC_PAYLOAD,
		UDP_FALLBACK
	}

	public static final CustomPayload.Id<HandshakePayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("handshake"));

	public static final PacketCodec<RegistryByteBuf, HandshakePayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING.xmap(HandshakePayload::parseMode, TransportMode::name),
							HandshakePayload::transportMode,
					PacketCodecs.VAR_INT, HandshakePayload::udpPort,
					PacketCodecs.VAR_INT, HandshakePayload::sampleRate,
					PacketCodecs.VAR_INT, HandshakePayload::frameDurationMs,
					PacketCodecs.BYTE_ARRAY, HandshakePayload::sessionSecret,
					PacketCodecs.STRING, HandshakePayload::udpHost,
					HandshakePayload::new
			);

	private static TransportMode parseMode(String name) {
		return TransportMode.valueOf(name);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
