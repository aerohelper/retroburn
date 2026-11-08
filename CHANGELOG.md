<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes to RetroBurn are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Architecture and design documentation: solution architecture, high-level
  design, decision records, threat model, risk register, glossary, UX
  specification and extensibility guide.
- Application skeleton: JavaFX window with the home screen (project types not
  available yet), Help → About and Help → Open-source licenses.
- Third-party component register with bundled licence texts, and a build check
  that every runtime dependency is credited.
- Build checks for formatting, licence headers, architecture rules and test
  coverage; CI on Linux and Windows; REUSE compliance.
- Core services: safe external-process runner with timeouts and cancellation,
  tool detection and feature gating, job pipeline with weighted progress and
  work folders, settings file, rotating log files.
- Linux: drive and media detection and ISO image creation with GNU xorriso,
  drive hot-plug and USB detection, battery detection, and keeping the
  computer awake during burns with systemd-inhibit.
- Windows: drive and media detection, burning, erasing, image creation and
  verification through the Windows Image Mastering API (IMAPI2), hot-plug and
  USB detection, battery detection and keeping the computer awake.
- Data discs, Burn ISO, Create ISO and erasing from the screens: drive bar
  with USB badge, capacity bar, checks before burning (disc fits, USB and
  battery advice), confirmation, progress with cancel, and a result screen
  with details. Data discs are burned from an image and compared with it.
- Audio CDs: music in any format FFmpeg reads, titles from tags, reordering,
  length against 74 or 80 minutes, optional loudness levelling, CD-Text on
  Linux (cdrskin), track-at-once on Windows (IMAPI2, no CD-Text yet), and a
  check of the burned disc's track list.
- MP3 discs on CD, DVD or Blu-ray: MP3 files copied unchanged, other formats
  converted with FFmpeg (128 to 320 kbps or VBR), layouts by folder, by
  artist and album or flat, car stereo mode (safe short names, track-number
  prefixes, two folder levels, ID3v2.3 and ID3v1 tags), and playlists.
- Video DVDs: up to 99 videos in any format FFmpeg reads, PAL or NTSC,
  16:9 or 4:3, a bitrate that fills the disc, chapters every few minutes,
  menus with thumbnails, "Play all" and pages in three templates, and
  burning, Create ISO or saving the VIDEO_TS folder (dvdauthor and spumux;
  genisoimage on Linux, IMAPI2 with UDF 1.02 on Windows).
- Cover and label designer: CD front, back inlay with spines, slim insert,
  DVD wrap with a chosen spine and disc labels (41 or 17 mm hole) on A4 or
  Letter with crop marks and bleed; text, pictures, shapes, track list and
  title elements with snapping, alignment and undo; classic, y2k and
  minimal styles; text filled in from the last project; `.rbdesign` files;
  PDF export with embedded fonts (Apache PDFBox), 300 dpi PNG, printing at
  100 % and a test page. Lato and VT323 fonts are bundled.
- Retro skins: a `y2k` theme beside `classic`, switched live from the View
  menu, with bevelled buttons, a gradient header, an LED progress bar and
  the VT323 display font; a spinning disc on the progress and result
  screens; original sounds for spin-up, burning, success and error (on by
  default in `y2k` only); options to turn sounds and the animation off.
- Burn reels: every burn is recorded locally; after a success, "Make my
  burn reel" renders a 15-second vertical video (1080 × 1920, H.264 and
  AAC, with the theme's sounds) of the burn as a timelapse, with item
  names hidden by default, saved to the Videos folder. Help → Make a demo
  burn reel shows one without a drive.
