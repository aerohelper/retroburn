<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Video DVD with menus (`video`, `project`, platform images, screen)

| | |
|---|---|
| Status | Built in the Video DVD milestone (M7); hardware-player checks pending |
| Packages | `io.aerohelper.retroburn.video` (standards, bitrate, menus, FFmpeg, dvdauthor and spumux files), `.project` (project, pipeline), `.engine` (`buildDvdVideoImage`), `.platform.linux`, `.platform.windows`, `.ui` |
| Related | [ADR-0015](../adr/0015-video-dvd-structure.md), [HLD §5](../architecture/HLD.md), [LLD-data-burning.md](LLD-data-burning.md) |

## 1. Scope

Up to 99 video files become a DVD that plays in DVD players: a menu with
a thumbnail button per title (six per page), "Play all", page buttons,
and chapters every few minutes. The result can be burned to DVD±R/RW,
saved as an `.iso`, or saved as a `VIDEO_TS` folder.

Choices on the screen: PAL or NTSC (default from the computer's country:
NTSC for the US, Canada, Mexico, Japan, South Korea, Taiwan, the
Philippines and most of the Americas; PAL elsewhere, including India,
Europe and Australia), 16:9 or 4:3 (detected from the first title,
editable), chapter interval (default 5 minutes, 0 for none), menu template
(`classic`, `y2k`, `minimal`), disc title, what happens after a title
(back to the menu, or the next title).

Not in this milestone: background images or music for the menu, manual
chapter points, live preview animation (the screen shows the rendered
first menu page as a still).

## 2. DVD numbers (`video.TvStandard`, `video.DvdBitrate`)

| | PAL | NTSC |
|---|---|---|
| Frame | 720 × 576 | 720 × 480 |
| Rate | 25 fps | 30000/1001 fps |
| FFmpeg target | `pal-dvd` | `ntsc-dvd` |
| Menu drawn at (square pixels, 4:3) | 768 × 576 | 640 × 480 |

Audio: AC-3, 48 kHz, stereo, 192 kbps.

**Fit-to-disc bitrate.** Usable bytes = disc capacity × 0.97 (safety
margin) − menu bytes (2 MB per menu page). Total kbit/s = usable bytes × 8
÷ total seconds ÷ 1000. Video kbit/s = total × 0.98 (multiplexing
overhead) − 192, clamped to at most 8,000 (DVD's 9,800 kbit/s peak leaves
room for the variable rate) and at least 1,000. Below 3,500 the screen
warns: "Picture quality will be low. Use fewer titles or a dual-layer
disc." Capacity: the inserted disc, or 4.7 GB (DVD±R) without one.

## 3. Disc structure (ADR-0015)

- One titleset holds every title; titles therefore share one TV standard
  and one aspect ratio (the disc's).
- Menus are 4:3 in the VMGM. One menu PGC per page; the first is the
  title menu (`entry="title"`).
- Buttons: one per title on the page (`g1 = 0; jump title N;`), "Play
  all" (`g1 = 1; jump title 1;`), and "Next page"/"Previous page"
  (`jump menu P;`) when there are more pages. Button neighbours (`up`,
  `down`, `left`, `right`) are written out for every button rather than
  left to spumux's guess, so remote-control navigation follows the grid.
- After title N: back to its menu page (`g1 = 0; call vmgm menu P;`);
  with "play next" chosen, `jump title N+1;`; otherwise
  `if (g1 eq 1) jump title N+1;` first, so "Play all" carries on. The
  last title always returns to the menu.
- Chapters: `chapters="0,5:00,10:00,…"` on each title's `<vob>`.

Syntax from the dvdauthor manual: `<dvdauthor dest>` → `<vmgm><menus>`
with `<video format aspect/>` and `<pgc entry="title">` holding `<vob
file pause="inf"/>`, `<button name>commands;</button>`; `<titleset><titles>`
with `<video>`, `<audio>` and a `<pgc>` per title with `<vob file
chapters>` and `<post>`.

## 4. Menus (`video.MenuRenderer`, `video.MenuTemplate`)

Java2D draws, per page, at the square-pixel size, then scales to the DVD
frame:

- **background**: template colours, disc title, a 3 × 2 grid of
  thumbnails (FFmpeg frame at 10 % of each title) with titles under
  them, and the "Play all" and page labels;
- **highlight** and **select** overlays: transparent images with an
  outline around each button in one colour, no anti-aliasing (at most four
  colours: transparent plus one), as spumux needs.

`video.DvdXml` writes the spumux file: `<subpictures><stream><spu start="00:00:00.00"
force="yes" image highlight select>` with a `<button name x0 y0 x1 y1
up down left right/>` per button; y coordinates are even, as the manual
asks. Buttons are named `t1`…`t6`, `all`, `prev`, `next`.

Templates (`classic`, `y2k`, `minimal`) are data: background top and
bottom colours, text colour, highlight colour, font family and size, and
whether thumbnails get a frame. No logos. The templates use the bundled
fonts (`classic` and `minimal`: Lato; `y2k`: VT323) through
`video.MenuFonts`, so menus look the same on every system. Until the
retro skins milestone they used the JDK's logical fonts.

## 5. FFmpeg (`video.FfmpegVideo`)

```
# title
ffmpeg -nostdin -hide_banner -loglevel error -y -i <source>
       -map 0:v:0 -map 0:a:0? -target pal-dvd -aspect 16:9
       -vf scale=720:576 -b:v <kbps>k -maxrate 8000k
       -c:a ac3 -b:a 192k -ar 48000 -ac 2
       -progress pipe:1 -nostats <work>/titles/NN.mpg
# thumbnail
ffmpeg ... -ss <10 % of duration> -i <source> -frames:v 1 -vf scale=320:-2 <work>/thumbs/NN.png
# menu page
ffmpeg ... -loop 1 -framerate 25 -i page.png -f lavfi -i anullsrc=r=48000:cl=stereo -t 1
       -target pal-dvd -aspect 4:3 -c:a ac3 -b:a 192k -shortest <work>/menus/P.mpg
spumux -m dvd <work>/menus/P.xml < P.mpg > P-buttons.mpg
dvdauthor -x <work>/dvd.xml           # writes <work>/dvd/VIDEO_TS
```

`-target` is documented in `ffmpeg.html` ("vcd, svcd, dvd, dv, dv50 …
prefixed with pal-, ntsc- or film-"); the other options are standard.
Titles are converted one at a time (DVD encoding already uses every
core). The source is only read.

## 6. Disc images (`DiscImageBuilder.buildDvdVideoImage`)

- **Linux:** `genisoimage -dvd-video -V <label> -o <target> <dvd folder>`
  (manual: "Generate a DVD-Video compliant UDF filesystem … sorting the
  order of the content … and adding padding"; the folder holds
  `VIDEO_TS` and an empty `AUDIO_TS`).
- **Windows:** the helper's `build-image` with `"dvdVideo": true`:
  ISO 9660 + UDF only (no Joliet), `UDFRevision = 0x102` (UDF 1.02, listed
  in `UDFRevisionsSupported` on Windows 11), the files added in DVD order
  (`VideoTsOrder`: `VIDEO_TS.IFO`, `VIDEO_TS.VOB`, `VIDEO_TS.BUP`, then per
  titleset `VTS_nn_0.IFO`, `VTS_nn_0.VOB`, `VTS_nn_1.VOB`…, `VTS_nn_0.BUP`)
  and an empty `AUDIO_TS`. Whether IMAPI2FS lays the data out in that
  order is checked by a test that reads the image's ISO 9660 directory;
  players are the real test (risk noted in ADR-0015).

## 7. Pipeline (`project.DvdJobFactory`)

| Step | Weight |
|---|---|
| Convert titles (one at a time) | total seconds |
| Thumbnails | 0.02 × titles |
| Draw and encode menus, add buttons (spumux) | 0.05 × pages |
| Author (`dvdauthor`) | 0.05 × total seconds |
| Build the image (burn or ISO) | image bytes |
| Erase first / burn / verify | as for data discs |

The work folder needs about twice the estimated output (titles plus
image); the check blocks below that. "Save VIDEO_TS folder" copies the
authored folder to the chosen place and stops.

## 8. Settings (`Settings.Video`)

| Key | Default |
|---|---|
| `video.tvStandard` | from the computer's country |
| `video.chapterMinutes` | `5` |
| `video.template` | `classic` |
| `video.afterTitle` | `MENU` (`MENU`, `NEXT`) |

## 9. Testing

- Unit: bitrate maths and clamping, chapter lists, TV standard by
  country, menu pages and button layout, dvdauthor and spumux XML, FFmpeg
  arguments, `VideoTsOrder`, rendered overlays have at most two colours.
- Integration (Linux CI, real tools): two 20-second synthetic clips →
  `VIDEO_TS` with `VIDEO_TS.IFO`; the title table in `VIDEO_TS.IFO` lists
  2 titles with the expected chapter counts; the menu has 3 buttons
  (2 titles, Play all); the `genisoimage` image holds a UDF volume
  (`NSR02` descriptor).
- Integration (Windows): an IMAPI2FS DVD-Video image of a `VIDEO_TS`
  folder has UDF 1.02 (`NSR02`), no Joliet, and its VIDEO_TS files in DVD
  order on disc.
- Manual: plays and navigates in VLC and a hardware DVD player, for discs
  made on each system ([MANUAL_TEST.md](../MANUAL_TEST.md), video DVD
  section).

Results so far:

- Linux CI (`DvdJobIntegrationTest`, Ubuntu packages of FFmpeg,
  dvdauthor, spumux and genisoimage): the `VIDEO_TS` folder has 2 titles
  with 1 chapter each, and the image has `NSR02` with the VIDEO_TS files
  in DVD order. Passed.
- Windows 11 (`WindowsHelperIntegrationTest`, IMAPI2FS): `NSR02`, no
  Joliet, VIDEO_TS files in DVD order. Passed.
- Windows build of dvdauthor and spumux 0.7.2 from pinned source in
  MSYS2 UCRT64 (`windows-tools/build-dvdauthor.sh`, workflow "Windows
  tools"): passed, and the built dvdauthor authored a 5-second PAL title
  (`VIDEO_TS.IFO`, `VTS_01_0.IFO`, `VTS_01_1.VOB`). What it took:
  libxml2 found with pkg-config (libxml2 2.13 no longer ships
  `libxml.m4`), `freetype-config` answered from pkg-config, libdvdread,
  bison and flex from MSYS2, and small MinGW shims (byte-order functions,
  one-argument `mkdir`, `fsync`, `bzero`, no iconv so text subtitles are
  read as UTF-8). Only `dvdauthor` and `spumux` are built. They need
  about 19 UCRT64 DLLs (libxml2, libpng, freetype, fontconfig, fribidi,
  harfbuzz, glib and their dependencies; the list is in the workflow's
  `dlls.txt`). Bundling them with their licences is release work (M11),
  and spumux on Windows has so far only been started, not run on the
  app's menu files.
- Not run yet: hardware DVD players.
