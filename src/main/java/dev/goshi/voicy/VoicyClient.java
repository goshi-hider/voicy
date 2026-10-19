package dev.goshi.voicy;

import dev.goshi.voicy.api.VoicyVoiceChatAPI;
import dev.goshi.voicy.client.ClientNetworkHandler;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.client.VoicyKeyBindings;
import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.client.gui.GroupHud;
import dev.goshi.voicy.config.VoicyConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class VoicyClient implements ClientModInitializer {

	private static ClientVoiceManager voiceManager;

	@Override
	public void onInitializeClient() {
		try {
			voiceManager = new ClientVoiceManager(VoicyConfig.INSTANCE);
			new ClientNetworkHandler(voiceManager).register();
			VoicyKeyBindings.register(voiceManager);
			GroupHud.register(voiceManager);

			ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
				if (voiceManager != null) {
					voiceManager.startCapture();
				}
			});
			ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
				ClientGroupState.reset();
				if (voiceManager != null) {
					voiceManager.stopCapture();
					voiceManager.closeTransport();
				}
			});

			VoicyVoiceChatAPI.init(
					Voicy.getInstance().securityService(),
					voiceManager,
					Voicy.getInstance().channelManager()
			);

			Voicy.LOGGER.info("[Voicy] Client voice pipeline initialized");
		} catch (Exception e) {
			Voicy.LOGGER.error("[Voicy] Client initialization failed", e);
		}
	}

	public static ClientVoiceManager voiceManager() {
		return voiceManager;
	}
}
