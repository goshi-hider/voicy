package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.UUID;

public record SpeakerTestPayload(
		UUID speakerId,
		double x,
		double y,
		double z,
		byte[] pcmData
) implements CustomPayload {
	public static final CustomPayload.Id<SpeakerTestPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("speaker_test"));

	public static final PacketCodec<RegistryByteBuf, SpeakerTestPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.STRING.xmap(UUID::fromString, UUID::toString).cast(), SpeakerTestPayload::speakerId,
					PacketCodecs.DOUBLE, payload -> payload.x,
					PacketCodecs.DOUBLE, payload -> payload.y,
					PacketCodecs.DOUBLE, payload -> payload.z,
					PacketCodecs.BYTE_ARRAY, SpeakerTestPayload::pcmData,
					SpeakerTestPayload::new
			);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
