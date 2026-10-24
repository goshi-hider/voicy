package dev.goshi.voicy.client.music;

import dev.goshi.voicy.config.MusicMode;
import dev.goshi.voicy.config.RepeatMode;
import dev.goshi.voicy.config.VoicyConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

public final class MusicWidget {
	public static final int WIDTH = 180;
	public static final int HEIGHT = 60;
	public static final int MARGIN = 6;
	public static final float MIN_SCALE = 0.6f;
	public static final float MAX_SCALE = 2.0f;

	private static final int BG = 0xE60C0C11;
	private static final int GRAY = 0xFFA3A3B5;
	private static final int DIM = 0xFF50505F;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int TRACK = 0xFF2B2B38;
	private static final int INK = 0xFF0B0B10;
	private static final int IDLE_ACCENT = 0xFF8C8CA6;

	private static final int DISC_X = 30;
	private static final int DISC_Y = 30;
	private static final int DISC_R = 23;

	private static final int TEXT_X = 62;
	private static final int TEXT_RIGHT = WIDTH - 8;
	private static final int TEXT_W = TEXT_RIGHT - TEXT_X;
	private static final int TITLE_W = 90;
	private static final int TITLE_Y = 7;
	private static final int SUBTITLE_Y = 18;

	private static final int ROW_Y = 35;
	private static final int SMALL = 12;
	private static final int PLAY = 18;
	private static final int GAP = 8;
	private static final int ROW_W = SMALL * 4 + PLAY + GAP * 4;
	private static final int ROW_X = TEXT_X + (TEXT_W - ROW_W) / 2;

	private static final int BAR_Y = 50;
	private static final int TIME_W = 22;
	private static final int BAR_X = TEXT_X + TIME_W;
	private static final int BAR_W = TEXT_W - TIME_W * 2;

	private static final int SHUFFLE = 0;
	private static final int PREVIOUS = 1;
	private static final int PLAY_PAUSE = 2;
	private static final int NEXT = 3;
	private static final int REPEAT = 4;

	private static final int[][] DISC_BODY = ring(DISC_R, 0);
	private static final int[][] DISC_RIM = ring(DISC_R, DISC_R - 1);
	private static final int[][] GROOVE_A = ring(19, 18);
	private static final int[][] GROOVE_B = ring(16, 15);
	private static final int[][] GROOVE_C = ring(13, 12);
	private static final int[][] LABEL = ring(8, 0);
	private static final int[][] LABEL_EDGE = ring(8, 7);
	private static final int[][] HOLE = ring(2, 0);

	private static long lastFrame;
	private static float spinAngle;
	private static float spinSpeed;
	private static int accent = IDLE_ACCENT;

	private MusicWidget() {
	}

	public static float scale() {
		return VoicyConfig.INSTANCE.musicWidgetScale();
	}

	public static int width(float scale) {
		return Math.round(WIDTH * scale);
	}

	public static int height(float scale) {
		return Math.round(HEIGHT * scale);
	}

	public static int hudX(int screenWidth, float scale) {
		return MARGIN + Math.round(VoicyConfig.INSTANCE.musicWidgetX() * Math.max(0, screenWidth - width(scale) - MARGIN * 2));
	}

	public static int hudY(int screenHeight, float scale) {
		return MARGIN + Math.round(VoicyConfig.INSTANCE.musicWidgetY() * Math.max(0, screenHeight - height(scale) - MARGIN * 2));
	}

	public static float fractionX(int x, int screenWidth, float scale) {
		int free = screenWidth - width(scale) - MARGIN * 2;
		return free <= 0 ? 0f : MathHelper.clamp((x - MARGIN) / (float) free, 0f, 1f);
	}

	public static float fractionY(int y, int screenHeight, float scale) {
		int free = screenHeight - height(scale) - MARGIN * 2;
		return free <= 0 ? 0f : MathHelper.clamp((y - MARGIN) / (float) free, 0f, 1f);
	}

	public static boolean contains(int x, int y, double mouseX, double mouseY, float scale) {
		return mouseX >= x && mouseX < x + width(scale) && mouseY >= y && mouseY < y + height(scale);
	}

	private static int[][] ring(int outer, int inner) {
		List<int[]> rows = new ArrayList<>();
		for (int dy = -outer; dy < outer; dy++) {
			double yy = dy + 0.5;
			int ho = (int) Math.round(Math.sqrt(Math.max(0, outer * outer - yy * yy)));
			int hi = 0;
			if (inner > 0 && Math.abs(yy) < inner) {
				hi = (int) Math.round(Math.sqrt(inner * inner - yy * yy));
			}
			if (hi <= 0) {
				rows.add(new int[]{dy, -ho, ho});
			} else {
				rows.add(new int[]{dy, -ho, -hi});
				rows.add(new int[]{dy, hi, ho});
			}
		}
		return rows.toArray(new int[0][]);
	}

	private static void fillSpans(DrawContext context, int[][] spans, int cx, int cy, int color) {
		for (int[] s : spans) {
			context.fill(cx + s[1], cy + s[0], cx + s[2], cy + s[0] + 1, color);
		}
	}

	private static int alpha(int color, int a) {
		return (a << 24) | (color & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		t = MathHelper.clamp(t, 0f, 1f);
		int aa = Math.round(((a >>> 24) & 0xFF) * (1 - t) + ((b >>> 24) & 0xFF) * t);
		int ar = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
		int ag = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
		int ab = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
		return (aa << 24) | (ar << 16) | (ag << 8) | ab;
	}

	private static int accentFor(String title) {
		if (title.isEmpty()) {
			return IDLE_ACCENT;
		}
		float hue = ((title.hashCode() & 0x7FFFFFFF) % 360) / 360f;
		return 0xFF000000 | MathHelper.hsvToRgb(hue, 0.62f, 1.0f);
	}

	private static void advance(long now, boolean spinning, int targetAccent) {
		if (lastFrame == 0L) {
			lastFrame = now;
		}
		float dt = Math.min(0.1f, Math.max(0f, (now - lastFrame) / 1000f));
		lastFrame = now;
		float target = spinning ? 210f : 0f;
		spinSpeed += (target - spinSpeed) * Math.min(1f, dt * 3f);
		spinAngle = (spinAngle + spinSpeed * dt) % 360f;
		accent = mix(accent, targetAccent, Math.min(1f, dt * 6f));
	}

	private static int itemX(int item) {
		int x = ROW_X;
		for (int i = 0; i < item; i++) {
			x += (i == PLAY_PAUSE ? PLAY : SMALL) + GAP;
		}
		return x;
	}

	private static int itemSize(int item) {
		return item == PLAY_PAUSE ? PLAY : SMALL;
	}

	private static boolean overItem(int item, double mx, double my) {
		int size = itemSize(item);
		int ix = itemX(item);
		int iy = ROW_Y - size / 2;
		return mx >= ix - 2 && mx < ix + size + 2 && my >= iy - 2 && my < iy + size + 2;
	}

	private static boolean overBar(double mx, double my) {
		return mx >= BAR_X - 2 && mx < BAR_X + BAR_W + 2 && my >= BAR_Y - 5 && my < BAR_Y + 7;
	}

	public static void render(DrawContext context, int x, int y, int mouseX, int mouseY, float scale) {
		MusicPlayer player = MusicPlayer.INSTANCE;
		VoicyConfig config = VoicyConfig.INSTANCE;
		TextRenderer font = MinecraftClient.getInstance().textRenderer;
		long now = Util.getMeasuringTimeMs();
		String title = player.title();
		boolean idle = title.isEmpty();
		boolean playing = player.state() == MusicPlayer.State.PLAYING;
		advance(now, playing, accentFor(title));
		int ac = accent;
		double mx = (mouseX - x) / scale;
		double my = (mouseY - y) / scale;
		boolean local = config.musicMode() == MusicMode.LOCAL;

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x, y, 0f);
		matrices.scale(scale, scale, 1f);

		drawPanel(context, ac);
		drawDisc(context, ac, playing, idle, player.level());
		drawBars(context, ac, playing, player.level(), now);

		String subtitle = player.subtitle();
		if (!subtitle.isEmpty()) {
			context.drawTextWithShadow(font, font.trimToWidth(subtitle, TEXT_W), TEXT_X, SUBTITLE_Y, mix(ac, GRAY, 0.55f));
		}

		drawShuffle(context, itemX(SHUFFLE), ROW_Y - 4,
				!local ? DIM : config.musicShuffle() ? ac : overItem(SHUFFLE, mx, my) ? WHITE : GRAY,
				local && config.musicShuffle());
		drawSkip(context, itemX(PREVIOUS), ROW_Y - 4, true, overItem(PREVIOUS, mx, my) ? WHITE : GRAY);
		drawPlayButton(context, itemX(PLAY_PAUSE), ROW_Y - PLAY / 2, player.isPlaying(), overItem(PLAY_PAUSE, mx, my), ac);
		drawSkip(context, itemX(NEXT), ROW_Y - 4, false, overItem(NEXT, mx, my) ? WHITE : GRAY);
		RepeatMode repeat = config.musicRepeat();
		boolean repeating = local && repeat != RepeatMode.OFF;
		drawRepeat(context, font, itemX(REPEAT), ROW_Y - 4, repeat,
				!local ? DIM : repeating ? ac : overItem(REPEAT, mx, my) ? WHITE : GRAY, repeating);

		drawSeekBar(context, font, player, ac, now, overBar(mx, my));
		matrices.pop();

		drawTitle(context, font, idle ? "Nothing playing" : title, x, y, scale, idle ? GRAY : WHITE, now);
	}

	private static void drawTitle(DrawContext context, TextRenderer font, String text, int x, int y, float scale, int color, long now) {
		MatrixStack matrices = context.getMatrices();
		int textWidth = font.getWidth(text);
		int tx = x + Math.round(TEXT_X * scale);
		int ty = y + Math.round(TITLE_Y * scale);
		boolean scroll = textWidth > TITLE_W;
		if (scroll) {
			context.enableScissor(tx, ty - Math.round(2 * scale), tx + Math.round(TITLE_W * scale), ty + Math.round(10 * scale));
		}
		matrices.push();
		matrices.translate(tx, ty, 0f);
		matrices.scale(scale, scale, 1f);
		if (scroll) {
			int cycle = textWidth + 28;
			long phase = (now / 30) % (cycle + 50);
			int offset = (int) Math.max(0, phase - 50);
			context.drawTextWithShadow(font, text, -offset, 0, color);
			context.drawTextWithShadow(font, text, -offset + cycle, 0, color);
		} else {
			context.drawTextWithShadow(font, text, 0, 0, color);
		}
		matrices.pop();
		if (scroll) {
			context.disableScissor();
		}
	}

	private static void shape(DrawContext c, int x, int y, int w, int h, int tl, int tr, int br, int bl, int color) {
		int top = Math.max(tl, tr);
		int bottom = Math.max(bl, br);
		for (int i = 0; i < top; i++) {
			c.fill(x + Math.max(0, tl - i), y + i, x + w - Math.max(0, tr - i), y + i + 1, color);
		}
		if (h - bottom > top) {
			c.fill(x, y + top, x + w, y + h - bottom, color);
		}
		for (int d = bottom - 1; d >= 0; d--) {
			c.fill(x + Math.max(0, bl - d), y + h - 1 - d, x + w - Math.max(0, br - d), y + h - d, color);
		}
	}

	private static void drawPanel(DrawContext c, int ac) {
		shape(c, 2, 2, WIDTH, HEIGHT, 6, 2, 6, 2, 0x55000000);
		shape(c, 0, 0, WIDTH, HEIGHT, 6, 2, 6, 2, alpha(ac, 0x80));
		shape(c, 1, 1, WIDTH - 2, HEIGHT - 2, 5, 1, 5, 1, BG);
		c.fillGradient(7, 1, WIDTH - 7, HEIGHT - 1, alpha(ac, 0x28), alpha(ac, 0x00));
		c.fill(10, 0, 42, 2, ac);
		c.fill(WIDTH - 26, HEIGHT - 2, WIDTH - 12, HEIGHT, alpha(ac, 0xB0));
	}

	private static void drawDisc(DrawContext c, int ac, boolean playing, boolean idle, float level) {
		int cx = DISC_X;
		int cy = DISC_Y;
		int rimBase = 0xFF2A2A34;
		int rim = playing ? mix(rimBase, ac, 0.35f + 0.65f * Math.min(1f, level * 1.4f)) : rimBase;
		fillSpans(c, DISC_BODY, cx, cy, 0xFF0D0D12);
		fillSpans(c, DISC_RIM, cx, cy, rim);
		fillSpans(c, GROOVE_A, cx, cy, 0xFF1B1B24);
		fillSpans(c, GROOVE_B, cx, cy, 0xFF1B1B24);
		fillSpans(c, GROOVE_C, cx, cy, 0xFF1B1B24);
		int label = idle ? 0xFF3A3A48 : mix(ac, 0xFF000000, 0.12f);
		fillSpans(c, LABEL, cx, cy, label);
		fillSpans(c, LABEL_EDGE, cx, cy, mix(label, 0xFF000000, 0.45f));
		fillSpans(c, HOLE, cx, cy, INK);

		MatrixStack m = c.getMatrices();
		m.push();
		m.translate(cx, cy, 0f);
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));
		c.fill(10, 0, 22, 1, 0x48FFFFFF);
		c.fill(-21, -1, -11, 0, 0x24FFFFFF);
		c.fill(5, -1, 7, 1, idle ? DIM : WHITE);
		m.pop();
	}

	private static void drawBars(DrawContext c, int ac, boolean playing, float level, long now) {
		int bars = 6;
		int baseX = WIDTH - 8 - (bars * 3 - 1);
		int base = 16;
		float t = now / 1000f;
		float lv = playing ? Math.max(0.18f, level) : 0f;
		for (int i = 0; i < bars; i++) {
			float wobble = 0.5f + 0.5f * MathHelper.sin(t * (7f + i * 2.3f) + i * 1.9f);
			int h = playing ? 2 + Math.round(lv * 8f * wobble) : 2;
			int bx = baseX + i * 3;
			int color = playing ? ac : DIM;
			c.fill(bx, base - h, bx + 2, base, color);
			if (playing) {
				c.fill(bx, base - h, bx + 2, base - h + 1, mix(color, WHITE, 0.55f));
			}
		}
	}

	private static void drawPlayButton(DrawContext c, int x, int y, boolean playing, boolean hovered, int ac) {
		int fill = hovered ? mix(ac, WHITE, 0.4f) : ac;
		shape(c, x, y, PLAY, PLAY, 4, 0, 4, 0, fill);
		if (playing) {
			c.fill(x + 5, y + 4, x + 8, y + 14, INK);
			c.fill(x + 10, y + 4, x + 13, y + 14, INK);
		} else {
			for (int col = 0; col < 5; col++) {
				c.fill(x + 5 + col * 2, y + 4 + col, x + 7 + col * 2, y + 14 - col, INK);
			}
		}
	}

	private static void drawSkip(DrawContext context, int x, int y, boolean backwards, int color) {
		if (backwards) {
			context.fill(x + 1, y, x + 3, y + 8, color);
			for (int c = 0; c < 4; c++) {
				context.fill(x + 10 - c * 2 - 2, y + c, x + 10 - c * 2, y + 8 - c, color);
			}
		} else {
			context.fill(x + 9, y, x + 11, y + 8, color);
			for (int c = 0; c < 4; c++) {
				context.fill(x + c * 2 + 1, y + c, x + c * 2 + 3, y + 8 - c, color);
			}
		}
	}

	private static void plot(DrawContext context, int x, int y, int color) {
		context.fill(x, y, x + 1, y + 1, color);
	}

	private static void line(DrawContext context, int x0, int y0, int x1, int y1, int color) {
		int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
		for (int i = 0; i <= steps; i++) {
			float f = steps == 0 ? 0f : i / (float) steps;
			plot(context, Math.round(x0 + (x1 - x0) * f), Math.round(y0 + (y1 - y0) * f), color);
		}
	}

	private static void arrowRight(DrawContext context, int x, int y, int color) {
		for (int c = 0; c < 3; c++) {
			context.fill(x + c, y - 2 + c, x + c + 1, y + 3 - c, color);
		}
	}

	private static void drawShuffle(DrawContext context, int x, int y, int color, boolean active) {
		line(context, x, y + 2, x + 2, y + 2, color);
		line(context, x + 2, y + 2, x + 6, y + 6, color);
		line(context, x + 6, y + 6, x + 8, y + 6, color);
		arrowRight(context, x + 8, y + 6, color);
		line(context, x, y + 6, x + 2, y + 6, color);
		line(context, x + 2, y + 6, x + 6, y + 2, color);
		line(context, x + 6, y + 2, x + 8, y + 2, color);
		arrowRight(context, x + 8, y + 2, color);
		if (active) {
			context.fill(x + 4, y + 10, x + 7, y + 11, color);
		}
	}

	private static void drawRepeat(DrawContext context, TextRenderer font, int x, int y, RepeatMode mode, int color, boolean active) {
		line(context, x + 1, y + 1, x + 7, y + 1, color);
		arrowRight(context, x + 8, y + 1, color);
		line(context, x + 10, y + 3, x + 10, y + 6, color);
		line(context, x + 1, y + 7, x + 10, y + 7, color);
		line(context, x, y + 3, x, y + 6, color);
		if (mode == RepeatMode.ONE) {
			MatrixStack matrices = context.getMatrices();
			matrices.push();
			matrices.translate(x + 4, y + 2, 0f);
			matrices.scale(0.6f, 0.6f, 1f);
			context.drawText(font, "1", 0, 0, color, false);
			matrices.pop();
		}
		if (active) {
			context.fill(x + 4, y + 10, x + 7, y + 11, color);
		}
	}

	private static void drawSeekBar(DrawContext context, TextRenderer font, MusicPlayer player, int ac, long now, boolean hovered) {
		int bx = BAR_X;
		int by = BAR_Y;
		context.fill(bx, by, bx + BAR_W, by + 2, TRACK);

		if (player.isLive()) {
			boolean live = player.state() == MusicPlayer.State.PLAYING;
			int pulse = live ? 0x80 + Math.round(0x7F * (0.5f + 0.5f * MathHelper.sin(now / 260f))) : 0x50;
			context.fill(TEXT_X, by - 1, TEXT_X + 3, by + 3, alpha(0xFF4D5E, pulse));
			smallText(context, font, "LIVE", TEXT_X + 5, by - 2, live ? WHITE : GRAY);
			if (live) {
				int seg = 16;
				int pos = (int) ((now / 14) % (BAR_W + seg)) - seg;
				int s = Math.max(0, pos);
				int e = Math.min(BAR_W, pos + seg);
				if (e > s) {
					context.fill(bx + s, by, bx + e, by + 2, ac);
				}
			}
			return;
		}

		double duration = player.durationSeconds();
		double position = player.positionSeconds();
		smallText(context, font, format(position), TEXT_X, by - 2, GRAY);
		if (duration <= 0) {
			return;
		}
		String total = format(duration);
		smallText(context, font, total, TEXT_RIGHT - Math.round(font.getWidth(total) * 0.75f), by - 2, GRAY);
		int filled = (int) Math.round(BAR_W * MathHelper.clamp(position / duration, 0.0, 1.0));
		context.fill(bx, by, bx + filled, by + 2, ac);
		if (hovered) {
			context.fill(bx + filled - 2, by - 3, bx + filled + 2, by + 5, alpha(ac, 0x66));
		}
		context.fill(bx + filled - 1, by - 2, bx + filled + 1, by + 4, WHITE);
	}

	private static void smallText(DrawContext context, TextRenderer font, String text, int x, int y, int color) {
		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(x, y, 0f);
		matrices.scale(0.75f, 0.75f, 1f);
		context.drawTextWithShadow(font, text, 0, 0, color);
		matrices.pop();
	}

	private static String format(double seconds) {
		int total = (int) Math.max(0, seconds);
		return (total / 60) + ":" + String.format("%02d", total % 60);
	}

	public static boolean click(int x, int y, double mouseX, double mouseY, float scale) {
		MusicPlayer player = MusicPlayer.INSTANCE;
		VoicyConfig config = VoicyConfig.INSTANCE;
		boolean local = config.musicMode() == MusicMode.LOCAL;
		double mx = (mouseX - x) / scale;
		double my = (mouseY - y) / scale;

		if (overItem(PLAY_PAUSE, mx, my)) {
			player.togglePause();
			return true;
		}
		if (overItem(PREVIOUS, mx, my)) {
			player.previous();
			return true;
		}
		if (overItem(NEXT, mx, my)) {
			player.next();
			return true;
		}
		if (local && overItem(SHUFFLE, mx, my)) {
			config.setMusicShuffle(!config.musicShuffle());
			return true;
		}
		if (local && overItem(REPEAT, mx, my)) {
			config.setMusicRepeat(config.musicRepeat().next());
			return true;
		}
		if (!player.isLive() && player.durationSeconds() > 0 && overBar(mx, my)) {
			player.seek(MathHelper.clamp((mx - BAR_X) / BAR_W, 0.0, 1.0));
			return true;
		}
		return false;
	}
}
