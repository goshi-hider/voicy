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

These are fire-and-forget: the call is one-way client→server networking with no
acknowledgement. The return value only tells you whether the request packet could be
sent (e.g. `false` if you're not connected to a server) — it does **not** tell you
whether the server actually applied the mute. A cross-player request can still be
silently rejected server-side (see [Getting Started](Getting-Started.md#registering-a-security-context))
after `requestMute` has already returned `true`. There is no priority system between
mute sources — whichever request lands last wins, regardless of `MuteReason` or who
sent it.

```java
Identifier me = Identifier.of("antigrief", "voice_control");
api.getMuteManager().requestMute(offenderUuid, me, MuteReason.MOD_EFFECT);
```

## `MuteReason`

```java
public enum MuteReason { ADMIN, MOD_EFFECT, PLAYER_SELF, API, SECURITY_VIOLATION }
```

The interface accepts a reason, but in the current build it isn't transmitted over
the network or recorded anywhere — passing any value is equivalent to any other. If
you're listening via [`VoiceChatEvents`](VoiceChatEvents.md), `PlayerMuteEvent.reason()`
is always `API` right now, no matter what reason the mute was requested with or
whether it came from a player self-muting. Pick whichever value reads clearest in
your own call sites; don't build branching logic around it yet.

## `isMuted` / `getMuteSource`

Read-only, safe to poll every tick. `getMuteSource` always returns empty in the
current build — source attribution isn't wired up yet — so don't use it to decide
whether it's safe to unmute a player; an unrelated mod's `requestUnmute` will clear
your mute without any way to detect that in advance.

## `setSelfMuted` / `isSelfMuted`

Client-side only — toggles the local player's own mic, equivalent to the in-game mute
key. Calling this server-side has no effect; it's not routed over the network.
