package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.music.MusicLibrary;
import dev.goshi.voicy.client.music.MusicPlayer;
import dev.goshi.voicy.client.music.MusicTrack;
import dev.goshi.voicy.client.music.MusicWidget;
import dev.goshi.voicy.client.music.RadioStation;
import dev.goshi.voicy.client.music.RadioStations;
import dev.goshi.voicy.config.MusicMode;
import dev.goshi.voicy.config.RepeatMode;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

import java.util.List;

public final class MusicScreen extends VoicyScreen {
	private static final int ROW_HEIGHT = 24;
	private static final int CARD_Y = 52;
	private static final int ACCENT = 0xFF3BD16F;

	private final Screen parent;
	private final MusicPlayer player = MusicPlayer.INSTANCE;
	private final VoicyConfig config = VoicyConfig.INSTANCE;

	public MusicScreen(Screen parent) {
		super(Text.translatable("screen.voicy.music"));
		this.parent = parent;
	}

	private int columnWidth() {
		return Math.min(300, width - 20);
	}

	@Override
	protected void init() {
		if (player.library().tracks().isEmpty()) {
			player.refreshLibrary();
		}
		int cx = width / 2;
		int colW = columnWidth();
		int left = cx - colW / 2;
		boolean local = config.musicMode() == MusicMode.LOCAL;

		ButtonWidget localButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.local"),
				button -> switchMode(MusicMode.LOCAL)).dimensions(left, 26, colW / 2 - 1, 20).build());
		ButtonWidget radioButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.radio"),
				button -> switchMode(MusicMode.RADIO)).dimensions(left + colW / 2 + 1, 26, colW / 2 - 1, 20).build());
		localButton.active = !local;
		radioButton.active = local;

		int sideX = left + MusicWidget.WIDTH + 6;
		int sideW = colW - MusicWidget.WIDTH - 6;
		int half = (sideW - 2) / 2;
		int cardY = CARD_Y;
		if (local) {
			ButtonWidget folder = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.folder"),
					button -> openFolder()).dimensions(sideX, cardY, half, 20).build());
			folder.setTooltip(Tooltip.of(Text.translatable("gui.voicy.music.folder_help", MusicLibrary.FOLDER_NAME)));
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.refresh"), button -> {
				player.refreshLibrary();
				clearAndInit();
			}).dimensions(sideX + half + 2, cardY, half, 20).build());
			addDrawableChild(ButtonWidget.builder(shuffleText(), button -> {
				config.setMusicShuffle(!config.musicShuffle());
				button.setMessage(shuffleText());
			}).dimensions(sideX, cardY + 24, half, 20).build());
			addDrawableChild(ButtonWidget.builder(repeatText(), button -> {
				config.setMusicRepeat(config.musicRepeat().next());
				button.setMessage(repeatText());
			}).dimensions(sideX + half + 2, cardY + 24, half, 20).build());
		} else {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.add_station"),
					button -> client.setScreen(new CustomStationScreen(this))).dimensions(sideX, cardY, sideW, 20).build());
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.stop"), button -> player.stop())
					.dimensions(sideX, cardY + 24, sideW, 20).build());
		}

		int listTop = cardY + MusicWidget.HEIGHT + 6;
		int listBottom = height - 58;
		MusicList list = new MusicList(client, width, Math.max(ROW_HEIGHT, listBottom - listTop), listTop, colW);
		if (local) {
			list.loadTracks();
		} else {
			list.loadStations(this);
		}
		addDrawableChild(list);

		float volume = config.musicVolume();
		addDrawableChild(new SliderWidget(left, height - 52, colW, 20, volumeText(volume), volume) {
			@Override
			protected void updateMessage() {
				setMessage(volumeText((float) value));
			}

			@Override
			protected void applyValue() {
				player.setVolume((float) value);
			}
		});

		int quarter = (colW - 12) / 4;
		int bx = left;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.move_widget"),
				button -> client.setScreen(new MusicWidgetEditorScreen(this))).dimensions(bx, height - 28, quarter, 20).build());
		bx += quarter + 4;
		addDrawableChild(ButtonWidget.builder(widgetText(), button -> {
			config.setMusicWidgetEnabled(!config.musicWidgetEnabled());
			button.setMessage(widgetText());
		}).dimensions(bx, height - 28, quarter, 20).build());
		bx += quarter + 4;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.priority_button"),
				button -> client.setScreen(new MusicPriorityScreen(this))).dimensions(bx, height - 28, quarter, 20).build());
		bx += quarter + 4;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(bx, height - 28, left + colW - bx, 20).build());
	}

	private void switchMode(MusicMode mode) {
		player.setMode(mode);
		clearAndInit();
	}

	private void openFolder() {
		player.library().scan();
		try {
			Util.getOperatingSystem().open(player.library().folder().toFile());
		} catch (RuntimeException ignored) {
		}
	}

	private Text shuffleText() {
		Text label = Text.translatable("gui.voicy.music.shuffle");
		return config.musicShuffle() ? Text.empty().append(label).formatted(Formatting.GREEN) : label;
	}

	private Text repeatText() {
		RepeatMode mode = config.musicRepeat();
		Text label = Text.translatable("gui.voicy.music.repeat." + mode.name().toLowerCase(java.util.Locale.ROOT));
		return mode == RepeatMode.OFF ? label : Text.empty().append(label).formatted(Formatting.GREEN);
	}

	private Text volumeText(float volume) {
		return Text.translatable("gui.voicy.music.volume", String.format("%.0f%%", volume * 100));
	}

	private Text widgetText() {
		return Text.translatable(config.musicWidgetEnabled() ? "gui.voicy.music.widget_on" : "gui.voicy.music.widget_off");
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && MusicWidget.contains(cardX(), CARD_Y, mouseX, mouseY, 1f)
				&& MusicWidget.click(cardX(), CARD_Y, mouseX, mouseY, 1f)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private int cardX() {
		return width / 2 - columnWidth() / 2;
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
		int accentY = 46;
		int colW = columnWidth();
		int left = width / 2 - colW / 2;
		boolean local = config.musicMode() == MusicMode.LOCAL;
		int x = local ? left : left + colW / 2 + 1;
		context.fill(x, accentY, x + colW / 2 - 1, accentY + 2, ACCENT);
		MusicWidget.render(context, cardX(), CARD_Y, mouseX, mouseY, 1f);
	}

	private static TextRenderer font() {
		return MinecraftClient.getInstance().textRenderer;
	}

	private static void clickSound() {
		MinecraftClient.getInstance().getSoundManager()
				.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}

	private static final class MusicList extends ElementListWidget<MusicList.Row> {
		private final int rowWidth;

		MusicList(MinecraftClient client, int width, int height, int top, int rowWidth) {
			super(client, width, height, top, ROW_HEIGHT);
			this.rowWidth = rowWidth;
		}

		@Override
		public int getRowWidth() {
			return rowWidth;
		}

		void loadTracks() {
			List<MusicTrack> tracks = MusicPlayer.INSTANCE.library().tracks();
			if (tracks.isEmpty()) {
				addEntry(new MessageRow(Text.translatable("gui.voicy.music.empty_local", MusicLibrary.FOLDER_NAME)));
				addEntry(new MessageRow(Text.translatable("gui.voicy.music.empty_local_hint")));
				return;
			}
			for (int i = 0; i < tracks.size(); i++) {
				addEntry(new TrackRow(i, tracks.get(i)));
			}
		}

		void loadStations(MusicScreen screen) {
			for (RadioStation station : RadioStations.all()) {
				addEntry(new StationRow(screen, station));
			}
		}

		abstract static class Row extends ElementListWidget.Entry<Row> {
		}

		static final class MessageRow extends Row {
			private final Text message;

			MessageRow(Text message) {
				this.message = message;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				context.drawCenteredTextWithShadow(font(), message, x + entryWidth / 2, y + 8, 0xFFAAAAAA);
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}

		static final class TrackRow extends Row {
			private final int index;
			private final MusicTrack track;

			TrackRow(int index, MusicTrack track) {
				this.index = index;
				this.track = track;
			}

			@Override
			public void render(DrawContext context, int i, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				boolean current = MusicPlayer.INSTANCE.hasMedia() && MusicPlayer.INSTANCE.currentIndex() == index;
				if (current) {
					context.fill(x, y, x + entryWidth, y + entryHeight - 2, 0x2A3BD16F);
					context.fill(x, y, x + 2, y + entryHeight - 2, ACCENT);
				} else if (hovered) {
					context.fill(x, y, x + entryWidth, y + entryHeight - 2, 0x22FFFFFF);
				}
				String number = String.valueOf(index + 1);
				context.drawTextWithShadow(font(), number, x + 18 - font().getWidth(number), y + 7,
						current ? ACCENT : 0xFF777777);
				int textW = entryWidth - 30;
				int titleColor = current ? ACCENT : 0xFFFFFFFF;
				if (track.artist().isBlank()) {
					context.drawTextWithShadow(font(), font().trimToWidth(track.title(), textW), x + 26, y + 7, titleColor);
				} else {
					context.drawTextWithShadow(font(), font().trimToWidth(track.title(), textW), x + 26, y + 3, titleColor);
					context.drawTextWithShadow(font(), font().trimToWidth(track.artist(), textW), x + 26, y + 13, 0xFF999999);
				}
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (button != 0) {
					return false;
				}
				clickSound();
				MusicPlayer.INSTANCE.playIndex(index);
				return true;
			}

			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}

		static final class StationRow extends Row {
			private final RadioStation station;
			private final ButtonWidget remove;

			StationRow(MusicScreen screen, RadioStation station) {
				this.station = station;
				if (station.custom()) {
					this.remove = ButtonWidget.builder(Text.literal("x"), button -> {
						VoicyConfig.INSTANCE.removeCustomStation(station.url());
						screen.clearAndInit();
					}).dimensions(0, 0, 18, 18).build();
					this.remove.setTooltip(Tooltip.of(Text.translatable("gui.voicy.music.remove_station")));
				} else {
					this.remove = null;
				}
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				boolean current = MusicPlayer.INSTANCE.hasMedia() && station.url().equalsIgnoreCase(MusicPlayer.INSTANCE.currentStationUrl());
				if (current) {
					context.fill(x, y, x + entryWidth, y + entryHeight - 2, 0x2A3BD16F);
					context.fill(x, y, x + 2, y + entryHeight - 2, ACCENT);
				} else if (hovered) {
					context.fill(x, y, x + entryWidth, y + entryHeight - 2, 0x22FFFFFF);
				}
				int textW = entryWidth - 16 - (remove != null ? 22 : 0);
				context.drawTextWithShadow(font(), font().trimToWidth(station.name(), textW), x + 8, y + 3,
						current ? ACCENT : 0xFFFFFFFF);
				context.drawTextWithShadow(font(), font().trimToWidth(station.tagline(), textW), x + 8, y + 13,
						0xFF999999);
				if (remove != null) {
					remove.setX(x + entryWidth - 20);
					remove.setY(y + 1);
					remove.render(context, mouseX, mouseY, delta);
				}
			}

			@Override
			public boolean mouseClicked(double mouseX, double mouseY, int button) {
				if (remove != null && remove.mouseClicked(mouseX, mouseY, button)) {
					return true;
				}
				if (button != 0) {
					return false;
				}
				clickSound();
				MusicPlayer.INSTANCE.playStation(station);
				return true;
			}

			@Override
			public List<? extends Element> children() {
				return remove == null ? List.of() : List.of(remove);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return remove == null ? List.of() : List.of(remove);
			}
		}
	}
}
