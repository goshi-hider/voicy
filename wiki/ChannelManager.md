# ChannelManager

`net.voicechat.api.channel.ChannelManager` — `VoiceChatAPI.getChannelManager()`

Server-side. A channel is an alternative routing mode to plain proximity voice — join
one and the router (`VoiceRouter`) stops distance-checking you against listeners and
instead fans your voice out to the channel's member list.

```java
public interface ChannelManager {
    VoiceChannel createChannel(Identifier channelId, ChannelType type, ChannelSettings settings);
    Optional<VoiceChannel> getChannel(Identifier channelId);
    void deleteChannel(Identifier channelId);
    Collection<VoiceChannel> getChannels();

    boolean joinChannel(UUID playerId, Identifier channelId);
    boolean leaveChannel(UUID playerId, Identifier channelId);
    Identifier getActiveChannel(UUID playerId);   // nullable

    enum ChannelType { PROXIMITY, GROUP, PRIVATE }

    interface ChannelSettings {
        double maxDistance();
        boolean occluded();
        static ChannelSettings defaults();   // 48.0 blocks, occlusion on
    }

    interface VoiceChannel {
        Identifier id();
        ChannelType type();
        Collection<UUID> members();
        ChannelSettings settings();
    }
}
```

## Routing behavior by type

- **`PROXIMITY`** — routed exactly like no channel at all: distance-gated, no member
  list consulted. Mostly useful if you want a named/trackable proximity group without
  changing routing semantics.
- **`GROUP`** / **`PRIVATE`** — distance is ignored entirely. The router sends to every
  member of `VoiceChannel.members()` regardless of position, as long as they're not
  deafened and share a channel with the sender. Use `PRIVATE` by convention for
  1:1/small rosters and `GROUP` for larger team comms — the manager doesn't enforce a
  difference between them, it's a hint for your own UI/moderation.

## Joining and leaving

```java
Identifier teamChannel = Identifier.of("mymod", "red_team");
api.getChannelManager().createChannel(teamChannel, ChannelManager.ChannelType.GROUP,
        ChannelManager.ChannelSettings.defaults());

api.getChannelManager().joinChannel(playerUuid, teamChannel);
```

A player has at most one active channel at a time — `joinChannel` silently removes
them from whatever channel they were previously active in before adding them to the
new one. `getActiveChannel` returns `null` for a player on plain proximity voice.

`leaveChannel` always returns `true` and removes membership even if the player wasn't
the *active* channel holder (e.g. cleaning up stale membership after a channel switch)
— check `getActiveChannel` beforehand if you need to know whether they were actually
listening through it.

Deleting a channel (`deleteChannel`) force-clears every member's active-channel state
back to `null` (they drop to proximity voice, not silence).
