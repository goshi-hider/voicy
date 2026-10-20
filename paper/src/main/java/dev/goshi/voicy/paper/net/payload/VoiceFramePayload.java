package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.UUID;

public record VoiceFramePayload(UUID speakerId, long sequence, float distanceRadius, byte[] opusData) {

	public void encode(VoicyByteBuf buf) {
		buf.writeUuidAsString(speakerId);
		buf.writeVarLong(sequence);
		buf.writeFloat(distanceRadius);
		buf.writeByteArray(opusData);
	}

	public static VoiceFramePayload decode(VoicyByteBuf buf) {
		UUID speakerId = buf.readUuidAsString();
		long sequence = buf.readVarLong();
		float radius = buf.readFloat();
		byte[] opus = buf.readByteArray();
		return new VoiceFramePayload(speakerId, sequence, radius, opus);
	}
}
