package dev.goshi.voicy.client.gui;

import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.client.group.ClientGroupState;
import dev.goshi.voicy.group.GroupNameColors;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.GroupInfo;
import dev.goshi.voicy.network.payload.GroupDirectoryPayload.Member;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GroupHud {
	private static final int HEAD = 16;
	private static final int OUTLINE = 2;
	private static final int GAP = 4;
	private static final int MARGIN = 6;
	private static final int BACKING = 0x90000000;

	private final Map<UUID, Float> talkAmount = new HashMap<>();
	private final ClientVoiceManager voiceManager;
	private long lastFrameMs;

	private GroupHud(ClientVoiceManager voiceManager) {
		this.voiceManager = voiceManager;
	}

	public static void register(ClientVoiceManager voiceManager) {
		GroupHud hud = new GroupHud(voiceManager);
		HudRenderCallback.EVENT.register((context, tickCounter) -> hud.render(context));
	}

	private void render(DrawContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		long now = Util.getMeasuringTimeMs();
		float dt = lastFrameMs == 0L ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
		lastFrameMs = now;

		GroupInfo group = ClientGroupState.myGroup();
		if (client.player == null || group == null || client.options.hudHidden) {
			talkAmount.clear();
			return;
		}

		int top = MARGIN + statusEffectOffset(client);
		int step = HEAD + OUTLINE * 2 + GAP;
		int screenWidth = context.getScaledWindowWidth();
		int perColumn = Math.max(1, (context.getScaledWindowHeight() - top - MARGIN) / step);
		UUID self = client.player.getUuid();

		List<Member> members = group.members();
		for (int i = 0; i < members.size(); i++) {
			UUID id = members.get(i).id();
			boolean talking = id.equals(self) ? voiceManager.isSelfTalking() : voiceManager.isTalking(id);
			float amount = approach(id, talking, dt);

			int column = i / perColumn;
			int row = i % perColumn;
			int x = screenWidth - MARGIN - OUTLINE - HEAD - column * step;
			int y = top + OUTLINE + row * step;
			drawMember(context, id, x, y, amount, now, i);
		}
		talkAmount.keySet().removeIf(id -> !group.hasMember(id));
	}

	private float approach(UUID id, boolean talking, float dt) {
		float current = talkAmount.getOrDefault(id, 0f);
		float target = talking ? 1f : 0f;
		float rate = talking ? 12f : 5f;
		current = current < target ? Math.min(target, current + rate * dt) : Math.max(target, current - rate * dt);
		talkAmount.put(id, current);
		return current;
	}

	private static void drawMember(DrawContext context, UUID id, int x, int y, float amount, long now, int phase) {
		if (amount <= 0.01f) {
			context.fill(x - 1, y - 1, x + HEAD + 1, y + HEAD + 1, BACKING);
			GuiUtil.drawHead(context, id, x, y, HEAD);
			return;
		}

		float t = now / 1000f;
		float pulse = 0.5f + 0.5f * MathHelper.sin(t * 14f + phase * 1.7f);
		float tilt = MathHelper.sin(t * 19f + phase) * 7f * amount;
		float scale = 1f + 0.12f * amount * pulse;
		float centerX = x + HEAD / 2f;
		float centerY = y + HEAD / 2f;

		Formatting formatting = GroupNameColors.get(id);
		Integer groupColor = formatting == null ? null : formatting.getColorValue();
		int colorRgb = groupColor == null ? 0xFFFFFF : groupColor;

		MatrixStack matrices = context.getMatrices();
		matrices.push();
		matrices.translate(centerX, centerY, 0f);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tilt));
		matrices.scale(scale, scale, 1f);
		matrices.translate(-centerX, -centerY, 0f);

		float glow = amount * (0.6f + 0.4f * pulse);
		int outer = OUTLINE + 4;
		int inner = OUTLINE + 2;
		context.fill(x - outer, y - outer, x + HEAD + outer, y + HEAD + outer, argb(colorRgb, 0.10f * glow));
		context.fill(x - inner, y - inner, x + HEAD + inner, y + HEAD + inner, argb(colorRgb, 0.22f * glow));
		context.fill(x - OUTLINE, y - OUTLINE, x + HEAD + OUTLINE, y + HEAD + OUTLINE, argb(colorRgb, amount));
		GuiUtil.drawHead(context, id, x, y, HEAD);

		matrices.pop();
	}

	private static int statusEffectOffset(MinecraftClient client) {
		boolean beneficial = false;
		boolean other = false;
		for (StatusEffectInstance effect : client.player.getStatusEffects()) {
			if (!effect.shouldShowIcon()) {
				continue;
			}
			if (effect.getEffectType().value().getCategory() == StatusEffectCategory.BENEFICIAL) {
				beneficial = true;
			} else {
				other = true;
			}
		}
		return other ? 50 : beneficial ? 25 : 0;
	}

	private static int argb(int rgb, float alpha) {
		int a = (int) (MathHelper.clamp(alpha, 0f, 1f) * 255f);
		return (a << 24) | (rgb & 0xFFFFFF);
	}
}
