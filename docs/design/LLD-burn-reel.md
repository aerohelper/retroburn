<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Burn sessions and the burn reel (`reel`, screens)

| | |
|---|---|
| Status | Designed for the burn reel milestone (M10) |
| Packages | `io.aerohelper.retroburn.reel` (sessions, timeline, scenes, soundtrack, encoding; Java2D, no JavaFX), `.tools` (streamed standard input), `.ui` (result screen, demo) |
| Related | [ADR-0018](../adr/0018-burn-reel-rendering.md), [EXTENSIBILITY §7](../EXTENSIBILITY.md), [LLD-themes.md](LLD-themes.md) |

## 1. Scope

- Every burn is recorded as a `BurnSession` and saved as JSON in the data
  folder. Nothing leaves the computer.
- After a successful burn the result screen offers **Make my burn reel**:
  a 15-second vertical MP4 (1080 × 1920, 30 fps, H.264 `yuv420p`, AAC)
  that retells the burn as a timelapse. Length 12 to 18 seconds
  (setting), default 15.
- Scenes: intro (disc name and kind) → spin-up → progress (percentage,
  speed, scrolling item names) → "Burn complete ✓" with figures → end
  card "Burned with RetroBurn".
- **Hide item names in reel** (on by default) replaces file, track and
  title names with "Item 1", "Item 2", ….
- Saved to `Videos/RetroBurn/` (`AppPaths.reels()`), with **Open folder**.
- **Help → Make a demo burn reel** renders a made-up session, without a
  drive.

Not in this milestone: the DVD menu preview and the designed cover inside
the reel (the progress scene shows names only).

## 2. Sessions (`reel.BurnSession`, `reel.SessionRecorder`, `reel.SessionStore`)

```java
record BurnSession(int schemaVersion, String id, Instant started, Instant ended,
                   String kind, String label, String theme, List<String> items,
                   long bytes, String outcome, List<Sample> samples, List<Marker> markers)
record Sample(long atMillis, double fraction, String speed)        // at most one per second
record Marker(long atMillis, String step)                          // a step started
```

`SessionRecorder` is a `PipelineListener` placed next to the progress
screen's listener. It keeps overall progress (`OverallProgress`), the
latest speed (`BurnEvent.Progress` inside `StepOutput`) and step starts
(`StepStarted`, the step's message key), thread-safely. `finish(outcome)`
returns the session.

`SessionStore` writes `data/sessions/<started>-<id>.json` (temporary file,
then move) and keeps the newest 50. Unknown fields are ignored on reading.

## 3. Timeline and scenes (`reel.ReelTimeline`, `reel.ReelScene`)

`ReelTimeline.of(seconds)` splits the reel: intro 2.0 s, spin-up 1.5 s,
complete 2.5 s, end card 2.0 s, and the rest for progress (5 s at 13 s,
7 s at 15 s). Frame `n` is at `n / 30` s; `ReelFrame(index, seconds,
scene, sceneFraction)`.

```java
interface ReelScene {
    String id();
    void render(Graphics2D graphics, ReelFrame frame, BurnSession session, ReelStyle style);
}
```

`render` is pure: the same frame gives the same pixels. `ReelStyle`
holds the colours and fonts for the session's theme (`classic` or `y2k`;
the bundled Lato and VT323 fonts). The progress scene maps its own time
onto the session's samples (`fraction` and `speed` at the matching moment
of the real burn, so a 20-minute burn plays in 7 seconds) and scrolls the
item names.

## 4. Soundtrack (`reel.ReelSoundtrack`)

A 22 050 Hz mono 16-bit WAV as long as the reel, written to the job's work
folder: the session theme's spin-up sound at the start of the spin-up
scene, its burning loop under the progress scene, the success sound at the
complete scene, fade-out on the end card. The theme files are read from
`/themes/<theme>/sounds/`; a missing file gives silence there.

## 5. Encoding (`reel.ReelEncoder`)

FFmpeg reads raw frames from standard input and the soundtrack from the
file:

```
ffmpeg -hide_banner -loglevel error -y
       -f rawvideo -pixel_format rgb24 -video_size 1080x1920 -framerate 30 -i pipe:0
       -i <work>/soundtrack.wav
       -map 0:v:0 -map 1:a:0 <video codec> -pix_fmt yuv420p
       -c:a aac -b:a 160k -shortest -movflags +faststart <reels>/<name>.mp4
```

Video codec, the first that `ffmpeg -hide_banner -encoders` lists:

| Encoder | Arguments | Note shown to the user |
|---|---|---|
| `libx264` | `-c:v libx264 -preset medium -crf 20` | — |
| `libopenh264` | `-c:v libopenh264 -b:v 8M` | "made with OpenH264" |
| `mpeg4` | `-c:v mpeg4 -q:v 3` | "MPEG-4 video, not H.264: some phones and sites may not take it" |

`rawvideo` demuxer options (`pixel_format`, `video_size`, `framerate`) are
from `ffmpeg-formats`; the rest are standard FFmpeg options and are
checked by the acceptance test, which runs the real FFmpeg.

Frames are drawn one at a time into a reused `BufferedImage`
(`TYPE_3BYTE_BGR`), turned into RGB bytes and written to FFmpeg's input
by `ProcessRequest.stdinWriter`, a new option that streams data to a
program's standard input from a background thread (about 2.8 GB of raw
frames for 15 s never touch the disk). Cancelling stops FFmpeg; the
partial file is deleted.

File name: `RetroBurn-<yyyyMMdd-HHmmss>.mp4` from the session's start.

## 6. Screens

- Result screen, after a success, when the `BURN_REEL` feature is
  available: **Make my burn reel**, **Hide item names in reel**, then a
  progress line ("Drawing frame 120 of 450"), then "Saved RetroBurn-….mp4"
  with **Open folder**, plus the encoder note when it is not libx264.
- Help → **Make a demo burn reel** does the same from
  `DemoSession.create()`.

## 7. Settings (`Settings.Reel`)

| Key | Default |
|---|---|
| `reel.hideNames` | `true` |
| `reel.seconds` | `15` (12–18) |

## 8. Testing

- Unit: recorder (progress, speed, steps, one sample per second),
  session store round trip and pruning, timeline at 12, 15 and 18 s,
  scenes draw without errors and hide names, soundtrack length and
  format, encoder choice from encoder lists, FFmpeg arguments, the
  streamed standard input.
- Acceptance (Linux and Windows CI, real FFmpeg): a demo reel; `ffprobe`
  reports 1080 × 1920, 14.5–15.5 s, `h264` (or the documented fallback),
  `yuv420p` and an AAC audio stream.
- Manual: make a reel after a real burn; play it on a phone.
