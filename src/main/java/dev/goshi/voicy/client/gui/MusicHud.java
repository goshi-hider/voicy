package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.music.MusicPlayer;
import dev.goshi.voicy.client.music.MusicWidget;
import dev.goshi.voicy.config.VoicyConfig;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;

public final class MusicHud {
	private MusicHud() {
	}

	public static void register() {
		HudRenderCallback.EVENT.register((context, tickCounter) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.player == null || client.options.hudHidden || !VoicyConfig.INSTANCE.musicWidgetEnabled()) {
				return;
			}
			if (!MusicPlayer.INSTANCE.hasMedia()) {
				return;
			}
			if (client.currentScreen instanceof MusicScreen || client.currentScreen instanceof MusicWidgetEditorScreen) {
				return;
			}
			float scale = MusicWidget.scale();
			int x = MusicWidget.hudX(context.getScaledWindowWidth(), scale);
			int y = MusicWidget.hudY(context.getScaledWindowHeight(), scale);
			MusicWidget.render(context, x, y, -1, -1, scale);
		});
	}
}
