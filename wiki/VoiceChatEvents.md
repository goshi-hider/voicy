# VoiceChatEvents

`net.voicechat.api.events.VoiceChatEvents` — `VoiceChatAPI.getEvents()`

Listener registration for mute/deafen/talking state changes.

```java
public interface VoiceChatEvents {
    void onPlayerMuted(Consumer<PlayerMuteEvent> listener);
    void onPlayerUnmuted(Consumer<PlayerUnmuteEvent> listener);
    void onPlayerDeafened(Consumer<PlayerDeafenEvent> listener);
    void onPlayerUndeafened(Consumer<PlayerUndeafenEvent> listener);
    void onPlayerStartTalking(Consumer<PlayerTalkingEvent> listener);
    void onPlayerStopTalking(Consumer<PlayerTalkingEvent> listener);

    void unregisterListener(Consumer<?> listener);

    record PlayerMuteEvent(UUID playerId, MuteReason reason, Identifier sourceMod) {}
    record PlayerUnmuteEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerDeafenEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerUndeafenEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerTalkingEvent(UUID playerId) {}
}
```

## Registering

```java
api.getEvents().onPlayerStartTalking(event -> {
    highlightNameTag(event.playerId());
});
```

Keep a reference to the lambda/method-reference if you intend to unregister it later —
`unregisterListener` compares by reference (`Consumer` identity), not by event type, so
you must pass the exact same instance you registered.

## Talking events

`onPlayerStartTalking` / `onPlayerStopTalking` fire from the client-side talking-state
tracker (`ClientVoiceManager` — speakers and real players alike are eligible; speaker
UUIDs are synthetic, see [Speakers](Speakers.md)). Stop-talking fires after roughly
400ms of silence (`TALKING_TIMEOUT_NANOS`), not immediately on the last frame — this
debounce avoids start/stop flicker between words.

## Mute/deafen events

Fire on both self-initiated changes and changes driven through `MuteManager` /
`DeafenManager`. `reason` on `PlayerMuteEvent` is always populated (see `MuteReason`);
there's no equivalent reason enum for deafen events, matching `DeafenManager`'s own
shape.
