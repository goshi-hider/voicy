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

## Registering a security context

Every mutating call (mute, deafen, filter registration) takes an `Identifier` naming
the calling mod. Register it once so Voicy can attribute and later revoke your mod's
actions cleanly:

```java
VoiceChatAPI.getInstance().registerModSecurityContext(Identifier.of("yourmod", "voice_integration"));
```

Calls made without registering still work, but won't be grouped under your mod's
identity in `/speaker` diagnostics or mute-source lookups.

## Where to go next

- Muting/deafening players programmatically → [MuteManager](MuteManager.md) / [DeafenManager](DeafenManager.md)
- Per-player volume sliders or custom DSP → [VolumeManager](VolumeManager.md)
- Private or group channels instead of proximity → [ChannelManager](ChannelManager.md)
- Reacting to who's talking → [VoiceChatEvents](VoiceChatEvents.md)
