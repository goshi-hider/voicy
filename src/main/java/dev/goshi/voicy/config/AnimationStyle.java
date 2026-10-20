package dev.goshi.voicy.config;

import java.util.Locale;

public enum AnimationStyle {
	OFF,
	SQUISH,
	SCALE_HEIGHT,
	SCALE,
	SPLIT_HINGE,
	SPLIT_VERTICAL,
	NOD;

	public boolean isSplit() {
		return this == SPLIT_HINGE || this == SPLIT_VERTICAL;
	}

	public AnimationStyle next() {
		AnimationStyle[] all = values();
		return all[(ordinal() + 1) % all.length];
	}

	public static AnimationStyle byIndex(int index) {
		AnimationStyle[] all = values();
		return index >= 0 && index < all.length ? all[index] : OFF;
	}

	public String translationKey() {
		return "gui.voicy.animation_style." + name().toLowerCase(Locale.ROOT);
	}
}
