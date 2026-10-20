package dev.goshi.voicy.client;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.network.payload.AnimationStylePayload;
import net.minecraft.client.MinecraftClient;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload;
import dev.goshi.voicy.network.payload.GroupNotifyPayload;
import dev.goshi.voicy.network.payload.GroupStatePayload;
import dev.goshi.voicy.network.payload.HandshakePayload;
import dev.goshi.voicy.network.payload.SpeakerSyncPayload;
import dev.goshi.voicy.network.payload.StateSyncPayload;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class ClientNetworkHandler {

	private final ClientVoiceManager voiceManager;

	public ClientNetworkHandler(ClientVoiceManager voiceManager) {
		this.voiceManager = voiceManager;
	}

	public void register() {
		ClientPlayNetworking.registerGlobalReceiver(VoiceFramePayload.TYPE, (payload, context) ->
				context.client().execute(() -> voiceManager.onVoiceFrame(payload)));

		ClientPlayNetworking.registerGlobalReceiver(StateSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> voiceManager.applyServerVoiceState(
						payload.playerId(), payload.muted(), payload.deafened())));

		ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					voiceManager.setTransport(payload);
					sendOwnAnimation();
				}));

		ClientPlayNetworking.registerGlobalReceiver(SpeakerSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> voiceManager.onSpeakerSync(payload)));

		ClientPlayNetworking.registerGlobalReceiver(GroupDirectoryPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientGroupState.applyDirectory(payload)));

		ClientPlayNetworking.registerGlobalReceiver(GroupStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientGroupState.applyState(payload)));

		ClientPlayNetworking.registerGlobalReceiver(GroupNotifyPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientGroupState.showNotification(context.client(), payload)));

		ClientPlayNetworking.registerGlobalReceiver(AnimationStylePayload.TYPE, (payload, context) ->
				context.client().execute(() -> AnimationStates.put(
						payload.playerId(), payload.style(), payload.splitHeight())));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new HandshakePayload(
						HandshakePayload.TransportMode.FABRIC_PAYLOAD, 0, 0, 0, new byte[0], "127.0.0.1")));
	}

	public static void sendOwnAnimation() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || !ClientPlayNetworking.canSend(AnimationStylePayload.TYPE)) {
			return;
		}
		VoicyConfig config = VoicyConfig.INSTANCE;
		ClientPlayNetworking.send(new AnimationStylePayload(
				client.player.getUuid(), config.animationStyle().ordinal(), config.splitHeight()));
	}
}
