<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0018 — Burn reels drawn with Java2D and encoded by FFmpeg from a pipe

- Status: Proposed
- Date: 2026-11-08
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

The burn reel is a 15-second 1080 × 1920 H.264 video with sound, made on
the user's computer from a recorded burn session. How are the frames
drawn and encoded, with which encoder, and where does the sound come from?

## Decision drivers

- No new dependency; FFmpeg is already used for audio and video.
- Same result on Linux and Windows; testable without a screen.
- Raw frames for 15 s are about 2.8 GB: they must not be written to disk.
- FFmpeg builds differ: some have no libx264.

## Considered options

1. Java2D frames streamed as raw RGB to FFmpeg's standard input.
2. PNG frames in the work folder, then FFmpeg reads the image sequence.
3. JavaFX snapshots of animated nodes.

## Decision outcome

Proposed: **option 1**. Scenes draw into a `BufferedImage` with Java2D,
the bytes go to `ffmpeg -f rawvideo -pixel_format rgb24 -video_size
1080x1920 -framerate 30 -i pipe:0` through a new streamed-stdin option of
`ProcessRequest`. Encoder: `libx264`, else `libopenh264`, else `mpeg4`,
picked from `ffmpeg -encoders` and named to the user when it is not
`libx264`. The sound is mixed in Java from the theme's own sounds
(ADR-0017) into a WAV file and encoded as AAC by FFmpeg's built-in
encoder.

### Consequences

- Good: no disk space for frames; scenes are pure functions that unit
  tests can draw; works headless.
- Bad: drawing 450 large frames takes several seconds of CPU.
- Option 3 was not chosen: JavaFX snapshots need the UI thread and a
  toolkit, which tests and the encoding thread do not have.

## Findings so far (2026-11-08)

- The FFmpeg command in LLD-burn-reel section 5 works as written with the
  FFmpeg of Ubuntu's packages and of Chocolatey on Windows: `ffprobe`
  reports `h264`, 1080 × 1920, `yuv420p`, `aac`, 15.000 s
  (`ReelAcceptanceTest`, CI).
- Streaming 450 raw frames through `ProcessRequest.stdinWriter` took 14 s
  (Linux) and 24 s (Windows) on the CI runners, drawing included.
