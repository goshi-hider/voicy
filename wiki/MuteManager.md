# MuteManager

`net.voicechat.api.mute.MuteManager` — `VoiceChatAPI.getMuteManager()`

Server-authoritative mute control. A mute set via this API blocks the target's voice
frames at the router — they stop transmitting to everyone, not just to the caller.

```java
public interface MuteManager {
    boolean requestMute(UUID target, Identifier sourceMod, MuteReason reason);
    boolean requestUnmute(UUID target, Identifier sourceMod);
    boolean isMuted(UUID playerId);
    Optional<Identifier> getMuteSource(UUID playerId);

    void setSelfMuted(boolean muted);   // client-side: local player only
    boolean isSelfMuted();
}
```

## `requestMute` / `requestUnmute`

Returns `false` if the request was rejected (e.g. target already muted by a
higher-priority source — see `MuteReason` ordering below). `sourceMod` is recorded so
`getMuteSource` can later report who's responsible, and so another mod's
`requestUnmute` targeting the same player doesn't silently override an admin mute.

```java
Identifier me = Identifier.of("antigrief", "voice_control");
api.getMuteManager().requestMute(offenderUuid, me, MuteReason.MOD_EFFECT);
```

## `MuteReason`

```java
public enum MuteReason { ADMIN, MOD_EFFECT, PLAYER_SELF, API, SECURITY_VIOLATION }
```

Use `ADMIN` for staff-issued mutes, `MOD_EFFECT` for gameplay effects (traps, items,
minigame states), `API` for generic third-party integrations, and
`SECURITY_VIOLATION` for mutes issued by Voicy's own anti-spoof/anti-abuse layer —
treat that one as informational, Voicy manages its own lifecycle for it.

## `isMuted` / `getMuteSource`

Read-only, safe to poll every tick. `getMuteSource` is empty when the player isn't
muted, or when muted via `setSelfMuted` (self-mutes have no external source).

## `setSelfMuted` / `isSelfMuted`

Client-side only — toggles the local player's own mic, equivalent to the in-game mute
key. Calling this server-side has no effect; it's not routed over the network.
