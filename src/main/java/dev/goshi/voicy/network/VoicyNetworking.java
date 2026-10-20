package dev.goshi.voicy.network;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.network.payload.ApiCommandPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload;
import dev.goshi.voicy.network.payload.GroupNotifyPayload;
import dev.goshi.voicy.network.payload.GroupStatePayload;
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
		PayloadTypeRegistry.playC2S().register(GroupActionPayload.TYPE, GroupActionPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(dev.goshi.voicy.network.payload.AnimationStylePayload.TYPE,
				dev.goshi.voicy.network.payload.AnimationStylePayload.CODEC);

		PayloadTypeRegistry.playS2C().register(VoiceFramePayload.TYPE, VoiceFramePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(StateSyncPayload.TYPE, StateSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.goshi.voicy.network.payload.SpeakerSyncPayload.TYPE,
				dev.goshi.voicy.network.payload.SpeakerSyncPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GroupDirectoryPayload.TYPE, GroupDirectoryPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GroupStatePayload.TYPE, GroupStatePayload.CODEC);
		PayloadTypeRegistry.playS2C().register(GroupNotifyPayload.TYPE, GroupNotifyPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(dev.goshi.voicy.network.payload.AnimationStylePayload.TYPE,
				dev.goshi.voicy.network.payload.AnimationStylePayload.CODEC);
	}
}
