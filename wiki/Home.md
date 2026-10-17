# Voicy API Wiki

Voicy is a proximity + spatial voice chat mod for Fabric 1.21. This wiki covers the
`voicechat-api` module: the public surface other mods use to integrate with voice chat
(mute/deafen control, channels, volume/audio filters, and talk events).

- [Getting Started](Getting-Started.md) — depending on the API, getting the instance, lifecycle
- [VoiceChatAPI](VoiceChatAPI.md) — the root entry point and `VoiceState`
- [MuteManager](MuteManager.md) — muting players, mute sources/reasons
- [DeafenManager](DeafenManager.md) — deafening players
- [VolumeManager](VolumeManager.md) — per-player/master volume and audio filters
- [ChannelManager](ChannelManager.md) — proximity/group/private voice channels
- [VoiceChatEvents](VoiceChatEvents.md) — mute/deafen/talking listeners
- [VoiceChatConfigView](VoiceChatConfigView.md) — read-only live config
- [Speakers](Speakers.md) — in-world speaker blocks, `/speaker` commands, replay
- [Transport & Packets](Transport.md) — payload types, UDP fallback, handshake
- [Troubleshooting](Troubleshooting.md) — stale build cache crash, common pitfalls

Source packages:

```
net.voicechat.api                — public API module (voicechat-api)
dev.goshi.voicy                  — mod implementation (src)
```
