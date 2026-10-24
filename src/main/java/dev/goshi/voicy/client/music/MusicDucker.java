package dev.goshi.voicy.client.music;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Util;

import java.util.Locale;

public final class MusicDucker {
	private static final long HOLD_MS = 700L;
	private static final long RELEASE_MS = 900L;
	private static final double HEARING_RANGE = 20.0;
	private static final float MIN_STRENGTH = 0.3f;

	private static final String[] IGNORED = {
			"step", "swim", "splash", "pickup", "flap", "wool", "rain", "wind", "ambient.cave", "armor.equip"
	};

	private static long lastMs;
	private static float strength;

	private MusicDucker() {
	}

	public static void onSound(SoundInstance sound) {
		try {
			float weight = weigh(sound);
			if (weight < MIN_STRENGTH) {
				return;
			}
			long now = Util.getMeasuringTimeMs();
			strength = Math.max(pressureAt(now), weight);
			lastMs = now;
		} catch (RuntimeException ignored) {
		}
	}

	public static float gamePressure() {
		return pressureAt(Util.getMeasuringTimeMs());
	}

	private static float pressureAt(long now) {
		long elapsed = now - lastMs;
		if (strength <= 0f || elapsed < 0) {
			return 0f;
		}
		if (elapsed <= HOLD_MS) {
			return strength;
		}
		long fade = elapsed - HOLD_MS;
		return fade >= RELEASE_MS ? 0f : strength * (1f - fade / (float) RELEASE_MS);
	}

	private static float weigh(SoundInstance sound) {
		SoundCategory category = sound.getCategory();
		if (category == SoundCategory.MASTER || category == SoundCategory.MUSIC
				|| category == SoundCategory.RECORDS || category == SoundCategory.AMBIENT) {
			return 0f;
		}
		String id = sound.getId().getPath().toLowerCase(Locale.ROOT);
		boolean wanted = id.startsWith("entity.") || id.startsWith("event.") || id.startsWith("item.totem")
				|| id.startsWith("item.trident.thunder") || id.contains("explode");
		if (!wanted) {
			return 0f;
		}
		for (String skip : IGNORED) {
			if (id.contains(skip)) {
				return 0f;
			}
		}

		float loudness = Math.min(1f, sound.getVolume());
		if (sound.isRelative() || sound.getAttenuationType() == SoundInstance.AttenuationType.NONE) {
			return loudness;
		}
		ClientPlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return 0f;
		}
		double dx = sound.getX() - player.getX();
		double dy = sound.getY() - player.getY();
		double dz = sound.getZ() - player.getZ();
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double range = HEARING_RANGE * Math.max(1.0, sound.getVolume());
		if (distance >= range) {
			return 0f;
		}
		return (float) (loudness * (1.0 - distance / range));
	}
}
