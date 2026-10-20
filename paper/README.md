# Voicy Paper

Lets a **plain Paper/Spigot server** run Voicy voice chat with **no Fabric Loader, no server-side
mod** - same model as Simple Voice Chat's Bukkit plugin. Players only need:

- The Voicy **Fabric** client mod + **Fabric API**, client-side.
- This plugin's jar, dropped into the server's `plugins/` folder. Nothing else.

## Why this works

Minecraft's custom-payload packet (what Fabric's networking API calls a "payload") and Bukkit's
plugin-messaging channel are the same vanilla protocol packet. Fabric registers channels via
`PayloadTypeRegistry`; this plugin registers the *same channel strings* via Bukkit's `Messenger`.
Neither side needs to know what loader the other side runs - the client mod has no idea it's
talking to a plugin instead of a Fabric server.

To make that work, every payload in `dev.goshi.voicy.paper.net.payload` is a from-scratch
re-implementation of the matching class in `dev.goshi.voicy.network.payload`, byte-for-byte
compatible with the Fabric `PacketCodecs` it was built from (see `VoicyByteBuf` - no Minecraft or
Netty classes needed, just a wire-format-accurate reader/writer). If you change a payload or its
channel ID in the Fabric mod, mirror the change here or the two sides will silently desync.

## What's ported vs. dropped

| Fabric mod piece | Status |
|---|---|
| `GroupManager` | Copied unchanged (pure Java, no Fabric types) |
| `PlayerVoiceState` | Copied unchanged |
| `GroupNetwork`, `VoiceSecurityService`, `VoiceRouter`, `SpeakerManager` | Ported: `ServerPlayerEntity`/`ServerWorld` -> Bukkit `Player`/`World`, `ServerPlayNetworking.send` -> `Player#sendPluginMessage` |
| `UdpVoiceServer` | Ported near-verbatim (already loader-agnostic) |
| Third-party `net.voicechat.api.channel.ChannelManager` extension point | **Dropped.** Only exists for *other* Fabric mods to hook into Voicy; the client itself never calls `JOIN_CHANNEL`/`LEAVE_CHANNEL` for its own proximity or group features, so nothing user-facing is lost. Proximity voice and groups have full parity. |
| Client-side occlusion raycasting, name-color rendering, HUD, mixins | N/A - those are rendering/client-only and never touch the server |

## Building

```
./gradlew :paper:build
```

Needs network access to `repo.papermc.io` (Paper API) - this sandbox didn't have that, so this
module hasn't been build-verified here; it's been hand-reviewed line by line against the Fabric
source for wire-format and call-signature correctness instead. Run the build yourself before
trusting it in production, and skim `VoicyPaperPlugin.onEnable()` first - it's the one place
everything gets wired together.

Output: `paper/build/libs/voicy-paper-<version>.jar`.

## Installing

1. Put `voicy-paper-<version>.jar` in the Paper server's `plugins/` folder. Restart.
2. Players install the Fabric mod + Fabric API as normal and just join. No server-side mod, no
   Fabric Loader on the server.
3. `config.yml` (generated under `plugins/VoicyPaper/`) controls proximity radius, Opus
   sample-rate/frame-duration (must match what the client encodes with), and whether a UDP
   fallback transport is also opened.

## Commands

Same tree as the Fabric server's `/speaker` command: `create`, `remove`, `show`, `list`, `test`,
and `group <join|leave|list>`. Permission nodes: `voicy.speaker` (default: op), `voicy.admin` for
targeting another player's mute/deafen state through the API channel (default: op).

## Folia

Not Folia-safe as written - `VoicyPaperPlugin` uses the global `BukkitScheduler`
(`runTaskTimer`/`runTask`) and touches `World`/`Player` state directly in the plugin-message
callback, which assumes a single main thread. Region-based scheduling would need the router's
per-player/per-speaker work split onto the right region scheduler.
