package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

public record VoiceFramePayload(
		UUID speakerId,
		long sequence,
		float distanceRadius,
		byte[] opusData
) implements CustomPayload {

	private static final PacketCodec<RegistryByteBuf, UUID> UUID_CODEC =
			PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString).cast();

	public static final CustomPayload.Id<VoiceFramePayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("voice_frame"));

	public static final PacketCodec<RegistryByteBuf, VoiceFramePayload> CODEC =
			PacketCodec.tuple(
					UUID_CODEC, VoiceFramePayload::speakerId,
					PacketCodecs.VAR_LONG, VoiceFramePayload::sequence,
					PacketCodecs.FLOAT, VoiceFramePayload::distanceRadius,
					PacketCodecs.BYTE_ARRAY, VoiceFramePayload::opusData,
					VoiceFramePayload::new
			);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
