package dev.goshi.voicy.paper.net.payload;

import dev.goshi.voicy.paper.net.VoicyByteBuf;

import java.util.UUID;

public record StateSyncPayload(UUID playerId, boolean muted, boolean deafened) {

	public void encode(VoicyByteBuf buf) {
		buf.writeUuidAsString(playerId);
		buf.writeBoolean(muted);
		buf.writeBoolean(deafened);
	}

	public static StateSyncPayload decode(VoicyByteBuf buf) {
		UUID playerId = buf.readUuidAsString();
		boolean muted = buf.readBoolean();
		boolean deafened = buf.readBoolean();
		return new StateSyncPayload(playerId, muted, deafened);
	}
}
