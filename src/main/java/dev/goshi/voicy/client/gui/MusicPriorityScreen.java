package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.config.DuckPriority;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.Locale;

public final class MusicPriorityScreen extends VoicyScreen {
	private final Screen parent;
	private final VoicyConfig config = VoicyConfig.INSTANCE;

	public MusicPriorityScreen(Screen parent) {
		super(Text.translatable("screen.voicy.music_priority"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int w = Math.min(220, width - 20);
		int left = cx - w / 2;

		addDrawableChild(ButtonWidget.builder(priorityText(), button -> {
			config.setMusicPriority(config.musicPriority().next());
			button.setMessage(priorityText());
		}).dimensions(left, 44, w, 20).build());

		float depth = config.musicDuckDepth();
		addDrawableChild(new SliderWidget(left, 70, w, 20, depthText(depth), depth) {
			@Override
			protected void updateMessage() {
				setMessage(depthText((float) value));
			}

			@Override
			protected void applyValue() {
				config.setMusicDuckDepth((float) value);
			}
		});

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(left, height - 28, w, 20).build());
	}

	private Text priorityText() {
		return Text.translatable("gui.voicy.music.priority",
				Text.translatable("gui.voicy.music.priority." + config.musicPriority().name().toLowerCase(Locale.ROOT)));
	}

	private Text depthText(float depth) {
		return Text.translatable("gui.voicy.music.duck_depth", String.format("%.0f%%", depth * 100));
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		int w = Math.min(220, width - 20);
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 14, 0xFFFFFFFF);
		DuckPriority priority = config.musicPriority();
		int y = 100;
		for (var line : textRenderer.wrapLines(Text.translatable("gui.voicy.music.priority_help." + priority.name().toLowerCase(Locale.ROOT)), w)) {
			context.drawTextWithShadow(textRenderer, line, cx - w / 2, y, 0xFFAAAAAA);
			y += 11;
		}
		y += 6;
		for (var line : textRenderer.wrapLines(Text.translatable("gui.voicy.music.priority_help.general"), w)) {
			context.drawTextWithShadow(textRenderer, line, cx - w / 2, y, 0xFF777777);
			y += 11;
		}
	}
}
