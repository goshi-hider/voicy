package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.group.GroupManager;
import dev.goshi.voicy.group.GroupNameColors;
import dev.goshi.voicy.network.payload.GroupActionPayload;
import dev.goshi.voicy.network.payload.GroupActionPayload.Action;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

public final class NameColorScreen extends VoicyScreen {

	private static final int COLUMNS = 8;
	private static final int SWATCH_W = 24;
	private static final int SWATCH_H = 24;
	private static final int GAP = 4;

	private final Screen parent;
	private int seenVersion;

	public NameColorScreen(Screen parent) {
		super(Text.translatable("gui.voicy.name_color"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		seenVersion = ClientGroupState.version();
		int cx = width / 2;
		int gridWidth = COLUMNS * SWATCH_W + (COLUMNS - 1) * GAP;
		int left = cx - gridWidth / 2;
		int top = 60;
		for (int index = 0; index < GroupManager.COLOR_COUNT; index++) {
			int column = index % COLUMNS;
			int row = index / COLUMNS;
			addDrawableChild(new Swatch(left + column * (SWATCH_W + GAP), top + row * (SWATCH_H + GAP), index));
		}
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.default_color"),
						button -> sendColor(GroupManager.NO_COLOR))
				.dimensions(cx - 100, top + 2 * (SWATCH_H + GAP) + 30, 200, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> client.setScreen(parent))
				.dimensions(cx - 100, height - 28, 200, 20).build());
	}

	private static void sendColor(int index) {
		ClientGroupState.send(new GroupActionPayload(Action.SET_NAME_COLOR, new UUID(0L, 0L), "", index, false));
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
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 14, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.voicy.name_color_help"), cx, 30, 0xFFAAAAAA);

		String name = MinecraftClient.getInstance().player == null ? "" : MinecraftClient.getInstance().player.getGameProfile().getName();
		Formatting selected = GroupNameColors.fromIndex(ClientGroupState.myColor());
		Text preview = Text.literal(name);
		if (selected != null) {
			preview = Text.literal(name).formatted(selected);
		}
		context.drawCenteredTextWithShadow(textRenderer, preview, cx, 44, 0xFFFFFFFF);
	}

	private static final class Swatch extends ClickableWidget {

		private final int index;

		Swatch(int x, int y, int index) {
			super(x, y, SWATCH_W, SWATCH_H, Text.translatable("gui.voicy.color." + Formatting.byColorIndex(index).getName()));
			this.index = index;
			setTooltip(Tooltip.of(getMessage()));
		}

		@Override
		protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
			Integer rgb = Formatting.byColorIndex(index).getColorValue();
			int color = 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb);
			boolean selected = ClientGroupState.myColor() == index;
			int x = getX();
			int y = getY();
			int borderColor = selected ? 0xFFFFFFFF : isHovered() ? 0xFFCCCCCC : 0xFF555555;
			context.fill(x, y, x + width, y + height, borderColor);
			int inset = selected ? 2 : 1;
			context.fill(x + inset, y + inset, x + width - inset, y + height - inset, color);
			if (selected) {
				context.drawBorder(x - 1, y - 1, width + 2, height + 2, 0xFFFFFFFF);
			}
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			sendColor(index);
		}

		@Override
		protected void appendClickableNarrations(NarrationMessageBuilder builder) {
			builder.put(NarrationPart.TITLE, getMessage());
		}
	}
}
