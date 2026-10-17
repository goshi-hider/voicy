# VolumeManager

`net.voicechat.api.audio.VolumeManager` — `VoiceChatAPI.getVolumeManager()`

Client-side playback volume control and a PCM filter pipeline, run once per decoded
frame before it reaches the spatial audio engine.

```java
public interface VolumeManager {
    float getPlayerVolume(UUID playerId);
    void setPlayerVolume(UUID playerId, float volume);

    float getMasterVolume();
    void setMasterVolume(float volume);

    float getEffectiveVolume(UUID playerId);   // player volume x master volume, clamped

    short[] processAudio(UUID speakerId, short[] pcm, int sampleRate);

    AudioFilterHandle registerFilter(Identifier filterId, AudioFilter filter);
    void unregisterFilter(AudioFilterHandle handle);

    interface AudioFilter {
        short[] process(short[] pcm, int sampleRate, UUID speakerId);
    }

    interface AudioFilterHandle {
        Identifier filterId();
    }
}
```

## Volume

`getPlayerVolume` / `setPlayerVolume` are per-player multipliers (persisted in config,
see `VoicyConfig.playerVolume`). `getMasterVolume` / `setMasterVolume` scale everything.
`getEffectiveVolume` is what playback actually applies — multiply the two yourself only
if you need the raw components.

## `processAudio` and audio filters

`processAudio` is called by the client playback loop once per decoded PCM frame, for
every active speaker, right before upload to OpenAL (`ClientVoiceManager.tick()` →
`processPlaybackAudio`). It runs every registered `AudioFilter` in registration order
and returns the result — this is the hook point for mods that want to alter voice in
flight: pitch effects, radio-static filters inside a helmet item, muffling underwater,
spatial reverb per-room, etc.

```java
Identifier filterId = Identifier.of("mymod", "radio_crackle");
AudioFilterHandle handle = api.getVolumeManager().registerFilter(filterId, (pcm, sampleRate, speakerId) -> {
    // mutate or replace pcm; return a same-length short[] (or a shorter one — the
    // caller just forwards whatever you return)
    return pcm;
});

// later
api.getVolumeManager().unregisterFilter(handle);
```

Filters run on the client render/network thread inside the per-tick playback loop —
keep them allocation-light and branch-cheap; a filter that blocks or is slow will stall
every speaker's audio for that tick, not just the one it's processing.

`sampleRate` is always `VoicyConfig.sampleRate()` (48000 by default) — passed through
rather than hardcoded so filters stay correct if that config value changes.
