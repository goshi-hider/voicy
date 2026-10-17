package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.Voicy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.voicechat.api.VoiceChatAPI;

import java.util.List;
import java.util.UUID;

public final class PlayerAudioSettingsScreen extends VoicyScreen {

	private final Screen parent;
	private final UUID requestedPlayer;
	private List<OnlinePlayer> players = List.of();

	public PlayerAudioSettingsScreen(Screen parent) {
		this(parent, null);
	}

	private PlayerAudioSettingsScreen(Screen parent, UUID requestedPlayer) {
		super(Text.translatable("screen.voicy.player_audio"));
		this.parent = parent;
		this.requestedPlayer = requestedPlayer;
	}

	@Override
	protected void init() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world != null && client.player != null) {
			players = client.world.getPlayers().stream()
					.filter(player -> !player.getUuid().equals(client.player.getUuid()))
					.map(player -> new OnlinePlayer(player.getUuid(), player.getGameProfile().getName()))
					.toList();
		} else {
			players = List.of();
		}

		int centerX = width / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(centerX - 100, height - 34, 200, 20).build());

		if (players.isEmpty()) {
			return;
		}

		int selectedIndex = 0;
		for (int index = 0; index < players.size(); index++) {
			if (players.get(index).id().equals(requestedPlayer)) {
				selectedIndex = index;
				break;
			}
		}
		OnlinePlayer selected = players.get(selectedIndex);
		int nextIndex = (selectedIndex + 1) % players.size();
		addDrawableChild(ButtonWidget.builder(Text.literal(selected.name() + "  >"), button ->
				client.setScreen(new PlayerAudioSettingsScreen(parent, players.get(nextIndex).id())))
				.dimensions(centerX - 100, 62, 200, 20).build());

		VoiceChatAPI api = VoiceChatAPI.getInstance();
		float initialVolume = api == null ? 1.0f : api.getVolumeManager().getPlayerVolume(selected.id());
		addDrawableChild(new SliderWidget(centerX - 100, 100, 200, 20,
				Text.translatable("gui.voicy.player_volume", String.format("%.0f%%", initialVolume * 100)),
				MathHelper.clamp(initialVolume / 2.0, 0.0, 1.0)) {
			@Override
			protected void updateMessage() {
				setMessage(Text.translatable("gui.voicy.player_volume", String.format("%.0f%%", this.value * 200.0)));
			}

			@Override
			protected void applyValue() {
				VoiceChatAPI currentApi = VoiceChatAPI.getInstance();
				if (currentApi != null) {
					currentApi.getVolumeManager().setPlayerVolume(selected.id(), (float) (this.value * 2.0));
				}
			}
		});

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.reset_player_volume"), button -> {
			VoiceChatAPI currentApi = VoiceChatAPI.getInstance();
			if (currentApi != null) {
				currentApi.getVolumeManager().setPlayerVolume(selected.id(), 1.0f);
			}
			client.setScreen(new PlayerAudioSettingsScreen(parent, selected.id()));
		}).dimensions(centerX - 100, 132, 200, 20).build());
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(width / 2 - 124, 12, width / 2 + 124, 36, 0xDD09121B);
		context.fill(width / 2 - 124, 12, width / 2 + 124, 14, 0xFF3AD6BE);
		context.drawTexture(Voicy.id("textures/gui/voice_badge.png"), width / 2 - 114, 16,
				0.0f, 0.0f, 18, 18, 128, 128);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2 + 10, 21, 0xFFEAF6F8);
		if (players.isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("gui.voicy.no_other_players"), width / 2, height / 2 - 4, 0xFFB9C7CE);
		} else {
			context.drawCenteredTextWithShadow(textRenderer,
					Text.translatable("gui.voicy.player_volume_help"), width / 2, 88, 0xFFB9C7CE);
		}
	}


	private record OnlinePlayer(UUID id, String name) {
	}
}
