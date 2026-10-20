package dev.goshi.voicy.client.render;

import dev.goshi.voicy.client.AnimationStates;
import dev.goshi.voicy.client.TalkingHeadAnimator;
import dev.goshi.voicy.config.AnimationStyle;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

public final class TalkingSplitHeadFeatureRenderer
		extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

	private static final float TEX = 64f;

	public TalkingSplitHeadFeatureRenderer(
			FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
		super(context);
	}

	@Override
	public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
			AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
			float animationProgress, float headYaw, float headPitch) {
		if (player.isInvisible()) {
			return;
		}
		TalkingHeadAnimator.Pose pose = TalkingHeadAnimator.last(player.getUuid());
		if (pose == null || !pose.splitActive()) {
			return;
		}
		float split = AnimationStates.of(player.getUuid()).splitHeight();
		Identifier texture = player.getSkinTextures().texture();
		VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
		int overlay = LivingEntityRenderer.getOverlay(player, 0.0f);

		matrices.push();
		getContextModel().head.rotate(matrices);
		drawLayer(matrices, consumer, light, overlay, 0.0f, 0, pose, split);
		if (player.isPartVisible(PlayerModelPart.HAT)) {
			drawLayer(matrices, consumer, light, overlay, 0.5f, 32, pose, split);
		}
		matrices.pop();
	}

	private static void drawLayer(MatrixStack matrices, VertexConsumer consumer, int light, int overlay, float extra,
			int u0, TalkingHeadAnimator.Pose pose, float split) {
		float tCut = 1.0f - split;
		float yTop = -8.0f - extra;
		float yBottom = extra;
		float cutY = yTop + tCut * (yBottom - yTop);

		matrices.push();
		if (pose.style() == AnimationStyle.SPLIT_VERTICAL) {
			matrices.translate(0.0f, -pose.open() * 0.25f, 0.0f);
		}
		box(matrices.peek(), consumer, light, overlay, extra, u0, 0, 0.0f, tCut, true, false);
		matrices.pop();

		matrices.push();
		if (pose.style() == AnimationStyle.SPLIT_HINGE) {
			float hingeY = cutY / 16.0f;
			float hingeZ = (4.0f + extra) / 16.0f;
			matrices.translate(0.0f, hingeY, hingeZ);
			matrices.multiply(RotationAxis.POSITIVE_X.rotation(pose.open() * 0.55f));
			matrices.translate(0.0f, -hingeY, -hingeZ);
		}
		box(matrices.peek(), consumer, light, overlay, extra, u0, 0, tCut, 1.0f, false, true);
		matrices.pop();
	}

	private static void box(MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay, float e,
			int u0, int v0, float tA, float tB, boolean top, boolean bottom) {
		float x0 = -4.0f - e;
		float x1 = 4.0f + e;
		float z0 = -4.0f - e;
		float z1 = 4.0f + e;
		float yTop = -8.0f - e;
		float yBottom = e;
		float yA = yTop + tA * (yBottom - yTop);
		float yB = yTop + tB * (yBottom - yTop);
		float vA = v0 + 8 + tA * 8;
		float vB = v0 + 8 + tB * 8;

		quad(entry, consumer, light, overlay, 0, 0, -1,
				x0, yA, z0, u0 + 8, vA, x1, yA, z0, u0 + 16, vA, x1, yB, z0, u0 + 16, vB, x0, yB, z0, u0 + 8, vB);
		quad(entry, consumer, light, overlay, 0, 0, 1,
				x1, yA, z1, u0 + 24, vA, x0, yA, z1, u0 + 32, vA, x0, yB, z1, u0 + 32, vB, x1, yB, z1, u0 + 24, vB);
		quad(entry, consumer, light, overlay, -1, 0, 0,
				x0, yA, z0, u0 + 8, vA, x0, yA, z1, u0, vA, x0, yB, z1, u0, vB, x0, yB, z0, u0 + 8, vB);
		quad(entry, consumer, light, overlay, 1, 0, 0,
				x1, yA, z0, u0 + 16, vA, x1, yA, z1, u0 + 24, vA, x1, yB, z1, u0 + 24, vB, x1, yB, z0, u0 + 16, vB);
		if (top) {
			quad(entry, consumer, light, overlay, 0, -1, 0,
					x0, yTop, z0, u0 + 8, v0 + 8, x1, yTop, z0, u0 + 16, v0 + 8,
					x1, yTop, z1, u0 + 16, v0, x0, yTop, z1, u0 + 8, v0);
		}
		if (bottom) {
			quad(entry, consumer, light, overlay, 0, 1, 0,
					x0, yBottom, z0, u0 + 16, v0 + 8, x1, yBottom, z0, u0 + 24, v0 + 8,
					x1, yBottom, z1, u0 + 24, v0, x0, yBottom, z1, u0 + 16, v0);
		}
	}

	private static void quad(MatrixStack.Entry entry, VertexConsumer consumer, int light, int overlay,
			float nx, float ny, float nz, float... d) {
		Matrix4f matrix = entry.getPositionMatrix();
		for (int i = 0; i < 4; i++) {
			int o = i * 5;
			consumer.vertex(matrix, d[o] / 16.0f, d[o + 1] / 16.0f, d[o + 2] / 16.0f)
					.color(-1)
					.texture(d[o + 3] / TEX, d[o + 4] / TEX)
					.overlay(overlay)
					.light(light)
					.normal(entry, nx, ny, nz);
		}
	}
}
