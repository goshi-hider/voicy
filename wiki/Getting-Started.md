# Getting Started

## Depending on the API

Add the `voicechat-api` module as a compile-time dependency. It ships inside the Voicy jar
and has no runtime dependency on the rest of the mod — your mod only needs the interfaces.

```gradle
repositories {
    maven { url "https://your-maven-or-modrinth-maven" }
}

dependencies {
    modImplementation "dev.goshi:voicy-api:${voicy_version}"
}
```

Declare Voicy as a dependency in `fabric.mod.json` if your mod requires it to function,
or as an optional dependency if integration is best-effort:

```json
"depends": {
  "voicy": ">=1.0.0"
}
```

## Getting the API instance

```java
VoiceChatAPI api = VoiceChatAPI.getInstance();
if (api == null) {
    // Voicy isn't loaded, or hasn't finished initializing yet.
    return;
}
```

`getInstance()` returns `null` until Voicy has registered itself. Don't cache the
reference at class-init time — fetch it lazily at the point of use, or register your
integration from `ModInitializer.onInitialize()` after checking `FabricLoader.isModLoaded("voicy")`.

The instance is currently only ever created client-side. If your `ModInitializer.onInitialize()`
runs on a dedicated server, `getInstance()` will stay `null` there for the lifetime of
the server — this isn't a load-order race, there is no server-side instance to wait for
yet. Plan accordingly, especially for `GroupManager`, which is the piece most likely to
be wanted from server-side automation.

## Registering a security context

Every mutating call (mute, deafen, filter registration) takes an `Identifier` naming
the calling mod. Register it once so Voicy can attribute and later revoke your mod's
actions cleanly:

```java
VoiceChatAPI.getInstance().registerModSecurityContext(Identifier.of("yourmod", "voice_integration"));
```

Calls that target the caller's own player (self-mute, self-deafen) work without
registering. Calls that target *another* player are rejected server-side unless the
caller is a server operator (permission level 2) **and** `sourceMod` is both
registered and matches a currently loaded mod's namespace — an unregistered or
unpermitted cross-player call is silently dropped and logged server-side as a
possible exploit attempt, not degraded-but-functional. There's currently no
diagnostics view or mute-source lookup that surfaces registered mods either —
`getMuteSource`/`getDeafenSource` (see [MuteManager](MuteManager.md)) always return
empty regardless of registration. Registering is still worth doing: it's the only
thing that makes cross-player calls work at all.

## Where to go next

- Muting/deafening players programmatically → [MuteManager](MuteManager.md) / [DeafenManager](DeafenManager.md)
- Per-player volume sliders or custom DSP → [VolumeManager](VolumeManager.md)
- Private or group channels instead of proximity → [ChannelManager](ChannelManager.md)
- Player-named groups with invites, colors and talk-to-group-only → [GroupManager](GroupManager.md)
- Reacting to who's talking → [VoiceChatEvents](VoiceChatEvents.md)
