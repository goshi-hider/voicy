# VoiceChatAPI

`net.voicechat.api.VoiceChatAPI`

The root entry point. All sub-managers below hang off it.

```java
public interface VoiceChatAPI {
    static VoiceChatAPI getInstance();          // nullable — null if Voicy isn't loaded yet

    MuteManager getMuteManager();
    DeafenManager getDeafenManager();
    VolumeManager getVolumeManager();
    ChannelManager getChannelManager();
    GroupManager getGroupManager();
    VoiceChatEvents getEvents();

    VoiceState getVoiceState(UUID playerId);
    void registerModSecurityContext(Identifier modId);

    record VoiceState(boolean muted, boolean deafened, Identifier activeChannel) {}
}
```

## `getInstance()`

Static accessor backed by `VoiceChatAPIHolder`. Returns `null` before Voicy has
initialized — always null-check, never assume load order.

Currently the instance is only ever constructed on the client (`VoicyClient`'s client
entrypoint). On a dedicated server `getInstance()` returns `null` permanently, even
after the world is fully up — there is no server-side construction path yet. If your
mod runs server-side logic against this API (team assignment, moderation tooling),
plan around that: it works from client-side mod code against the locally running
game (including the server half of an integrated/singleplayer world, since that runs
in the same client process), but not from a headless dedicated server. `GroupManager`
in particular is often wanted for server automation — see
[GroupManager](GroupManager.md#availability) before relying on it there.

## `getVoiceState(UUID playerId)`

Snapshot of a player's current mute/deafen/channel state in one call, instead of
querying `MuteManager`, `DeafenManager`, and `ChannelManager` separately. `activeChannel`
is `null` when the player is on plain proximity voice (no channel joined).

## `registerModSecurityContext(Identifier modId)`

See [Getting Started](Getting-Started.md#registering-a-security-context). Call once
per mod, early, before issuing mute/deafen/filter calls.

## `VoiceState`

Immutable record returned by `getVoiceState`. Also the shape published to
`VoicyVoiceChatEvents` on mute/deafen changes (see implementation-side
[Transport & Packets](Transport.md) for how it's synced client ↔ server).
