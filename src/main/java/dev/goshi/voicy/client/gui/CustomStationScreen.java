package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.music.MusicPlayer;
import dev.goshi.voicy.client.music.RadioStation;
import dev.goshi.voicy.client.music.RadioStations;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class CustomStationScreen extends VoicyScreen {
	private final Screen parent;
	private TextFieldWidget urlField;
	private TextFieldWidget nameField;
	private ButtonWidget saveButton;
	private String urlDraft = "";
	private String nameDraft = "";

	public CustomStationScreen(Screen parent) {
		super(Text.translatable("screen.voicy.custom_station"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = width / 2;
		int fieldW = Math.min(260, width - 20);
		int left = cx - fieldW / 2;

		urlField = new TextFieldWidget(textRenderer, left, 52, fieldW, 20, Text.translatable("gui.voicy.music.station_url"));
		urlField.setMaxLength(512);
		urlField.setPlaceholder(Text.translatable("gui.voicy.music.station_url_hint"));
		urlField.setText(urlDraft);
		urlField.setChangedListener(text -> {
			urlDraft = text;
			updateSave();
		});
		addDrawableChild(urlField);
		setInitialFocus(urlField);

		nameField = new TextFieldWidget(textRenderer, left, 92, fieldW, 20, Text.translatable("gui.voicy.music.station_name"));
		nameField.setMaxLength(32);
		nameField.setPlaceholder(Text.translatable("gui.voicy.music.station_name_hint"));
		nameField.setText(nameDraft);
		nameField.setChangedListener(text -> nameDraft = text);
		addDrawableChild(nameField);

		saveButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.save_and_play"), button -> save())
				.dimensions(left, height - 28, fieldW / 2 - 1, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> client.setScreen(parent))
				.dimensions(left + fieldW / 2 + 1, height - 28, fieldW / 2 - 1, 20).build());
		updateSave();
	}

	private boolean urlValid() {
		return RadioStations.isValidUrl(urlDraft.trim());
	}

	private void updateSave() {
		if (saveButton != null) {
			saveButton.active = urlValid();
		}
	}

	private void save() {
		if (!urlValid()) {
			return;
		}
		String url = urlDraft.trim();
		String name = nameDraft.trim().isEmpty() ? RadioStations.hostOf(url) : nameDraft.trim();
		VoicyConfig.INSTANCE.addCustomStation(name, url);
		MusicPlayer.INSTANCE.playStation(new RadioStation(name, url, RadioStations.hostOf(url), true));
		client.setScreen(parent);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == 257 || keyCode == 335) && urlValid()) {
			save();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		int fieldW = Math.min(260, width - 20);
		int left = cx - fieldW / 2;
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 14, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.voicy.music.station_url"), left, 40, 0xFFAAAAAA);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.voicy.music.station_name"), left, 80, 0xFFAAAAAA);

		int lineY = 122;
		for (var line : textRenderer.wrapLines(Text.translatable("gui.voicy.music.station_help"), fieldW)) {
			context.drawTextWithShadow(textRenderer, line, left, lineY, 0xFF999999);
			lineY += 11;
		}
		if (!urlDraft.isBlank() && !urlValid()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.voicy.music.station_invalid"),
					left, lineY + 6, 0xFFFF7777);
		}
	}
}
