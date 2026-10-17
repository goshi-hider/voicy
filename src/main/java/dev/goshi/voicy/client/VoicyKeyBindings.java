package dev.goshi.voicy.client;

import dev.goshi.voicy.Voicy;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Default keybindings: M mute, N deafen, V GUI, H push-to-talk.
 */
public final class VoicyKeyBindings {

	public static KeyBinding TOGGLE_MUTE;
	public static KeyBinding TOGGLE_DEAFEN;
	public static KeyBinding OPEN_GUI;
	public static KeyBinding PUSH_TO_TALK;

	private static ClientVoiceManager voiceManager;

	private VoicyKeyBindings() {
	}

	public static void register(ClientVoiceManager manager) {
		voiceManager = manager;

		TOGGLE_MUTE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voicy.toggle_mute",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"key.category.voicy"
		));

		TOGGLE_DEAFEN = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voicy.toggle_deafen",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				"key.category.voicy"
		));

		OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voicy.open_gui",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				"key.category.voicy"
		));

		PUSH_TO_TALK = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.voicy.push_to_talk",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				"key.category.voicy"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TOGGLE_MUTE.wasPressed()) {
				voiceManager.setSelfMuted(!voiceManager.isSelfMuted());
			}
			while (TOGGLE_DEAFEN.wasPressed()) {
				voiceManager.setSelfDeafened(!voiceManager.isSelfDeafened());
			}
			while (OPEN_GUI.wasPressed()) {
				client.setScreen(new dev.goshi.voicy.client.gui.VoiceControlScreen(voiceManager));
			}
			voiceManager.setPushToTalkHeld(PUSH_TO_TALK.isPressed());
			voiceManager.tick(client);
		});
	}
}
