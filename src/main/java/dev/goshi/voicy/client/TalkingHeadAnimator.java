package dev.goshi.voicy.client;

import dev.goshi.voicy.config.AnimationStyle;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class TalkingHeadAnimator {
	private static final Map<UUID, State> STATES = new HashMap<>();
	private static long lastPurgeMs;

	private TalkingHeadAnimator() {
	}

	public record Pose(float scaleX, float scaleY, float scaleZ, float pitch, float lift, float open,
			boolean active, AnimationStyle style) {
		public static final Pose NONE = new Pose(1f, 1f, 1f, 0f, 0f, 0f, false, AnimationStyle.OFF);

		public boolean splitActive() {
			return active && style.isSplit();
		}
	}

	public static Pose pose(UUID id, boolean talking, AnimationStyle style) {
		long now = Util.getMeasuringTimeMs();
		purge(now);
		State state = STATES.computeIfAbsent(id, key -> new State(now));
		float dt = Math.min(0.1f, (now - state.lastMs) / 1000f);
		state.lastMs = now;
		float target = talking ? 1f : 0f;
		float rate = talking ? 12f : 5f;
		state.amount = state.amount < target
				? Math.min(target, state.amount + rate * dt)
				: Math.max(target, state.amount - rate * dt);

		if (style == AnimationStyle.OFF || state.amount <= 0.01f) {
			state.pose = Pose.NONE;
			return state.pose;
		}

		int phase = id.hashCode() & 15;
		float t = now / 1000f;
		float wave = 0.5f + 0.5f * MathHelper.sin(t * 13f + phase * 1.7f);
		float flutter = 0.65f + 0.35f * MathHelper.sin(t * 5.1f + phase * 0.9f);
		float w = wave * flutter * state.amount;

		state.pose = switch (style) {
			case SQUISH -> new Pose(1f + 0.12f * w, 1f - 0.22f * w, 1f + 0.12f * w, 0f, 0f, 0f, true, style);
			case SCALE_HEIGHT -> new Pose(1f, 1f + 0.28f * w, 1f, 0f, 0f, 0f, true, style);
			case SCALE -> new Pose(1f + 0.2f * w, 1f + 0.2f * w, 1f + 0.2f * w, 0f, 0f, 0f, true, style);
			case NOD -> {
				float pitch = (0.5f - 0.5f * MathHelper.cos(t * 9f + phase)) * 0.30f * state.amount;
				yield new Pose(1f, 1f, 1f, pitch, 2.2f * MathHelper.sin(pitch), 0f, true, style);
			}
			case SPLIT_HINGE, SPLIT_VERTICAL -> new Pose(1f, 1f, 1f, 0f, 0f, w, true, style);
			default -> Pose.NONE;
		};
		return state.pose;
	}

	public static Pose last(UUID id) {
		State state = STATES.get(id);
		return state == null ? null : state.pose;
	}

	private static void purge(long now) {
		if (now - lastPurgeMs < 10_000L) {
			return;
		}
		lastPurgeMs = now;
		Iterator<State> iterator = STATES.values().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().lastMs > 10_000L) {
				iterator.remove();
			}
		}
	}

	private static final class State {
		float amount;
		long lastMs;
		Pose pose = Pose.NONE;

		State(long now) {
			this.lastMs = now;
		}
	}
}
