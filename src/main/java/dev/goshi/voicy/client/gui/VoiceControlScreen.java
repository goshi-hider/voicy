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

public final class VoiceControlScreen extends Screen {

	private final ClientVoiceManager voiceManager;
	private final VoicyConfig config = VoicyConfig.INSTANCE;
	private int previousMenuBlurriness;

	public VoiceControlScreen(ClientVoiceManager voiceManager) {
		super(Text.translatable("screen.voicy.voice_control"));
		this.voiceManager = voiceManager;
	}

	@Override
	protected void init() {
		// Vanilla applies a post-process blur to the world behind ANY open screen, scaled by
		// the player's "Menu Background Blurriness" option, independent of what this screen's
		// own render()/renderBackground() draws - so it can't be turned off by changing our
		// draw calls. Force it off for the duration of this screen, restore on close.
		previousMenuBlurriness = client.options.getMenuBackgroundBlurriness().getValue();
		client.options.getMenuBackgroundBlurriness().setValue(0);

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

		float thresholdRange = VoicyConfig.ACTIVATION_THRESHOLD_MAX - VoicyConfig.ACTIVATION_THRESHOLD_MIN;
		double initialSliderPos = (config.activationThreshold() - VoicyConfig.ACTIVATION_THRESHOLD_MIN) / thresholdRange;
		addDrawableChild(new SliderWidget(cx - 100, y, 200, 20,
				Text.translatable("gui.voicy.sensitivity", String.format("%.0f%%", initialSliderPos * 100)),
				initialSliderPos) {
			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("gui.voicy.sensitivity",
						String.format("%.0f%%", this.value * 100)));
			}

			@Override
			protected void applyValue() {
				// this.value is the slider's normalized 0..1 drag position - map it onto the
				// gate's actual usable range instead of writing it straight into the threshold.
				float mapped = VoicyConfig.ACTIVATION_THRESHOLD_MIN + (float) this.value * thresholdRange;
				config.setActivationThreshold(mapped);
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

		// Vertical meter, right edge of the screen, fills bottom-to-top with input level.
		int meterWidth = 16;
		int meterHeight = 150;
		int meterX = width - 40 - meterWidth;
		int meterY = (height - meterHeight) / 2;
		context.fill(meterX, meterY, meterX + meterWidth, meterY + meterHeight, 0xFF171717);
		context.fill(meterX, meterY, meterX + meterWidth, meterY + 1, 0xFF2D2D2D);

		float level = voiceManager.getInputLevel();
		int fillHeight = Math.min(meterHeight, Math.max(0, (int) (level * meterHeight)));
		int fillTop = meterY + (meterHeight - fillHeight);
		int red = (int) (255 * (1.0f - level));
		int green = (int) (255 * level);
		int meterColor = 0xFF000000 | (red << 16) | (green << 8);
		context.fill(meterX, fillTop, meterX + meterWidth, meterY + meterHeight, meterColor);

		Text meterLabel = Text.literal("Mic");
		context.drawCenteredTextWithShadow(textRenderer, meterLabel, meterX + meterWidth / 2, meterY - 12, 0xFFFFFFFF);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		client.options.getMenuBackgroundBlurriness().setValue(previousMenuBlurriness);
		super.removed();
	}
}