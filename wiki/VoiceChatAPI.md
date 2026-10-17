# VoiceChatAPI

`net.voicechat.api.VoiceChatAPI`

The root entry point. One instance exists per running game (client or server side,
wherever Voicy is loaded); all sub-managers below hang off it.

```java
public interface VoiceChatAPI {
    static VoiceChatAPI getInstance();          // nullable — null if Voicy isn't loaded yet

    MuteManager getMuteManager();
    DeafenManager getDeafenManager();
    VolumeManager getVolumeManager();
    ChannelManager getChannelManager();
    VoiceChatEvents getEvents();

    VoiceState getVoiceState(UUID playerId);
    void registerModSecurityContext(Identifier modId);

    record VoiceState(boolean muted, boolean deafened, Identifier activeChannel) {}
}
```

## `getInstance()`

Static accessor backed by `VoiceChatAPIHolder`. Returns `null` before Voicy has
initialized — always null-check, never assume load order.

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
