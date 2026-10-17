# Voicy

Proximity and spatial voice chat for Fabric 1.21 — Opus-encoded, OpenAL-spatialized,
and built to be hooked into from other mods.

## Features

- **Proximity voice** with distance falloff and optional realistic occlusion (raycast
  line-of-sight muffling through blocks).
- **Spatial 3D audio** — voice is positioned via OpenAL relative to the listener, so you
  can hear which direction someone is talking from.
- **Push-to-talk, voice activation, or always-on** capture modes, with a proper
  hysteresis gate (configurable sensitivity, release ratio, and hangover) so voice
  activation doesn't chop words or chatter on background noise.
- **Automatic gain control and noise gating** on the capture side for consistent,
  clean mic levels without manual tuning.
- **In-world speaker blocks** (`/speaker create|remove|show|list|test`) — fixed points
  that relay nearby voice out to listeners, independent of any player, with a short
  rolling capture buffer you can replay on demand via `/speaker test`.
- **Group and private voice channels**, alongside plain proximity, for team or
  whisper-style comms that ignore distance.
- **UDP fallback transport** for lower-latency audio when the server has it enabled,
  with automatic fallback to the standard Fabric networking channel if UDP isn't
  reachable.
- **Jitter buffering with packet-loss concealment** — reordering, late-packet
  handling, and Opus in-band FEC so brief network hiccups don't produce audible gaps.
- **Per-player and master volume controls**, plus an audio filter API for other mods to
  process voice in flight (radio effects, muffling, custom DSP).
- **A public API module** (`voicechat-api`) for other mods to mute/deafen players,
  manage channels, listen for talk events, and register audio filters. See the
  [GitHub wiki](../../wiki) for the full reference.

## Requirements

- Fabric Loader + Fabric API
- Minecraft 1.21
- A working microphone and audio output device (uses the Java Sound API for capture,
  OpenAL/LWJGL — already bundled with Minecraft — for playback)

## Configuration

Settings live in `config/voicy.json` and are also editable in-game via the voice
settings screen (keybind-configurable). Covers proximity radius and falloff, sample
rate, frame duration, activation mode and threshold, UDP preference, occlusion,
per-device selection, max Opus bitrate, and jitter buffer size.

## For server admins

Voicy runs entirely server-authoritative — mute, deafen, and channel state are all
enforced server-side, so a modified client can't fake being unmuted or bypass a mute
issued against it. Speakers are a server-side, persistent world feature (survives
restarts) usable from commands without needing any client-side setup from players.

## For mod developers

Depend on the `voicechat-api` module to integrate: mute/deafen players, create and
manage voice channels, listen for talking/mute/deafen events, or register an audio
filter that processes decoded PCM before playback. Full reference on the
[GitHub wiki](../../wiki), starting with [Getting Started](../../wiki/Getting-Started).

## Source & Issues

Source, issue tracker, and the API wiki are on GitHub — see the links on this page.
