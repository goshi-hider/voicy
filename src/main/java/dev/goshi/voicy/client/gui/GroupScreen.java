package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload.Action;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.GroupInfo;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.Member;
import dev.goshi.voicy.network.payload.GroupStatePayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;

public final class GroupScreen extends VoicyScreen {

	private static final int ROW_HEIGHT = 26;

	private final Screen parent;
	private int seenVersion;

	public GroupScreen(Screen parent) {
		super(Text.translatable("screen.voicy.groups"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		seenVersion = ClientGroupState.version();
		GroupInfo group = ClientGroupState.myGroup();
		boolean owner = ClientGroupState.isOwner();
		int cx = width / 2;

		int footerRows = group == null ? 2 : 3;
		int footerTop = height - 8 - footerRows * 24;
		GroupList list = new GroupList(client, width, footerTop - 6 - 36, 36);
		list.load(group, owner);
		addDrawableChild(list);

		int y = footerTop;
		if (group == null) {
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.create_group"),
					button -> client.setScreen(new CreateGroupScreen(this)))
					.dimensions(cx - 100, y, 200, 20).build());
			y += 24;
		} else {
			boolean groupOnly = ClientGroupState.talksToGroupOnly();
			addDrawableChild(ButtonWidget.builder(
							Text.translatable(groupOnly ? "gui.voicy.talk_group_only" : "gui.voicy.talk_everyone"),
							button -> ClientGroupState.send(new GroupActionPayload(Action.SET_TALK_GROUP_ONLY,
									new UUID(0L, 0L), "", 0, !ClientGroupState.talksToGroupOnly())))
					.dimensions(cx - 100, y, 200, 20).build());
			y += 24;
			addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.name_color"),
							button -> client.setScreen(new NameColorScreen(this)))
					.dimensions(cx - 100, y, 98, 20).build());
			addDrawableChild(ButtonWidget.builder(
							Text.translatable(owner ? "gui.voicy.disband_group" : "gui.voicy.leave_group"),
							button -> ClientGroupState.send(owner ? Action.DISBAND : Action.LEAVE))
					.dimensions(cx + 2, y, 98, 20).build());
			y += 24;
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(cx - 100, y, 200, 20).build());
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
		GroupInfo group = ClientGroupState.myGroup();
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 10, 0xFFFFFFFF);
		Text subtitle = group == null
				? Text.translatable("gui.voicy.not_in_group")
				: Text.translatable(group.isPublic() ? "gui.voicy.group_line_public" : "gui.voicy.group_line_private",
						group.name(), group.members().size(), group.maxPlayers());
		context.drawCenteredTextWithShadow(textRenderer, subtitle, cx, 23, 0xFFAAAAAA);
	}

	private static final class GroupList extends ElementListWidget<GroupList.Row> {

		GroupList(MinecraftClient client, int width, int height, int top) {
			super(client, width, height, top, ROW_HEIGHT);
		}

		void load(GroupInfo group, boolean owner) {
			if (group != null) {
				addEntry(new HeaderRow(Text.translatable("gui.voicy.members")));
				for (Member member : group.members()) {
					addEntry(new MemberRow(member, group.owner().equals(member.id()), owner));
				}
				List<GroupStatePayload.Request> requests = ClientGroupState.requests();
				if (owner && !requests.isEmpty()) {
					addEntry(new HeaderRow(Text.translatable("gui.voicy.join_requests")));
					for (GroupStatePayload.Request request : requests) {
						addEntry(new RequestRow(request));
					}
				}
				return;
			}
			List<GroupStatePayload.Invitation> invitations = ClientGroupState.invitations();
			if (invitations.isEmpty()) {
				addEntry(new HeaderRow(Text.translatable("gui.voicy.no_invitations")));
				return;
			}
			addEntry(new HeaderRow(Text.translatable("gui.voicy.invitations")));
			for (GroupStatePayload.Invitation invitation : invitations) {
				addEntry(new InvitationRow(invitation));
			}
		}

		@Override
		public int getRowWidth() {
			return Math.min(width - 40, 320);
		}

		abstract static class Row extends ElementListWidget.Entry<Row> {
			@Override
			public List<? extends Element> children() {
				return List.of();
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return List.of();
			}
		}

		static final class HeaderRow extends Row {
			private final Text text;

			HeaderRow(Text text) {
				this.text = text;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				var font = MinecraftClient.getInstance().textRenderer;
				context.drawTextWithShadow(font, text, x + 4, y + 9, 0xFFAAAAAA);
			}
		}

		abstract static class PersonRow extends Row {
			private final UUID id;
			private final Text name;
			private final Text tag;
			private final List<ButtonWidget> buttons;

			PersonRow(UUID id, Text name, Text tag, List<ButtonWidget> buttons) {
				this.id = id;
				this.name = name;
				this.tag = tag;
				this.buttons = buttons;
			}

			@Override
			public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight,
							   int mouseX, int mouseY, boolean hovered, float delta) {
				var font = MinecraftClient.getInstance().textRenderer;
				GuiUtil.drawHead(context, id, x + 4, y + 5, 16);
				context.drawTextWithShadow(font, name, x + 26, tag == null ? y + 9 : y + 4, 0xFFFFFFFF);
				if (tag != null) {
					context.drawTextWithShadow(font, tag, x + 26, y + 15, 0xFF999999);
				}
				int right = x + entryWidth - 2;
				for (int i = buttons.size() - 1; i >= 0; i--) {
					ButtonWidget button = buttons.get(i);
					right -= button.getWidth();
					button.setX(right);
					button.setY(y + 2);
					button.render(context, mouseX, mouseY, delta);
					right -= 4;
				}
			}

			@Override
			public List<? extends Element> children() {
				return buttons;
			}

			@Override
			public List<? extends Selectable> selectableChildren() {
				return buttons;
			}
		}

		static final class MemberRow extends PersonRow {
			MemberRow(Member member, boolean isOwner, boolean viewerIsOwner) {
				super(member.id(),
						GuiUtil.coloredName(member.id(), member.name()),
						isOwner ? Text.translatable("gui.voicy.owner") : null,
						viewerIsOwner && !isOwner
								? List.of(ButtonWidget.builder(Text.translatable("gui.voicy.kick_short"),
										button -> ClientGroupState.send(Action.KICK, member.id()))
								.dimensions(0, 0, 44, 20).build())
								: List.of());
			}
		}

		static final class RequestRow extends PersonRow {
			RequestRow(GroupStatePayload.Request request) {
				super(request.player(),
						Text.literal(request.name()),
						Text.translatable("gui.voicy.wants_to_join"),
						List.of(
								ButtonWidget.builder(Text.translatable("gui.voicy.accept"),
										button -> ClientGroupState.send(Action.ACCEPT_REQUEST, request.player()))
										.dimensions(0, 0, 50, 20).build(),
								ButtonWidget.builder(Text.translatable("gui.voicy.decline"),
										button -> ClientGroupState.send(Action.DECLINE_REQUEST, request.player()))
										.dimensions(0, 0, 50, 20).build()));
			}
		}

		static final class InvitationRow extends PersonRow {
			InvitationRow(GroupStatePayload.Invitation invitation) {
				super(new UUID(0L, 0L),
						Text.literal(invitation.groupName()),
						Text.translatable("gui.voicy.invited_by", invitation.inviterName()),
						List.of(
								ButtonWidget.builder(Text.translatable("gui.voicy.accept"),
										button -> ClientGroupState.send(Action.ACCEPT_INVITE, invitation.groupId()))
										.dimensions(0, 0, 50, 20).build(),
								ButtonWidget.builder(Text.translatable("gui.voicy.decline"),
										button -> ClientGroupState.send(Action.DECLINE_INVITE, invitation.groupId()))
										.dimensions(0, 0, 50, 20).build()));
			}

		}
	}
}
