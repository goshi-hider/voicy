package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.music.MusicWidget;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class MusicWidgetEditorScreen extends VoicyScreen {
	private static final int SNAP = 5;
	private static final int HANDLE = 6;
	private static final int ACCENT = 0xFF3BD16F;

	private final Screen parent;
	private final VoicyConfig config = VoicyConfig.INSTANCE;

	private float scale;
	private int widgetX;
	private int widgetY;
	private boolean dragging;
	private int grabX;
	private int grabY;
	private boolean snappedX;
	private boolean snappedY;
	private int resizeCorner = -1;
	private int anchorX;
	private int anchorY;

	public MusicWidgetEditorScreen(Screen parent) {
		super(Text.translatable("screen.voicy.widget_editor"));
		this.parent = parent;
	}

	private int w() {
		return MusicWidget.width(scale);
	}

	private int h() {
		return MusicWidget.height(scale);
	}

	private void place() {
		widgetX = MusicWidget.hudX(width, scale);
		widgetY = MusicWidget.hudY(height, scale);
	}

	private float maxScale() {
		float fit = Math.min((width - MusicWidget.MARGIN * 2) / (float) MusicWidget.WIDTH,
				(height - MusicWidget.MARGIN * 2) / (float) MusicWidget.HEIGHT);
		return Math.max(MusicWidget.MIN_SCALE, Math.min(MusicWidget.MAX_SCALE, fit));
	}

	private static float snapScale(float value) {
		return Math.round(value * 20f) / 20f;
	}

	@Override
	protected void init() {
		scale = Math.min(MusicWidget.scale(), maxScale());
		place();
		int cx = width / 2;
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.voicy.music.reset_position"), button -> {
			config.setMusicWidgetPosition(0f, 0f);
			config.setMusicWidgetScale(1f);
			scale = Math.min(1f, maxScale());
			place();
		}).dimensions(cx - 102, height - 28, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> client.setScreen(parent))
				.dimensions(cx + 2, height - 28, 100, 20).build());
	}

	@Override
	protected int dimColor() {
		return 0x50101010;
	}

	private int cornerAt(double mouseX, double mouseY) {
		int[][] points = {
				{widgetX, widgetY},
				{widgetX + w(), widgetY},
				{widgetX, widgetY + h()},
				{widgetX + w(), widgetY + h()}
		};
		for (int i = 0; i < points.length; i++) {
			if (Math.abs(mouseX - points[i][0]) <= HANDLE && Math.abs(mouseY - points[i][1]) <= HANDLE) {
				return i;
			}
		}
		return -1;
	}

	private void handle(DrawContext context, int px, int py, boolean active) {
		context.fill(px - 3, py - 3, px + 3, py + 3, 0xFF101014);
		context.fill(px - 2, py - 2, px + 2, py + 2, active ? ACCENT : 0xFFE6E6EE);
	}

	@Override
	protected void renderPanels(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = width / 2;
		context.drawCenteredTextWithShadow(textRenderer, title, cx, 12, 0xFFFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.voicy.music.drag_hint"), cx, 24, 0xFF999999);

		if (dragging) {
			int guide = 0x55FFFFFF;
			if (snappedX) {
				context.fill(cx, 0, cx + 1, height, guide);
			}
			if (snappedY) {
				int cy = height / 2;
				context.fill(0, cy, width, cy + 1, guide);
			}
		}

		MusicWidget.render(context, widgetX, widgetY, mouseX, mouseY, scale);

		int w = w();
		int h = h();
		boolean busy = dragging || resizeCorner >= 0;
		boolean over = MusicWidget.contains(widgetX, widgetY, mouseX, mouseY, scale);
		int hoverCorner = resizeCorner >= 0 ? resizeCorner : cornerAt(mouseX, mouseY);

		int outline = busy ? ACCENT : 0x66FFFFFF;
		context.fill(widgetX - 1, widgetY - 1, widgetX + w + 1, widgetY, outline);
		context.fill(widgetX - 1, widgetY + h, widgetX + w + 1, widgetY + h + 1, outline);
		context.fill(widgetX - 1, widgetY, widgetX, widgetY + h, outline);
		context.fill(widgetX + w, widgetY, widgetX + w + 1, widgetY + h, outline);

		handle(context, widgetX, widgetY, hoverCorner == 0);
		handle(context, widgetX + w, widgetY, hoverCorner == 1);
		handle(context, widgetX, widgetY + h, hoverCorner == 2);
		handle(context, widgetX + w, widgetY + h, hoverCorner == 3);

		if (busy || over || hoverCorner >= 0) {
			String label = Math.round(scale * 100f) + "%";
			int ly = widgetY + h + 8 + 10 < height - 34 ? widgetY + h + 8 : widgetY - 14;
			context.drawCenteredTextWithShadow(textRenderer, label, widgetX + w / 2, ly, resizeCorner >= 0 ? ACCENT : 0xFFFFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != 0) {
			return false;
		}
		int corner = cornerAt(mouseX, mouseY);
		if (corner >= 0) {
			resizeCorner = corner;
			anchorX = corner == 0 || corner == 2 ? widgetX + w() : widgetX;
			anchorY = corner == 0 || corner == 1 ? widgetY + h() : widgetY;
			return true;
		}
		if (MusicWidget.contains(widgetX, widgetY, mouseX, mouseY, scale)) {
			if (MusicWidget.click(widgetX, widgetY, mouseX, mouseY, scale)) {
				return true;
			}
			dragging = true;
			grabX = (int) mouseX - widgetX;
			grabY = (int) mouseY - widgetY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (resizeCorner >= 0) {
			boolean right = resizeCorner == 1 || resizeCorner == 3;
			boolean bottom = resizeCorner == 2 || resizeCorner == 3;
			double spanX = right ? mouseX - anchorX : anchorX - mouseX;
			double spanY = bottom ? mouseY - anchorY : anchorY - mouseY;
			float wanted = (float) Math.max(spanX / MusicWidget.WIDTH, spanY / MusicWidget.HEIGHT);
			float roomX = right ? width - MusicWidget.MARGIN - anchorX : anchorX - MusicWidget.MARGIN;
			float roomY = bottom ? height - MusicWidget.MARGIN - anchorY : anchorY - MusicWidget.MARGIN;
			float limit = Math.min(maxScale(), Math.min(roomX / MusicWidget.WIDTH, roomY / MusicWidget.HEIGHT));
			limit = Math.max(MusicWidget.MIN_SCALE, limit);
			scale = MathHelper.clamp(snapScale(wanted), MusicWidget.MIN_SCALE, limit);
			widgetX = right ? anchorX : anchorX - w();
			widgetY = bottom ? anchorY : anchorY - h();
			return true;
		}
		if (!dragging) {
			return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
		}
		int minX = MusicWidget.MARGIN;
		int maxX = Math.max(minX, width - w() - MusicWidget.MARGIN);
		int minY = MusicWidget.MARGIN;
		int maxY = Math.max(minY, height - h() - MusicWidget.MARGIN);
		int x = MathHelper.clamp((int) mouseX - grabX, minX, maxX);
		int y = MathHelper.clamp((int) mouseY - grabY, minY, maxY);

		int centeredX = (width - w()) / 2;
		int centeredY = (height - h()) / 2;
		snappedX = Math.abs(x - centeredX) <= SNAP;
		snappedY = Math.abs(y - centeredY) <= SNAP;
		if (snappedX) {
			x = centeredX;
		}
		if (snappedY) {
			y = centeredY;
		}
		widgetX = x;
		widgetY = y;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (verticalAmount != 0 && MusicWidget.contains(widgetX, widgetY, mouseX, mouseY, scale)) {
			int centerX = widgetX + w() / 2;
			int centerY = widgetY + h() / 2;
			scale = MathHelper.clamp(snapScale(scale + (float) Math.signum(verticalAmount) * 0.05f),
					MusicWidget.MIN_SCALE, maxScale());
			widgetX = MathHelper.clamp(centerX - w() / 2, MusicWidget.MARGIN,
					Math.max(MusicWidget.MARGIN, width - w() - MusicWidget.MARGIN));
			widgetY = MathHelper.clamp(centerY - h() / 2, MusicWidget.MARGIN,
					Math.max(MusicWidget.MARGIN, height - h() - MusicWidget.MARGIN));
			persist();
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
	}

	private void persist() {
		config.setMusicWidgetScale(scale);
		config.setMusicWidgetPosition(MusicWidget.fractionX(widgetX, width, scale), MusicWidget.fractionY(widgetY, height, scale));
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (resizeCorner >= 0) {
			resizeCorner = -1;
			persist();
			return true;
		}
		if (dragging) {
			dragging = false;
			snappedX = false;
			snappedY = false;
			persist();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public void close() {
		client.setScreen(parent);
	}
}
