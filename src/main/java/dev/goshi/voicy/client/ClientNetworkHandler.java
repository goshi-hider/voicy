package dev.goshi.voicy.client;

import dev.goshi.voicy.network.payload.HandshakePayload;
import dev.goshi.voicy.network.payload.SpeakerSyncPayload;
import dev.goshi.voicy.network.payload.StateSyncPayload;
import dev.goshi.voicy.network.payload.VoiceFramePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side payload receivers.
 */
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
				context.client().execute(() -> voiceManager.setTransport(payload)));

		ClientPlayNetworking.registerGlobalReceiver(SpeakerSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> voiceManager.onSpeakerSync(payload)));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new HandshakePayload(
						HandshakePayload.TransportMode.FABRIC_PAYLOAD, 0, 0, 0, new byte[0], "127.0.0.1")));
	}
}
