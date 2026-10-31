<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Test strategy

| | |
|---|---|
| Status | Draft — extended as each module is built |

## 1. Principles

- **Synthetic media only.** All audio, video and images used in tests are
  generated (FFmpeg sine tones and test patterns with fake tags, Java2D
  images) by `tools/gen-test-media`. No real music, video or third-party images
  in the repository, ever.
- **Real tool output, captured.** Parsers are tested against output captured
  from the real tools and APIs, stored with a note of tool version, OS and
  command line. We never write fixtures by imagining what a tool prints.
- **Pure core, thin edges.** Parsers, writers, calculators and renderers are
  pure functions and carry most of the test weight; process and UI code stays
  thin.
- **Same tests on both OSes.** CI runs everything on Linux and Windows.
  OS-specific integration tests skip themselves on the other OS.
- **Tests read as documentation**: `rejectsAudioCdWhenTotalExceedsEightyMinutes()`.

## 2. Levels

| Level | What | How | From |
|---|---|---|---|
| Unit | Credits register, messages, platform detection | JUnit | M1 |
| Architecture | Layering, platform isolation, process rules | ArchUnit (`ArchitectureTest`) | M1 |
| Compliance | Every runtime dependency credited with licence text | `CreditsCompletenessTest` + `reuse lint` in CI | M1 |
| Unit | Tool output parsers (xorriso, cdrskin, cdrdao, FFmpeg progress, Windows helper JSON) | Captured fixtures in `src/test/resources/tool-output/<tool>/` | M2–M7 |
| Unit | Writers (v07t sheet, cdrdao TOC, spumux/dvdauthor XML, m3u) | Golden files in `src/test/resources/golden/`, including hostile input | M5–M7 |
| Unit | Bitrate calculation, car-stereo policy, CD-Text sanitising, reel timeline, migrations | Plain JUnit with edge cases | M5–M10 |
| Unit | Menu and reel renderers | Snapshot images; overlay ≤ 4 colours | M7, M10 |
| Unit | PDF export | Page size, disc-label diameter ±0.5 mm, fonts embedded | M8 |
| Unit | Sleep inhibitor lifecycle, drive watcher | Fake platform: always released on success/failure/cancel; drives added/removed; polling paused during burns | M2–M3 |
| Integration (Linux) | Data/MP3 disc, ISO creation | xorriso writing to a file target → read back, compare tree, names, checksums | M2, M6 |
| Integration (Windows) | Data/MP3 image, DVD UDF image | IMAPI2FS image → mount or parse → compare tree; UDF present | M3, M7 |
| Integration (both) | Audio CD preparation, DVD pipeline to image, reel export | ffprobe and structure checks | M5, M7, M10 |
| Performance | 100,000-file compilation, 512 MB heap | Synthetic file trees, simple timed benchmarks | M4 |
| Manual | Real hardware on both OSes | `docs/MANUAL_TEST.md` | M4 on |

## 3. Fixtures policy

- Location: `src/test/resources/tool-output/<tool>/<scenario>.txt`. Each file
  starts with the exact command line; `tool-output/README.md` records the OS,
  tool versions, drive and media of every capture.
- Fixtures are never edited by hand except to remove personal data (paths,
  serial numbers), which is noted in the README.
- Where no capture exists yet (drive output before hardware tests), a test may
  use lines built from the tool's documented format string, with a comment
  naming the source; captures replace them when available.
- When a tool version changes its output, add a new fixture; keep the old one
  so both stay supported.

## 4. Coverage

At least 70 % line coverage of non-UI code (everything outside `app`, `ui` and
`theme`), enforced by JaCoCo in `verify`. Views are covered by the manual test
checklist.

## 5. Hardware matrix (manual)

| Area | Linux | Windows |
|---|---|---|
| Internal or USB DVD writer | Owner's USB writer | Owner's USB writer |
| Media | CD-R, CD-RW, DVD-R, DVD+RW (BD and DL only if the drive supports them) | same |
| USB-specific | Hot-plug, USB tip, battery warning, sleep prevention, unplug mid-burn on rewritable media only | same |
| Players | A CD player with CD-Text, a car stereo, VLC, a hardware DVD player | same |

Features the available drive cannot test are marked as such in the manual
checklist rather than silently skipped.
