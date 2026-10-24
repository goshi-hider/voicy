package dev.goshi.voicy.mixin;

import dev.goshi.voicy.client.music.MusicDucker;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
	@Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), require = 0)
	private void voicy$onPlay(SoundInstance sound, CallbackInfo ci) {
		try {
			MusicDucker.onSound(sound);
		} catch (Throwable ignored) {
		}
	}
}
