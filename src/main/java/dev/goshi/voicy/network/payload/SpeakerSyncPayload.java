package dev.goshi.voicy.network.payload;

import dev.goshi.voicy.Voicy;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record SpeakerSyncPayload(
		int speakerId,
		double x,
		double y,
		double z,
		double captureRadius,
		double broadcastRadius
) implements CustomPayload {
	public static final CustomPayload.Id<SpeakerSyncPayload> TYPE =
			new CustomPayload.Id<>(Voicy.id("speaker_sync"));

	public static final PacketCodec<RegistryByteBuf, SpeakerSyncPayload> CODEC =
			PacketCodec.tuple(
					PacketCodecs.VAR_INT, SpeakerSyncPayload::speakerId,
					PacketCodecs.DOUBLE, SpeakerSyncPayload::x,
					PacketCodecs.DOUBLE, SpeakerSyncPayload::y,
					PacketCodecs.DOUBLE, SpeakerSyncPayload::z,
					PacketCodecs.DOUBLE, SpeakerSyncPayload::captureRadius,
					PacketCodecs.DOUBLE, SpeakerSyncPayload::broadcastRadius,
					SpeakerSyncPayload::new
			);

	@Override
	public Id<? extends CustomPayload> getId() {
		return TYPE;
	}
}
