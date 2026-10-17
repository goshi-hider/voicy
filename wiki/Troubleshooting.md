# Troubleshooting

## `NoSuchMethodError` on `VoiceState.<init>` or `ChannelManager.getActiveChannel`

```
java.lang.NoSuchMethodError: 'void net.voicechat.api.VoiceChatAPI$VoiceState.<init>(...)'
java.lang.NoSuchMethodError: 'net.minecraft.util.Identifier net.voicechat.api.channel.ChannelManager.getActiveChannel(java.util.UUID)'
```

Not a code bug — this is a stale Loom/Gradle remap cache. The `voicechat-api` subproject
got rebuilt with a changed method signature, but `.gradle/loom-cache/remapped_mods/`
still has the old remapped jar cached, so the runtime classpath and the source you're
reading disagree with each other.

Fix:

```
./gradlew clean
./gradlew build
```

If it persists, delete `.gradle/` and `build/` (and `voicechat-api/build/`) entirely and
rebuild — Loom will re-remap from scratch.

## Choppy / clipped voice capture, words cut off at the start

Fixed in this build — `ClientVoiceManager.captureLoop()` used to gate encoding on a
slow-smoothed meter value before the real voice-activation check ran, dropping the
first several frames of every utterance. If you're seeing this on an older build,
update; there's no config workaround for it.

## `/speaker test` plays nothing, or only a stutter

Fixed in this build — `VoiceRouter.tick()` used to drain the replay queue at a fixed
1-frame-per-server-tick rate (20fps) regardless of how the audio was actually encoded
(50fps at the default 20ms frame duration), so replayed frames arrived slower than the
client's jitter buffer would tolerate and got dropped as stale almost immediately.
Replay now paces itself against `VoicyConfig.frameDurationMs()` instead of the fixed
tick rate.

If you still get "No voice frames were captured in the last 5 seconds": you need to be
within proximity/channel range of at least one speaker *while* you're speaking — frames
are only ever recorded if the normal routing path actually reaches a listener check
(see `VoiceRouter.recordRecentFrame`), not just because a speaker exists somewhere on
the map.

## No input devices listed / microphone won't open

`MicrophoneCapture.listInputDevices()` filters mixers by whether they support a
`TargetDataLine` at 48kHz/16-bit/mono. A device that only exposes other formats (e.g.
44.1kHz-only hardware) won't appear. Try changing `sampleRate` in `voicy.json` to match
your device, or use a different input device if available.

## No sound from other players at all

Check, in order: `isSelfDeafened()` on your own client, whether the transport handshake
actually completed (`[Voicy] Voice transport negotiated: ...` in the log), and whether
you're in proximity range or sharing a channel with the speaker
(`VoiceSecurityService.shareChannel`).
