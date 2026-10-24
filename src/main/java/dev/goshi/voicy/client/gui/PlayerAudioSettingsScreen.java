package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.GroupInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.voicechat.api.VoiceChatAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class PlayerAudioSettingsScreen extends VoicyScreen {
	private static final int ROW_HEIGHT = 32;

	private final Screen parent;
	private int seenVersion;

	public PlayerAudioSettingsScreen(Screen parent) {
		super(Text.translatable("screen.voicy.player_audio"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		seenVersion = ClientGroupState.version();
		PlayerList list = new PlayerList(client, width, height - 32 - 36, 32);
		list.load(this);
		addDrawableChild(list);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(width / 2 - 100, height - 28, 200, 20).build());
	}

	@Override
	public void tick() {
		if (seenVersion != ClientGroupState.version()) {
			clearAndInit();
		}
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
	}

	private static final class PlayerList extends ElementListWidget<PlayerList.Row> {
		PlayerList(MinecraftClient client, int width, int height, int top) {
			super(client, width, height, top, ROW_HEIGHT);
		}

		void load(PlayerAudioSettingsScreen screen) {
			List<PlayerListEntry> players = new ArrayList<>();
			if (client.getNetworkHandler() != null && client.player != null) {
				for (PlayerListEntry entry : client.getNetworkHandler().getPlayerList()) {
					if (!entry.getProfile().getId().equals(client.player.getUuid())) {
						players.add(entry);
					}
				}
			}
			players.sort(Comparator.comparing(entry -> entry.getProfile().getName().toLowerCase()));
			if (players.isEmpty()) {
				addEntry(new MessageRow(Text.translatable("gui.voicy.no_other_players")));
				return;
			}
			for (PlayerListEntry entry : players) {
				addEntry(new PlayerRow(screen, entry.getProfile().getId(), entry.getProfile().getName()));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(width - 40, 320);
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
				var font = MinecraftClient.getInstance().textRenderer;
				context.drawCenteredTextWithShadow(font, message, x + entryWidth / 2, y + 12, 0xFFAAAAAA);
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

		static final class PlayerRow extends Row {
			private final UUID id;
			private final String name;
			private final SliderWidget volume;
			private final ButtonWidget details;

			PlayerRow(PlayerAudioSettingsScreen screen, UUID id, String name) {
				this.id = id;
				this.name = name;
				VoiceChatAPI api = VoiceChatAPI.getInstance();
				float initial = api == null ? 1.0f : api.getVolumeManager().getPlayerVolume(id);
				this.volume = new VolumeSlider(0, 0, 100, 20, id, initial);
				this.details = ButtonWidget.builder(Text.literal(">"),
								button -> MinecraftClient.getInstance().setScreen(new PlayerDetailScreen(screen, id, name)))
						.dimensions(0, 0, 20, 20).build();
				this.details.setTooltip(Tooltip.of(Text.translatable("gui.voicy.player_options")));
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				var font = MinecraftClient.getInstance().textRenderer;
				GuiUtil.drawHead(context, id, x + 4, y + 8, 16);

				int controlsWidth = 100 + 4 + 20;
				int nameMax = entryWidth - 28 - controlsWidth - 8;
				GroupInfo group = ClientGroupState.groupOf(id);
				int nameY = group == null ? y + 12 : y + 6;
				context.drawTextWithShadow(font, GuiUtil.coloredName(id, font.trimToWidth(name, nameMax)),
						x + 26, nameY, 0xFFFFFFFF);
				if (group != null) {
					context.drawTextWithShadow(font,
							Text.translatable("gui.voicy.in_group", font.trimToWidth(group.name(), nameMax - 40)),
							x + 26, y + 18, 0xFF999999);
				}

				volume.setX(x + entryWidth - controlsWidth);
				volume.setY(y + 6);
				volume.render(context, mouseX, mouseY, delta);
				details.setX(x + entryWidth - 20);
				details.setY(y + 6);
				details.render(context, mouseX, mouseY, delta);
			}

			@Override
			public List<? extends Element> children() {
				return List.of(volume, details);
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of(volume, details);
			}
		}
	}

	static final class VolumeSlider extends SliderWidget {
		private final UUID player;

		VolumeSlider(int x, int y, int width, int height, UUID player, float initialVolume) {
			super(x, y, width, height,
					Text.translatable("gui.voicy.volume_short", percent(MathHelper.clamp(initialVolume / 2.0, 0.0, 1.0))),
					MathHelper.clamp(initialVolume / 2.0, 0.0, 1.0));
			this.player = player;
		}

		private static String percent(double sliderValue) {
			return String.format("%.0f%%", sliderValue * 200.0);
		}

		@Override
		protected void updateMessage() {
			setMessage(Text.translatable("gui.voicy.volume_short", percent(value)));
		}

		@Override
		protected void applyValue() {
			VoiceChatAPI api = VoiceChatAPI.getInstance();
			if (api != null) {
				api.getVolumeManager().setPlayerVolume(player, (float) (value * 2.0));
			}
		}

		void reset() {
			value = 0.5;
			updateMessage();
			applyValue();
		}
	}
}
