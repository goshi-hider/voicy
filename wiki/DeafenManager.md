# DeafenManager

`net.voicechat.api.deafen.DeafenManager` — `VoiceChatAPI.getDeafenManager()`

Mirror of [MuteManager](MuteManager.md) for the receive side: a deafened player stops
*hearing* everyone (the router drops incoming frames for them), independent of whether
they themselves are muted.

```java
public interface DeafenManager {
    boolean requestDeafen(UUID target, Identifier sourceMod);
    boolean requestUndeafen(UUID target, Identifier sourceMod);
    boolean isDeafened(UUID playerId);
    Optional<Identifier> getDeafenSource(UUID playerId);

    void setSelfDeafened(boolean deafened);  // client-side: local player only
    boolean isSelfDeafened();
}
```

There's no `DeafenReason` enum. Same caveats as [MuteManager](MuteManager.md#requestmute--requestunmute)
apply here: `requestDeafen`/`requestUndeafen` are fire-and-forget (the return value
reflects whether the packet could be sent, not whether the server applied it),
`sourceMod` isn't tracked anywhere for conflict resolution, and `getDeafenSource`
always returns empty — an unrelated mod's `requestUndeafen` will clear a deafen your
mod imposed, with no attribution to tell you it happened.

Usage is identical in shape to `MuteManager`:

```java
api.getDeafenManager().requestDeafen(playerUuid, Identifier.of("spectator", "voice"));
```

`setSelfDeafened` / `isSelfDeafened` are client-side only, same caveat as
`setSelfMuted` — no network effect if called server-side.
