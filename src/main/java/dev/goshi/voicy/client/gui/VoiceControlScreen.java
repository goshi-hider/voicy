package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.config.VoicyConfig;
import dev.goshi.voicy.Voicy;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;

/**
 * In-game voice control panel with mute/deafen toggles, sensitivity, and device selectors.
 */
public final class VoiceControlScreen extends Screen {

	private final ClientVoiceManager voiceManager;
	private final VoicyConfig config = VoicyConfig.INSTANCE;

	public VoiceControlScreen(ClientVoiceManager voiceManager) {
		super(Text.translatable("screen.voicy.voice_control"));
		this.voiceManager = voiceManager;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 40;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable(voiceManager.isSelfMuted() ? "gui.voicy.unmute" : "gui.voicy.mute"),
				btn -> {
					voiceManager.setSelfMuted(!voiceManager.isSelfMuted());
					btn.setMessage(Text.translatable(voiceManager.isSelfMuted() ? "gui.voicy.unmute" : "gui.voicy.mute"));
				}
		).dimensions(cx - 100, y, 200, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.voicy.player_audio"),
				button -> MinecraftClient.getInstance().setScreen(new PlayerAudioSettingsScreen(this)))
				.dimensions(cx + 68, 13, 52, 20).build());
		y += 28;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable(voiceManager.isSelfDeafened() ? "gui.voicy.undeafen" : "gui.voicy.deafen"),
				btn -> {
					voiceManager.setSelfDeafened(!voiceManager.isSelfDeafened());
					btn.setMessage(Text.translatable(voiceManager.isSelfDeafened() ? "gui.voicy.undeafen" : "gui.voicy.deafen"));
				}
		).dimensions(cx - 100, y, 200, 20).build());
		y += 36;

		addDrawableChild(new SliderWidget(cx - 100, y, 200, 20,
				Text.translatable("gui.voicy.sensitivity", String.format("%.0f%%", config.activationThreshold() * 100)),
				config.activationThreshold()) {
			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("gui.voicy.sensitivity",
						String.format("%.0f%%", this.value * 100)));
			}

			@Override
			protected void applyValue() {
				config.setActivationThreshold((float) this.value);
			}
		});
		y += 36;

		var inputDevices = voiceManager.capture().listInputDevices();
		if (!inputDevices.isEmpty()) {
			String current = voiceManager.capture().getSelectedDevice();
			if (current == null || current.isBlank()) {
				current = inputDevices.getFirst();
				voiceManager.capture().selectDevice(current);
			}
			final String[] selected = { current };
			addDrawableChild(ButtonWidget.builder(
					Text.literal("Mic: " + selected[0]),
					btn -> {
						int idx = java.util.Arrays.asList(inputDevices.toArray(new String[0])).indexOf(selected[0]) + 1;
						if (idx >= inputDevices.size()) {
							idx = 0;
						}
						selected[0] = inputDevices.get(idx);
						voiceManager.capture().selectDevice(selected[0]);
						config.setSelectedInputDevice(selected[0]);
						btn.setMessage(Text.literal("Mic: " + selected[0]));
					}
			).dimensions(cx - 100, y, 200, 20).build());
		}
		y += 28;

		var outputDevices = voiceManager.capture().listOutputDevices();
		if (!outputDevices.isEmpty()) {
			String selectedOutput = config.selectedOutputDevice();
			if (selectedOutput == null || selectedOutput.isBlank() || !outputDevices.contains(selectedOutput)) {
				selectedOutput = outputDevices.getFirst();
				config.setSelectedOutputDevice(selectedOutput);
			}
			final String[] outputSelected = { selectedOutput };
			addDrawableChild(ButtonWidget.builder(
					Text.literal("Speaker: " + outputSelected[0]),
					btn -> {
						int idx = outputDevices.indexOf(outputSelected[0]) + 1;
						if (idx >= outputDevices.size()) {
							idx = 0;
						}
						outputSelected[0] = outputDevices.get(idx);
						config.setSelectedOutputDevice(outputSelected[0]);
						voiceManager.capture().selectOutputDevice(outputSelected[0]);
						btn.setMessage(Text.literal("Speaker: " + outputSelected[0]));
					}
			).dimensions(cx - 100, y, 200, 20).build());
		}
		y += 28;

		addDrawableChild(ButtonWidget.builder(
				Text.literal(config.realisticOcclusion() ? "Realism: ON" : "Realism: OFF"),
				btn -> {
					config.setRealisticOcclusion(!config.realisticOcclusion());
					btn.setMessage(Text.literal(config.realisticOcclusion() ? "Realism: ON" : "Realism: OFF"));
				}
		).dimensions(cx - 100, y, 200, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		renderBackground(context, mouseX, mouseY, delta);
		context.fill(width / 2 - 124, 12, width / 2 + 124, 32, 0x66000000);
		context.fill(width / 2 - 124, 12, width / 2 + 124, 13, 0xFF2A2A2A);
		context.drawTexture(Voicy.id("textures/gui/voice_badge.png"), width / 2 - 116, 13, 0.0f, 0.0f, 20, 20, 128, 128);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2 - 5, 16, 0xFFFFFFFF);

		int meterWidth = 180;
		int meterX = width / 2 - meterWidth / 2;
		int meterY = height - 54;
		context.fill(meterX, meterY, meterX + meterWidth, meterY + 14, 0xFF171717);
		context.fill(meterX, meterY, meterX + meterWidth, meterY + 1, 0xFF2D2D2D);
		int fill = Math.min(meterWidth, Math.max(0, (int) (voiceManager.getInputLevel() * meterWidth)));
		float level = voiceManager.getInputLevel();
		int red = (int) (255 * (1.0f - level));
		int green = (int) (255 * level);
		int meterColor = (red << 16) | (green << 8) | 0x00;
		context.fill(meterX, meterY, meterX + fill, meterY + 14, meterColor | 0xFF000000);
		Text meterLabel = Text.literal("Voice meter");
		context.drawCenteredTextWithShadow(textRenderer, meterLabel, width / 2, meterY - 12, 0xFFFFFFFF);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
