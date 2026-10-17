package dev.goshi.voicy.api;

import net.minecraft.util.Identifier;
import net.voicechat.api.events.VoiceChatEvents;
import net.voicechat.api.mute.MuteReason;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class VoicyVoiceChatEvents implements VoiceChatEvents {
	private final CopyOnWriteArrayList<Consumer<PlayerMuteEvent>> muted = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<PlayerUnmuteEvent>> unmuted = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<PlayerDeafenEvent>> deafened = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<PlayerUndeafenEvent>> undeafened = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<PlayerTalkingEvent>> talking = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Consumer<PlayerTalkingEvent>> stoppedTalking = new CopyOnWriteArrayList<>();

	@Override
	public void onPlayerMuted(Consumer<PlayerMuteEvent> listener) {
		muted.add(listener);
	}

	@Override
	public void onPlayerUnmuted(Consumer<PlayerUnmuteEvent> listener) {
		unmuted.add(listener);
	}

	@Override
	public void onPlayerDeafened(Consumer<PlayerDeafenEvent> listener) {
		deafened.add(listener);
	}

	@Override
	public void onPlayerUndeafened(Consumer<PlayerUndeafenEvent> listener) {
		undeafened.add(listener);
	}

	@Override
	public void onPlayerStartTalking(Consumer<PlayerTalkingEvent> listener) {
		talking.add(listener);
	}

	@Override
	public void onPlayerStopTalking(Consumer<PlayerTalkingEvent> listener) {
		stoppedTalking.add(listener);
	}

	@Override
	public void unregisterListener(Consumer<?> listener) {
		muted.remove(listener);
		unmuted.remove(listener);
		deafened.remove(listener);
		undeafened.remove(listener);
		talking.remove(listener);
		stoppedTalking.remove(listener);
	}

	void publishState(UUID playerId, boolean wasMuted, boolean mutedNow, boolean wasDeafened, boolean deafenedNow) {
		Identifier source = Identifier.of("voicy", "server");
		if (wasMuted != mutedNow) {
			if (mutedNow) {
				publish(muted, new PlayerMuteEvent(playerId, MuteReason.API, source));
			} else {
				publish(unmuted, new PlayerUnmuteEvent(playerId, source));
			}
		}
		if (wasDeafened != deafenedNow) {
			if (deafenedNow) {
				publish(deafened, new PlayerDeafenEvent(playerId, source));
			} else {
				publish(undeafened, new PlayerUndeafenEvent(playerId, source));
			}
		}
	}

	void publishTalking(UUID playerId, boolean start) {
		PlayerTalkingEvent event = new PlayerTalkingEvent(playerId);
		publish(start ? talking : stoppedTalking, event);
	}

	private static <T> void publish(Iterable<Consumer<T>> listeners, T event) {
		for (Consumer<T> listener : listeners) {
			try {
				listener.accept(event);
			} catch (RuntimeException e) {
				dev.goshi.voicy.Voicy.LOGGER.warn("[Voicy] Event listener threw: {}", e.toString());
			}
		}
	}
}