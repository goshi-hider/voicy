package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.group.GroupManager;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload.Action;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.UUID;

public final class CreateGroupScreen extends VoicyScreen {
	private final Screen parent;
	private TextFieldWidget nameField;
	private ButtonWidget createButton;
	private boolean isPublic;
	private int maxPlayers = GroupManager.DEFAULT_PLAYERS;
	private String nameDraft = "";

	public CreateGroupScreen(Screen parent) {
		super(Text.translatable("gui.voicy.create_group"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int cx = width / 2;

		nameField = new TextFieldWidget(textRenderer, cx - 100, 52, 200, 20, Text.translatable("gui.voicy.group_name"));
		nameField.setMaxLength(GroupManager.MAX_NAME_LENGTH);
		nameField.setTextPredicate(text -> text.matches("[A-Za-z0-9_-]*"));
		nameField.setPlaceholder(Text.translatable("gui.voicy.group_name_hint"));
		nameField.setText(nameDraft);
		nameField.setChangedListener(text -> {
			nameDraft = text;
			updateCreateButton();
		});
		addDrawableChild(nameField);
		setInitialFocus(nameField);

		addDrawableChild(ButtonWidget.builder(visibilityLabel(), button -> {
			isPublic = !isPublic;
			button.setMessage(visibilityLabel());
		}).dimensions(cx - 100, 82, 200, 20).build());

		double initial = (maxPlayers - GroupManager.MIN_PLAYERS) / (double) (GroupManager.MAX_PLAYERS - GroupManager.MIN_PLAYERS);
		addDrawableChild(new SliderWidget(cx - 100, 106, 200, 20, maxPlayersLabel(maxPlayers), initial) {
			@Override
			protected void updateMessage() {
				setMessage(maxPlayersLabel(fromSlider(value)));
			}

			@Override
			protected void applyValue() {
				maxPlayers = fromSlider(value);
			}
		});

		createButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.create"), button -> create())
				.dimensions(cx - 100, height - 28, 98, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> client.setScreen(parent))
				.dimensions(cx + 2, height - 28, 98, 20).build());
		updateCreateButton();
	}

	private static int fromSlider(double value) {
		return GroupManager.MIN_PLAYERS
				+ (int) Math.round(value * (GroupManager.MAX_PLAYERS - GroupManager.MIN_PLAYERS));
	}

	private static Text maxPlayersLabel(int players) {
		return Text.translatable("gui.voicy.max_players", players);
	}

	private Text visibilityLabel() {
		return Text.translatable(isPublic ? "gui.voicy.visibility_public" : "gui.voicy.visibility_private");
	}

	private boolean nameValid() {
		return nameField != null && GroupManager.isValidName(nameField.getText());
	}

	private void updateCreateButton() {
		if (createButton != null) {
			createButton.active = nameValid();
		}
	}

	private void create() {
		if (!nameValid()) {
			return;
		}
		ClientGroupState.send(new GroupActionPayload(Action.CREATE, new UUID(0L, 0L),
				nameField.getText().trim(), maxPlayers, isPublic));
		client.setScreen(parent);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if ((keyCode == 257 || keyCode == 335) && nameField.isFocused() && nameValid()) {
			create();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 14, 0xFFFFFFFF);
		context.drawTextWithShadow(textRenderer, Text.translatable("gui.voicy.group_name"), cx - 100, 40, 0xFFAAAAAA);
		Text rule = Text.translatable(isPublic ? "gui.voicy.visibility_public_help" : "gui.voicy.visibility_private_help");
		int lineY = 132;
		for (var line : textRenderer.wrapLines(rule, 200)) {
			context.drawTextWithShadow(textRenderer, line, cx - 100, lineY, 0xFF999999);
			lineY += 11;
		}
		if (!nameDraft.isEmpty() && !nameValid()) {
			context.drawTextWithShadow(textRenderer, Text.translatable("gui.voicy.name_too_short"),
					cx - 100, lineY + 6, 0xFFFF7777);
		}
	}
}
