<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0017 — Theme sounds made by a generator and played with Java Sound

- Status: Proposed
- Date: 2026-11-07
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

Themes have four sounds (spin-up, burning loop, success, error). They must
be original or CC0, credited, and play on Linux and Windows without new
dependencies or native code.

## Decision drivers

- No third-party audio in the repository; every asset traceable.
- No new runtime dependency (`javafx-media` adds native GStreamer code on
  Linux).
- Short, loopable, small files.

## Considered options

1. Generate the sounds with a small program in `tools/sounds/`, commit the
   WAV files, play them with `javax.sound.sampled` (part of the JDK).
2. Download CC0 sounds and play them with `javafx-media`.
3. Synthesise the sounds at run time.

## Decision outcome

Proposed: **option 1**. `tools/sounds/GenerateSounds.java` is a
single-file Java program (run with `java tools/sounds/GenerateSounds.java
src/main/resources/themes`) that writes the WAV files of both themes from
sine and square waves with envelopes; it is deterministic, so running it
again gives the same bytes. The files are listed in `assets/CREDITS.md` as
original work under GPL-3.0-or-later. Playback uses `Clip` from Java
Sound; a missing audio device only turns sounds off.

### Consequences

- Good: no new dependency, no downloaded assets, anyone can regenerate or
  change the sounds.
- Bad: simple synthetic sounds; WAV files of a few hundred kilobytes in
  all.
- Option 3 was not chosen because theme authors could not drop in their
  own files.

## Findings so far (2026-11-07)

- Running the generator twice gives byte-identical files (checked with
  SHA-256).
- Java Sound reads every file as 22 050 Hz mono 16-bit signed PCM
  (`ThemeFilesTest`, Windows 11). Playing them on a real audio device on
  each system is a manual check (`docs/MANUAL_TEST.md`, THEME-2).
