# Speakers

Speakers are fixed world points that participate in voice routing without being a
player — placed with `/speaker create`, they relay any voice that reaches them out to
nearby listeners, and can replay a short buffer of recently captured audio on demand.

Managed server-side by `SpeakerManager` (`dev.goshi.voicy.SpeakerManager`).

## Commands

```
/speaker create <x> <y> <z>     coordinates or ~ ~ ~ relative to the caller
/speaker remove <speaker_id>
/speaker show <speaker_id>      spawns a particle outline at the speaker's position
/speaker list
/speaker test                   replays the caller's last 5s of voice through nearby speakers
/speaker group join <group_name> [speaker_id]   see "Group-assigned speakers" below
/speaker group leave [speaker_id]
/speaker group list
```

`speaker_id` is optional on the `group` subcommands — omit it to target whichever
speaker is nearest the command's source.

Speaker state (position, dimension) persists in the world's `PersistentState` under
`voicy_speakers` — survives server restarts, synced to each client on join and on any
create/remove via `SpeakerSyncPayload`.

## Identity: synthetic UUIDs

Speakers don't have a real player UUID, so routing treats them as "virtual players"
using a synthetic UUID derived from a fixed magic constant plus the integer speaker ID:

```java
UUID speakerUuid = SpeakerManager.speakerUuid(speakerId);       // encode
int speakerId   = SpeakerManager.decodeSpeakerId(someUuid);     // decode, -1 if not a speaker
boolean isSpeaker = SpeakerManager.isSpeakerUuid(someUuid);
```

This lets the entire client playback pipeline (`JitterBuffer`, `OpusDecoder` map,
`SpatialAudioSource`, talking-state tracking) treat a speaker exactly like a remote
player, keyed by its synthetic UUID, with no special-casing needed in most of
`ClientVoiceManager` — the one branch that does special-case it is position lookup
(speakers use `SpeakerSyncPayload`-provided coordinates instead of an entity's live
position, since they have no entity).

## Live relay

Any `VoiceFramePayload` the router accepts from a player is relayed to every speaker
within that frame's radius, in the same world, as part of normal `VoiceRouter.route()`
handling — no separate opt-in needed. Nearby listeners of the speaker then receive it
as if the speaker itself were talking. This is the behavior for a speaker that isn't
assigned to a [group](GroupManager.md) and a player who isn't talking to their group
only — see the next section for how group assignment changes it.

## Group-assigned speakers

`/speaker group join <group_name> [speaker_id]` puts a speaker into one of the groups
from [`GroupManager`](GroupManager.md). This changes relay behavior, not just
cosmetics: a speaker assigned to a group **only** repeats voice from members talking
to that exact group with talk-to-group-only enabled, at any distance within the
speaker's radius — it stops repeating ordinary proximity voice entirely, from anyone,
group member or not. Symmetrically, a speaker with no group assignment never repeats
a player who has talk-to-group-only on, even if that player is standing right next to
it. `/speaker group leave [speaker_id]` reverts a speaker to normal (repeats everyone
in range, group-restricted or not). `/speaker group list` shows current assignments.
Use this to test a group's own speaker setup (an in-world "radio" exclusive to one
team, say) in isolation from general-purpose relays.

## `/speaker test` replay

`VoiceRouter` keeps a rolling 5-second buffer (`recentFrames`) of every frame a player
has sent. `/speaker test` snapshots whatever's still within that 5-second window into a
per-player replay queue, then `VoiceRouter.tick()` drains it and re-relays the frames to
nearby speakers over subsequent server ticks — paced to match the original recording's
frame duration (`VoicyConfig.frameDurationMs()`, 20ms by default) rather than the fixed
50ms server tick, so playback speed matches how it was spoken instead of arriving slower
than real-time and getting dropped as "late" by the receiving client's jitter buffer.

If `/speaker test` reports 0 frames replayed, nothing was captured in the trailing 5
seconds — the player needs to actually speak (and be within range of at least one
speaker, so a frame gets recorded at all) immediately before running the command.
