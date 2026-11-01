<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — MP3 disc (`audio`, `project`, screen)

| | |
|---|---|
| Status | Designed for the MP3 disc milestone (M6) |
| Packages | `io.aerohelper.retroburn.audio` (naming rules, playlists, transcoding), `.project` (compilation, layout, pipeline), `.ui` |
| Related | [ADR-0013](../adr/0013-data-discs-burned-from-an-image.md), [LLD-data-burning.md](LLD-data-burning.md), [LLD-audio-cd.md](LLD-audio-cd.md) |

## 1. Scope

An MP3 disc is a data disc of music files for car stereos and MP3 players,
on CD, DVD or Blu-ray. It is burned like a data disc: an image in the work
folder, burned, then compared with the image (ADR-0013).

- MP3 files are copied **unchanged**, byte for byte.
- Other formats FFmpeg can read are left out, or converted to MP3 when
  "Convert other formats" is on: CBR 128, 192 (default), 256 or 320 kbps,
  or VBR quality 2. Without FFmpeg's `libmp3lame` encoder the option is
  disabled with the reason (feature gating, `MP3_TRANSCODING`).
- Layout on the disc: **keep folders** as added, **Artist/Album** from
  tags, or **flat** (one folder).
- **Car stereo mode** (on by default): track-number prefixes, safe and
  short file names, at most two folder levels, warnings for crowded discs,
  ID3v2.3 plus ID3v1 tags on converted files.
- Optional playlist per folder.
- The screen shows size, file count and playing time against the disc.

## 2. Naming (`audio.CarNames`)

Used for every file and folder name on the disc in car stereo mode:

1. Spell out letters that have no base form (`ß` → `ss`, `æ` → `ae`,
   `ø` → `o`, `ł` → `l`, `þ` → `th` and their capitals), then
   NFD-decompose and drop combining marks (`é` → `e`).
2. Keep `A–Z a–z 0–9`, space and `- _ . , ( ) & ' !`; every other
   character becomes `_`; a run of spaces and `_` containing a space
   becomes one space, other runs of `_` one `_`; trim spaces, dots and
   underscores at both ends (`AC/DC: Live?` → `AC_DC Live`).
3. Cut to the limit (default 64 characters for a file name with its
   extension, 32 for a folder), keeping the extension.
4. Empty names become `Track` (files) or `Folder` (folders).
5. Files get a two-digit prefix by their position in the folder:
   `01 - Title.mp3`.

Without car stereo mode, names keep their characters, except the ones no
disc file system allows (`/ \ : * ? " < > |` and control characters,
replaced by `_`), and are cut at 100 characters.

Clashing names in one folder get ` (2)`, ` (3)`… before the extension,
compared without regard to case, as for data discs.

## 3. Layout (`project.Mp3Layout`, `project.Mp3Compilation`)

| Layout | Folder of a file on the disc |
|---|---|
| Keep folders | The folders between the added folder (included) and the file; files added one by one go to the disc's root |
| Artist/Album | `<album artist or artist>/<album>` from the tags; "Unknown artist" and "Unknown album" when missing |
| Flat | The root |

Order within a folder: track number tag (`track`, the part before a `/`),
then disc number, then title or file name. In car stereo mode, folders
deeper than two levels are joined into the second level
(`A/B/C` → `A/B - C`).

Warnings (not blockers) above two limits many head units have, kept as
constants: more than 255 files in one folder, more than 99 folders.

## 4. Conversion (`audio.Mp3Encoding`)

```
ffmpeg -nostdin -hide_banner -loglevel error -y -i <source> -map 0:a:0 -vn
       -c:a libmp3lame (-b:a 192k | -q:a 2)
       -map_metadata 0 -id3v2_version 3 -write_id3v1 1
       -progress pipe:1 -nostats <work>/mp3/NNNN.mp3
```

- `-b:a` gives a constant bitrate; `-q:a 2` is LAME's VBR quality 2.
- `-id3v2_version 3` and `-write_id3v1 1` are options of FFmpeg's `mp3`
  muxer. CI checks the result: the file starts with `ID3` version 3, and its
  last 128 bytes start with `TAG` (ID3v1).
- Size estimate for converted files: bitrate × duration (VBR quality 2 is
  counted as 256 kbps, on the safe side), plus 10 %.
- Conversion runs in parallel like audio CDs; the source is only read.

## 5. Playlists (`audio.M3uWriter`)

One playlist per folder that holds tracks, named after the folder
(`<folder>.m3u`; `Playlist.m3u` at the root):

```
#EXTM3U
#EXTINF:<seconds>,<artist> - <title>
01 - Title.mp3
```

Entries are relative file names. In car stereo mode the file is ASCII
(names are already ASCII; `#EXTINF` texts go through the same character
rule). Otherwise the playlist is written as UTF-8 with the `.m3u8`
extension, which players read as UTF-8.

## 6. Pipeline (`project.Mp3JobFactory`)

| Step | Weight |
|---|---|
| Convert other formats (parallel; only when needed) | converted bytes |
| Build the image from the plan: unchanged MP3s from their sources, converted files and playlists from the work folder | image bytes |
| Erase first / burn / verify | as for data discs |

The plan (`Mp3DiscPlan`) is made before the job starts: each entry has its
disc path and either a source to copy or a conversion to run, so the
screen can show exactly what will be on the disc. Create ISO uses the same
plan without a drive.

## 7. Settings added (`Settings.Mp3`)

| Key | Default |
|---|---|
| `mp3.layout` | `KEEP_FOLDERS` |
| `mp3.carCompat` | `true` |
| `mp3.convert` | `true` |
| `mp3.bitrate` | `CBR_192` (`CBR_128`, `CBR_256`, `CBR_320`, `VBR_2`) |
| `mp3.playlists` | `false` |

## 8. Testing

- Unit: `CarNames` (accents, symbols, length with extension, empty),
  layouts and ordering, depth joining, clashes, warnings, playlists,
  conversion arguments, size estimate.
- Integration, both systems: an image built from a plan of MP3 files with
  awkward names has the expected prefixed, sanitized paths, and the MP3s
  in it are byte-identical to their sources (Linux: xorriso; Windows:
  IMAPI2FS, mounted). The MP3s are small files made in the test; their
  bytes are only copied, never decoded.
- Integration, Linux CI with FFmpeg: a FLAC tone converted with each
  bitrate choice gives an MP3 with ID3v2.3 and ID3v1.
- Manual: a car stereo plays the disc in order (MANUAL_TEST).
