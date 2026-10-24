package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

public record SpeakerSyncPayload(
		int speakerId,
		double x,
		double y,
		double z,
		double captureRadius,
		double broadcastRadius
) {
	public void encode(VoicyByteBuf buf) {
		buf.writeVarInt(speakerId);
		buf.writeDouble(x);
		buf.writeDouble(y);
		buf.writeDouble(z);
		buf.writeDouble(captureRadius);
		buf.writeDouble(broadcastRadius);
	}

	public static SpeakerSyncPayload decode(VoicyByteBuf buf) {
		int speakerId = buf.readVarInt();
		double x = buf.readDouble();
		double y = buf.readDouble();
		double z = buf.readDouble();
		double captureRadius = buf.readDouble();
		double broadcastRadius = buf.readDouble();
		return new SpeakerSyncPayload(speakerId, x, y, z, captureRadius, broadcastRadius);
	}
}
