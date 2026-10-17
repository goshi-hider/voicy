package dev.goshi.voicy.network;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.network.payload.HandshakePayload;
import dev.goshi.voicy.network.payload.StateSyncPayload;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.util.Identifier;

public final class VoicyNetworking {

	public static final Identifier VOICE_FRAME = Voicy.id("voice_frame");
	public static final Identifier API_COMMAND = Voicy.id("api_command");
	public static final Identifier STATE_SYNC = Voicy.id("state_sync");
	public static final Identifier HANDSHAKE = Voicy.id("handshake");
	public static final Identifier SPEAKER_SYNC = Voicy.id("speaker_sync");

	private VoicyNetworking() {
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.playC2S().register(VoiceFramePayload.TYPE, VoiceFramePayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ApiCommandPayload.TYPE, ApiCommandPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(HandshakePayload.TYPE, HandshakePayload.CODEC);

		PayloadTypeRegistry.playS2C().register(VoiceFramePayload.TYPE, VoiceFramePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StateSyncPayload.TYPE, StateSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.goshi.voicy.network.payload.SpeakerSyncPayload.TYPE,
				dev.goshi.voicy.network.payload.SpeakerSyncPayload.CODEC);
	}
}
