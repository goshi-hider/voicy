package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.Voicy;
import dev.goshi.voicy.client.ClientNetworkHandler;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public final class VoiceControlScreen extends VoicyScreen {

	private static final int PAGE_COUNT = 2;
	private static int page = 0;

	private final ClientVoiceManager voiceManager;
	private final VoicyConfig config = VoicyConfig.INSTANCE;

	public VoiceControlScreen(ClientVoiceManager voiceManager) {
		super(Text.translatable("screen.voicy.voice_control"));
		this.voiceManager = voiceManager;
	}

	@Override
	protected void init() {
		int cx = this.width / 2;

		int pending = ClientGroupState.pendingCount();
		addDrawableChild(ButtonWidget.builder(
				pending > 0 ? Text.translatable("screen.voicy.groups_pending", pending) : Text.translatable("screen.voicy.groups"),
				button -> MinecraftClient.getInstance().setScreen(new GroupScreen(this)))
				.dimensions(cx - 14, 13, 68, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("screen.voicy.player_audio"),
				button -> MinecraftClient.getInstance().setScreen(new PlayerAudioSettingsScreen(this)))
				.dimensions(cx + 56, 13, 66, 20).build());

		if (page == 0) {
			initAudioPage(cx);
		} else {
			initEffectsPage(cx);
		}
		initNavigation(cx);
	}

	private void initAudioPage(int cx) {
		int y = 40;

		addDrawableChild(ButtonWidget.builder(
				Text.translatable(voiceManager.isSelfMuted() ? "gui.voicy.unmute" : "gui.voicy.mute"),
				btn -> {
					voiceManager.setSelfMuted(!voiceManager.isSelfMuted());
					btn.setMessage(Text.translatable(voiceManager.isSelfMuted() ? "gui.voicy.unmute" : "gui.voicy.mute"));
				}
		).dimensions(cx - 100, y, 200, 20).build());
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
	}

	private Text styleText() {
		return Text.translatable("gui.voicy.animation_style", Text.translatable(config.animationStyle().translationKey()));
	}

	private void initEffectsPage(int cx) {
		int y = 40;

		addDrawableChild(ButtonWidget.builder(
				Text.literal(config.realisticOcclusion() ? "Realism: ON" : "Realism: OFF"),
				btn -> {
					config.setRealisticOcclusion(!config.realisticOcclusion());
					btn.setMessage(Text.literal(config.realisticOcclusion() ? "Realism: ON" : "Realism: OFF"));
				}
		).dimensions(cx - 100, y, 200, 20).build());
		y += 28;

		addDrawableChild(ButtonWidget.builder(
				styleText(),
				btn -> {
					config.setAnimationStyle(config.animationStyle().next());
					ClientNetworkHandler.sendOwnAnimation();
					clearAndInit();
				}
		).dimensions(cx - 100, y, 200, 20).build());
		y += 28;

		double splitPos = (config.splitHeight() - 0.15f) / 0.7f;
		SliderWidget splitSlider = new SliderWidget(cx - 100, y, 200, 20,
				Text.translatable("gui.voicy.split_height", String.format("%.0f%%", config.splitHeight() * 100)),
				splitPos) {
			@Override
			protected void updateMessage() {
				float mapped = 0.15f + (float) this.value * 0.7f;
				setMessage(Text.translatable("gui.voicy.split_height", String.format("%.0f%%", mapped * 100)));
			}

			@Override
			protected void applyValue() {
				config.setSplitHeight(0.15f + (float) this.value * 0.7f);
			}
		};
		splitSlider.active = config.animationStyle().isSplit();
		addDrawableChild(splitSlider);
	}

	private void initNavigation(int cx) {
		int ny = this.height - 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("<"), btn -> changePage(-1))
				.dimensions(cx - 100, ny, 20, 20).build());
		addDrawableChild(ButtonWidget.builder(
				Text.translatable(page == 0 ? "gui.voicy.page_audio" : "gui.voicy.page_effects")
						.append(Text.literal("  " + (page + 1) + "/" + PAGE_COUNT)),
				btn -> changePage(1)
		).dimensions(cx - 76, ny, 152, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal(">"), btn -> changePage(1))
				.dimensions(cx + 80, ny, 20, 20).build());
	}

	private void changePage(int delta) {
		page = Math.floorMod(page + delta, PAGE_COUNT);
		clearAndInit();
	}

	@Override
	public void removed() {
		super.removed();
		ClientNetworkHandler.sendOwnAnimation();
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(width / 2 - 124, 12, width / 2 + 124, 32, 0x66000000);
		context.fill(width / 2 - 124, 12, width / 2 + 124, 13, 0xFF2A2A2A);
		context.drawTexture(Voicy.id("textures/gui/voice_badge.png"), width / 2 - 116, 13, 0.0f, 0.0f, 20, 20, 128, 128);
		context.drawTextWithShadow(textRenderer, title, width / 2 - 92, 18, 0xFFFFFFFF);

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
	}
}
