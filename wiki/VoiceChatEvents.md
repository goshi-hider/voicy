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
    void onPlayerJoinedGroup(Consumer<PlayerJoinedGroupEvent> listener);
    void onPlayerLeftGroup(Consumer<PlayerLeftGroupEvent> listener);

    void unregisterListener(Consumer<?> listener);

    record PlayerMuteEvent(UUID playerId, MuteReason reason, Identifier sourceMod) {}
    record PlayerUnmuteEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerDeafenEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerUndeafenEvent(UUID playerId, Identifier sourceMod) {}
    record PlayerTalkingEvent(UUID playerId) {}
    record PlayerJoinedGroupEvent(UUID playerId, UUID groupId, String groupName) {}
    record PlayerLeftGroupEvent(UUID playerId, UUID groupId, String groupName) {}
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
`DeafenManager`. `reason` on `PlayerMuteEvent` is always populated, but in the current
build it's always `MuteReason.API` — the reason a mute was actually requested with
isn't threaded through to this event yet (see [MuteManager](MuteManager.md#mutereason)).
Don't branch on it. There's no equivalent reason enum for deafen events, matching
`DeafenManager`'s own shape.

## Group events

`onPlayerJoinedGroup` / `onPlayerLeftGroup` fire whenever a tracked player's group
membership changes — joining, leaving, being kicked, a group disbanding under them,
or a direct swap from one group to another (one join, one leave, no third "switched"
event). They're driven off the group directory the server pushes down, which syncs in
full on every relevant change and on login — so right after joining a world, expect a
burst of join events covering every pre-existing group member you can see, not just
genuinely new joins from that point on. `groupName` is a snapshot at the moment of the
event; it won't update retroactively if the group is later renamed (groups can't
currently be renamed after creation, so this is moot for now, but don't rely on it
being live). See [GroupManager](GroupManager.md) for querying current group state
directly instead of tracking it from these events.
