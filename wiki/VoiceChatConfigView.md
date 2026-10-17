# VoiceChatConfigView

`net.voicechat.api.config.VoiceChatConfigView`

Read-only view of Voicy's live configuration. `VoicyConfig` (the concrete
implementation) implements this interface directly, so anywhere Voicy hands you a
config reference, you're looking at the real, currently-active settings — not a copy.

```java
public interface VoiceChatConfigView {
    double proximityRadius();     // blocks; default 48.0
    double proximityFalloff();    // exponent for distance attenuation; default 1.0
    int sampleRate();             // Hz; default 48000
    int frameDurationMs();        // ms per Opus frame; default 20
    ActivationMode activationMode();
    float activationThreshold();  // 0.0–1.0 normalized level; default 0.12

    enum ActivationMode { VOICE_ACTIVATION, PUSH_TO_TALK, ALWAYS_ON }
}
```

This interface doesn't expose setters — it's intentionally narrower than `VoicyConfig`
itself, so third-party code can read the live config to adapt behavior (e.g. an
`AudioFilter` scaling its effect strength by `proximityRadius()`) without being able to
mutate player-facing audio settings out from under them.

`frameDurationMs()` matters if you're doing anything frame-synchronous in an
`AudioFilter` or reasoning about network cadence — it's the duration each decoded PCM
chunk represents, and (as of this build) also the pacing unit `VoiceRouter` uses when
draining the speaker replay queue — see [Transport & Packets](Transport.md).
