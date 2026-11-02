<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0015 — Video DVD structure and images

- Status: Proposed
- Date: 2026-11-02
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

A video DVD can be authored in many shapes: several titlesets with
different aspect ratios, menus in each titleset or in the VMGM, 4:3 or
16:9 menus. Each shape has its own player-compatibility pitfalls, and
RetroBurn has no hardware player in CI. The image must also be readable by
players, which on Windows means IMAPI2FS has to produce what genisoimage's
`-dvd-video` mode produces on Linux. Which structure, and how are images
built?

## Decision drivers

- Plays on hardware players, not just VLC.
- Simple enough to check automatically (IFO title table, button count,
  UDF descriptor).
- No extra tools on Windows beyond FFmpeg, dvdauthor and spumux.

## Considered options

1. One titleset for all titles; 4:3 menus in the VMGM; titles share the
   disc's TV standard and aspect ratio.
2. A titleset per aspect ratio, menus in each titleset.
3. Same as 1, but 16:9 menus (widescreen subpicture streams).

## Decision outcome

Proposed: **option 1**. 4:3 menus with one subpicture stream are the most
widely supported form; a single titleset keeps the navigation commands
simple (`jump title N`, `call vmgm menu P`). Mixed-aspect sources are
converted to the disc's aspect ratio (letter- or pillar-boxed by the
player as usual).

Images: genisoimage `-dvd-video` on Linux. On Windows, IMAPI2FS with ISO
9660 + UDF 1.02 and the VIDEO_TS files added in DVD order. IMAPI2FS has no
documented DVD-Video mode, so a test reads back where the files landed;
hardware players are the real check (manual test on discs made on each
system). If players reject Windows images, the fallback is to build the
image with a bundled tool on Windows too (a new ADR).

### Consequences

- Good: one well-understood structure; automated checks for titles,
  chapters, buttons and UDF.
- Bad: no per-title aspect ratio; 16:9 menus are not offered.
- Risk: Windows images rely on IMAPI2FS's file layout until verified on
  players.
