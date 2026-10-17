# Transport & Packets

All payloads are registered via `VoicyNetworking.registerPayloadTypes()` using Fabric's
`CustomPayload` networking. Voice audio can ride either the normal Fabric play channel
or a raw UDP fallback, negotiated per-session.

## Payloads

| Payload | Direction | Purpose |
|---|---|---|
| `HandshakePayload` | S→C | Negotiates transport mode, sample rate, frame duration, UDP host/port, session secret |
| `VoiceFramePayload` | C→S→C | One Opus-encoded audio frame: `speakerId`, `sequence`, `distanceRadius`, `opusData` |
| `SpeakerSyncPayload` | S→C | Speaker `id` + position + radii, sent on join and on any speaker create/remove |
| `SpeakerTestPayload` | — | Record shape for a client-provided test tone; not currently wired to a receiver on either side (reserved for a future direct-PCM test path — `/speaker test` instead replays captured `VoiceFramePayload`s server-side, see [Speakers](Speakers.md)) |
| `StateSyncPayload` | S→C | Broadcasts a player's `muted`/`deafened` state |
| `ApiCommandPayload` | C→S | Client-issued mute/deafen/channel commands (`ApiCommandType`) |

## Handshake

On join, the client sends an empty `HandshakePayload` probe; the server responds with
the real negotiated parameters. `preferUdpFallback` in config decides whether the
server offers `UDP_FALLBACK` (if its `UdpVoiceServer` is running) or sticks to
`FABRIC_PAYLOAD`. The client reacts in `ClientVoiceManager.setTransport()`, opening a
`UdpVoiceClient` session only when UDP mode was actually negotiated.

## Voice frames

`VoiceFramePayload.speakerId` is the *sender's* UUID — the router rejects any frame
where it doesn't match the connection it arrived on (anti-spoof check in
`VoiceRouter.route()`). `sequence` is a monotonically increasing per-sender counter,
used purely for client-side jitter buffering/reordering — it resets to `0` at session
start and has no relation to Opus internal state. An empty `opusData` on the wire
specifically means "packet-loss concealment marker," synthesized by the sending
`JitterBuffer`, never sent directly from a microphone capture.

## UDP fallback

`UdpVoiceServer` / `UdpVoiceClient` carry the same logical `VoiceFramePayload` over a
raw UDP socket instead of the Minecraft protocol, authenticated with the per-session
secret issued in the handshake. Falls back to the Fabric payload channel automatically
if a UDP send throws (see `ClientVoiceManager.sendFrame()`).

## Mute/deafen sync

`ApiCommandPayload` carries client-originated mute/deafen/channel toggles to the
server; `StateSyncPayload` carries the server's resulting authoritative state back out
to everyone who needs to know (broadcast on change, and once per player on join).
Third-party mods should prefer the [MuteManager](MuteManager.md) /
[DeafenManager](DeafenManager.md) API over constructing these payloads directly — the
managers handle source-mod attribution and priority that raw packets don't.
