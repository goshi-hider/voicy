package dev.goshi.voicy.mixin;

import dev.goshi.voicy.VoicyClient;
import dev.goshi.voicy.client.AnimationStates;
import dev.goshi.voicy.client.ClientVoiceManager;
import dev.goshi.voicy.client.TalkingHeadAnimator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

	@Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
	private void voicy$talkingHead(LivingEntity entity, float limbAngle, float limbDistance, float animationProgress,
			float headYaw, float headPitch, CallbackInfo ci) {
		BipedEntityModel<?> model = (BipedEntityModel<?>) (Object) this;
		ModelPart head = model.head;
		ModelPart hat = model.hat;

		TalkingHeadAnimator.Pose pose = TalkingHeadAnimator.Pose.NONE;
		if (entity instanceof PlayerEntity player && player.getWorld().isClient()) {
			ClientVoiceManager voiceManager = VoicyClient.voiceManager();
			boolean talking = false;
			if (voiceManager != null) {
				MinecraftClient client = MinecraftClient.getInstance();
				boolean self = client.player != null && client.player.getUuid().equals(player.getUuid());
				talking = self ? voiceManager.isSelfTalking() : voiceManager.isTalking(player.getUuid());
			}
			pose = TalkingHeadAnimator.pose(player.getUuid(), talking, AnimationStates.of(player.getUuid()).style());
		}

		head.xScale = pose.scaleX();
		head.yScale = pose.scaleY();
		head.zScale = pose.scaleZ();
		head.pitch += pose.pitch();
		head.pivotY -= pose.lift();

		hat.xScale = head.xScale;
		hat.yScale = head.yScale;
		hat.zScale = head.zScale;
		hat.pitch = head.pitch;
		hat.pivotY = head.pivotY;

		if (pose.splitActive()) {
			head.visible = false;
			hat.visible = false;
		}
	}
}
