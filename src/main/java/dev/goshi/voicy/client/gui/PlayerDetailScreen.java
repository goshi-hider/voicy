package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.network.payload.GroupActionPayload.Action;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.GroupInfo;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.voicechat.api.VoiceChatAPI;

import java.util.UUID;

public final class PlayerDetailScreen extends VoicyScreen {
	private final Screen parent;
	private final UUID playerId;
	private final String playerName;
	private int seenVersion;
	private Text status = Text.empty();
	private Text hint = Text.empty();

	public PlayerDetailScreen(Screen parent, UUID playerId, String playerName) {
		super(Text.literal(playerName));
		this.parent = parent;
		this.playerId = playerId;
		this.playerName = playerName;
	}

	@Override
	protected void init() {
		seenVersion = ClientGroupState.version();
		int cx = width / 2;

		VoiceChatAPI api = VoiceChatAPI.getInstance();
		float initial = api == null ? 1.0f : api.getVolumeManager().getPlayerVolume(playerId);
		PlayerAudioSettingsScreen.VolumeSlider slider =
				new PlayerAudioSettingsScreen.VolumeSlider(cx - 100, 52, 200, 20, playerId, initial);
		addDrawableChild(slider);
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.reset_player_volume"), button -> slider.reset())
				.dimensions(cx - 100, 76, 200, 20).build());

		GroupInfo mine = ClientGroupState.myGroup();
		GroupInfo theirs = ClientGroupState.groupOf(playerId);
		boolean iAmOwner = ClientGroupState.isOwner();
		int y = 138;

		if (mine == null && theirs == null) {
			status = Text.translatable("gui.voicy.detail.neither", playerName);
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.create_group"),
					button -> client.setScreen(new CreateGroupScreen(this)))
					.dimensions(cx - 100, y, 200, 20).build());
		} else if (mine != null && theirs == null) {
			status = Text.translatable("gui.voicy.detail.no_group", playerName);
			ButtonWidget invite = addDrawableChild(ButtonWidget.builder(
							Text.translatable("gui.voicy.invite_to_group", mine.name()),
							button -> {
								ClientGroupState.send(Action.INVITE, playerId);
								button.active = false;
							})
					.dimensions(cx - 100, y, 200, 20).build());
			if (!iAmOwner) {
				invite.active = false;
				hint = Text.translatable("gui.voicy.hint.owner_only_invite");
			} else if (mine.isFull()) {
				invite.active = false;
				hint = Text.translatable("gui.voicy.hint.group_full");
			}
		} else if (mine == null) {
			status = Text.translatable(theirs.isPublic() ? "gui.voicy.detail.their_public" : "gui.voicy.detail.their_private",
					playerName, theirs.name());
			ButtonWidget join = addDrawableChild(ButtonWidget.builder(
							Text.translatable(theirs.isPublic() ? "gui.voicy.join_their_group" : "gui.voicy.ask_to_join"),
							button -> {
								ClientGroupState.send(Action.REQUEST_JOIN, playerId);
								button.active = false;
							})
					.dimensions(cx - 100, y, 200, 20).build());
			if (theirs.isFull()) {
				join.active = false;
				hint = Text.translatable("gui.voicy.hint.group_full");
			}
		} else if (mine.id().equals(theirs.id())) {
			status = Text.translatable("gui.voicy.detail.same_group", playerName);
			if (iAmOwner) {
				addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.kick"),
								button -> ClientGroupState.send(Action.KICK, playerId))
						.dimensions(cx - 100, y, 200, 20).build());
			}
		} else {
			status = Text.translatable("gui.voicy.detail.other_group", playerName, theirs.name());
			hint = Text.translatable("gui.voicy.hint.leave_first");
		}

		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(cx - 100, height - 28, 200, 20).build());
	}

	@Override
	public void tick() {
		if (seenVersion != ClientGroupState.version()) {
			clearAndInit();
		}
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		int nameWidth = textRenderer.getWidth(playerName);
		int left = cx - (nameWidth + 28) / 2;
		GuiUtil.drawHead(context, playerId, left, 14, 24);
		context.drawTextWithShadow(textRenderer, GuiUtil.coloredName(playerId, playerName), left + 30, 22, 0xFFFFFFFF);

		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.voicy.group_section"), cx, 112, 0xFFAAAAAA);
		context.fill(cx - 100, 123, cx + 100, 124, 0x66FFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, status, cx, 127, 0xFFFFFFFF);
		if (!hint.getString().isEmpty()) {
			context.drawCenteredTextWithShadow(textRenderer, hint, cx, 164, 0xFF999999);
		}
	}
}
