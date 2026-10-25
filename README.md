# Voicy

Fabric 1.21.x proximity voice chat with spatial speaker relays and a small third-party API.

## Build

Requires Java 21.

```powershell
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat :runClient
```

## API

Third-party Fabric mods can access Voicy through `VoiceChatAPI.getInstance()` after initialization. The API exposes authoritative mute/deafen requests, voice-state snapshots, player/master volume, decoded-PCM filters, and channels. Call `registerModSecurityContext(Identifier.of("your_mod", "main"))` during initialization before issuing commands attributed to your mod.

`VoiceChatAPI.getVoiceState(UUID)` returns an immutable snapshot. `getVolumeManager().registerFilter(...)` installs a client-side PCM filter; return the input array or a processed mono PCM array. Filter exceptions are isolated and do not stop voice playback.

## Audio Reliability

Voicy uses 48 kHz mono PCM in 20 ms frames, stateful Opus encoders/decoders per stream, sequence-ordered jitter buffering, bounded packet-loss concealment, and streamed OpenAL playback. Run `gradlew.bat test` after changing the capture, codec, packet, or playback path.

## License

See [LICENSE](LICENSE).
