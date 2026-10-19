package dev.goshi.voicy.mixin;

import dev.goshi.voicy.group.GroupNameColors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Colors the name tag above a player's head with the color they picked in their voice group. Only touches
 * the client-side copy of the player: server-side display names (chat, commands) stay untouched.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void voicy$colorGroupNameTag(CallbackInfoReturnable<Text> cir) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient()) {
			return;
		}
		Formatting color = GroupNameColors.get(self.getUuid());
		if (color != null) {
			cir.setReturnValue(cir.getReturnValue().copy().formatted(color));
		}
	}
}
