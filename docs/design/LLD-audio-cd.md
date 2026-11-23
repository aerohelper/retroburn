<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Audio CD with CD-Text (`audio`, `project`, engines, screen)

| | |
|---|---|
| Status | Built in the audio CD milestone (M5): models, rules, writers and FFmpeg wrappers; engines (Linux cdrskin, Windows IMAPI2 track-at-once); checks, pipeline and screen. Not yet tried on a real CD (no CD-R or CD-RW at hand); Windows CD-Text waits for the spike in ADR-0014. |
| Packages | `io.aerohelper.retroburn.audio` (rules, writers, FFmpeg), `.project` (compilation, checks, steps), `.engine` (`AudioCdEngine`, `AudioDisc`), `.platform.linux`, `.platform.windows`, `.ui` |
| Related | [ADR-0014](../adr/0014-cd-text-on-windows-through-cdrdao.md), [HLD §4.2, §5](../architecture/HLD.md), [LLD-data-burning.md](LLD-data-burning.md), [LLD-tools.md](LLD-tools.md) |

## 1. Scope

The Audio CD card becomes usable on Linux and Windows:

- Add music files in any format FFmpeg can decode (MP3, FLAC, WAV,
  OGG/Opus, M4A/AAC…). Copy-protected files are refused with a clear
  message.
- Edit the album title and performer and, per track, title and performer;
  reorder and remove tracks. Fields start from the file's tags, or from the
  file name when there are none.
- See the running length against 74 or 80 minutes (or the inserted disc);
  at most 99 tracks; over-length is blocked.
- Burn to CD-R or CD-RW with standard 2-second gaps, CD-Text where the
  system can write it, and optional loudness normalisation (off by
  default). The original files are never changed.

## 2. Red Book numbers (`audio.CdAudio`)

| Constant | Value |
|---|---|
| Sample rate, channels, sample size | 44,100 Hz, 2, 16-bit signed little-endian (in WAV) |
| Bytes per sector | 2352 = 588 stereo samples |
| Sectors per second | 75 |
| Gap before each track | 150 sectors (2 s); the first track always has one |
| Shortest track | 4 s = 300 sectors (cdrdao manual: "The length of a track must be at least 4 seconds") |
| Most tracks | 99 |
| 74-minute and 80-minute CD-R | 333,000 and 360,000 sectors |

`CdAudio` also turns sectors into `mm:ss` for the screen and `mm:ss:ff` for TOC
files.

## 3. Models

| Type | Package | Responsibility |
|---|---|---|
| `ProbedAudio` (record) | `audio` | What ffprobe says about a file: duration, codec, codec tag, sample rate, channels, and `AudioTags` (title, artist, album, album artist, all optional). |
| `AudioTools` | `audio` | Runs ffprobe (refusing files without sound or with copy protection) and FFmpeg's conversion. |
| `TrackListCheck` | `audio` | Compares a burned disc's table of contents with what was written (section 6.3). |
| `FfprobeJsonParser` | `audio` | Reads `ffprobe -print_format json -show_format -show_streams` output (section 5.1). |
| `CdTextRules` | `audio` | Cleans one CD-Text field (section 4). |
| `CdTextBudget` | `audio` | Whether the texts fit in one CD-Text block (section 4.2). |
| `V07tSheetWriter` | `audio` | Sony input sheet v0.7T for cdrskin (section 6.1). |
| `CdrdaoTocWriter` | `audio` | cdrdao TOC file with `CD_TEXT` blocks (section 6.2); used only if ADR-0014 is accepted. |
| `CdWavWriter` | `audio` | Wraps raw PCM in a 44-byte WAV header and pads it to whole sectors (section 5.3). |
| `AudioCompilation` | `project` | Album title and performer, ordered `Track`s (source file, probed facts, title, performer); add, remove, move; the cleaned CD-Text and the fields that cannot be written. Immutable like `DataCompilation`. |
| `AudioPreCheck` | `project` | The audio rules on top of `BurnPreCheck` (section 7). |
| `AudioJobFactory` | `project` | The pipeline (section 8). |
| `AudioDisc` (record) | `engine` | What an engine burns: the converted WAV files with their sector counts and the CD-Text (or none). Every track gets a 2-second gap. |
| `AudioCdEngine` | `engine` | `burnAudioCd(drive, disc, listener, cancel)`, `writesCdText()`, `readTrackLayout(drive)` for the check after burning. Every platform offers one (`PlatformServices.audioCdEngine()`); feature gating decides whether the tools are there. |
| `TrackLayout` (record) | `engine` | A disc's table of contents: track start LBAs and the lead-out LBA. |

Track titles default to the `title` tag, then the file name without its
extension. Performers default to `artist`. The album title defaults to the
`album` tag shared by all tracks, the album performer to `album_artist`
(or `artist` when all tracks share it). Tag names are matched without
regard to case, because containers differ (ID3 gives `title`, Vorbis
comments often `TITLE`); stream tags are used when the format has none.

## 4. CD-Text rules

### 4.1 Characters

CD-Text is written in ISO-8859-1 (libburn `doc/cdtext.txt`: a block is
ISO-8859-1, 7-bit ASCII or MS-JIS; RetroBurn uses ISO-8859-1 only).
`CdTextRules.clean(text)` returns the cleaned text and a status:

1. Normalise to NFC; turn line breaks and tabs into spaces; drop other
   control characters; collapse runs of spaces; trim.
2. Replace common typographic punctuation with Latin-1 equivalents:
   `‘ ’ ‚ ′` → `'`, `“ ” „ ″` → `"`, `– — ‒ −` → `-`, `…` → `...`.
3. Every remaining character outside ISO-8859-1 is decomposed (NFD) and its
   combining marks dropped (`ā` → `a`, `ő` → `o`). Characters already in
   ISO-8859-1 (`é`, `ü`, `ñ`) are kept.
4. If any letter is still outside ISO-8859-1 (Devanagari, Cyrillic, CJK…),
   the status is **needs Latin text**: the field cannot be written as
   CD-Text, and the screen asks for a Latin-script version. The original
   stays for covers and reels.
5. Fields longer than 80 characters are **too long**. (The format has no
   per-field limit, only the block budget below; 80 keeps one long title
   from using up the whole budget.)

Status values: `UNCHANGED`, `ADJUSTED` (shown as a note: "Will be written
as …"), `NEEDS_LATIN`, `TOO_LONG`. The last two block the burn while CD-Text
is on; turning CD-Text off removes the block.

### 4.2 Budget

A CD-Text block holds at most 253 packs of 12 payload bytes (libburn
`doc/cdtext.txt`). RetroBurn writes titles (pack type 0x80) and performers
(0x81) for the album and every track, plus the block's three size-info
packs (0x8f). Each pack type uses `ceil(sum(length + 1) / 12)` packs, the
`+ 1` being the 0-terminator. `CdTextBudget` blocks the burn when
`packs(0x80) + packs(0x81) + 3 > 253`; the message names the total and the
limit. The repeat-shortcut (a TAB for "same as previous track") is not
counted, so the check is on the safe side.

## 5. FFmpeg

### 5.1 Probing

```
ffprobe -v error -print_format json -show_format -show_streams <file>
```

Run in the background when files are added (bounded pool, as many workers
as cores minus one), with a 30-second timeout per file. Parsed fields:
`format.duration`, `format.tags`, and from the first stream with
`codec_type` `audio`: `codec_name`, `codec_tag_string`, `sample_rate`,
`channels`, `duration`, `tags`. A file without an audio stream is refused
("This file has no sound RetroBurn can use"). A file whose audio stream has
the codec tag `drms` (the MP4 sample entry of FairPlay-protected tracks) is
refused as copy-protected; other protected files fail to decode and get the
general "can't use" message. Fixtures are captured from the real ffprobe on
synthetic files (`src/test/resources/tool-output/ffmpeg/`).

### 5.2 Converting

```
ffmpeg -nostdin -hide_banner -loglevel error -y -i <source>
       -map 0:a:0 -vn -sn -dn -ac 2 -ar 44100 [-af loudnorm=I=-16:TP=-1.5:LRA=11]
       -c:a pcm_s16le -f s16le -progress pipe:1 -nostats <work>/NN.pcm
```

- One process per track, run in parallel on the same bounded pool. The
  sources are only read.
- `-progress pipe:1` prints `key=value` lines; `out_time_us` against the
  probed duration gives the track's progress.
- Loudness normalisation uses FFmpeg's `loudnorm` filter in one pass, per
  track, aiming at −16 LUFS. It is off by default (setting
  `audio.normalizeLoudness`).
- The options are checked in CI against the installed FFmpeg, and the
  output is probed (44,100 Hz, 2 channels, `pcm_s16le`).

### 5.3 WAV files for the burners

`CdWavWriter` copies `NN.pcm` into `NN.wav` with a canonical 44-byte header
(RIFF, `fmt ` PCM 1, 2 channels, 44,100 Hz, 176,400 bytes/s, block align 4,
16 bits, `data`), appends zeros up to the next multiple of 2352 bytes, and
deletes the `.pcm`. Every burner then gets the same files: cdrskin and
cdrdao read `.wav` files with little-endian samples; the Windows helper
gives IMAPI2 the bytes after the 44-byte header, a whole number of
sectors. Tracks shorter than 4 s are blocked before conversion.

## 6. Burning

### 6.1 Linux: cdrskin

```
cdrskin -v dev=<drive> -sao [input_sheet_v07t=<work>/cdtext.v07t] -audio
        <work>/01.wav sao_pregap=150 <work>/02.wav sao_pregap=150 <work>/03.wav …
```

From the cdrskin manual (Ubuntu 24.04 `cdrskin.1`):

- `-sao` "can be used to write CD-TEXT"; `input_sheet_v07t=` requires SAO
  on CD and "All tracks must be -audio tracks".
- `-audio` "Announces that the subsequent tracks are to be burned as
  audio"; `.wav` files with a matching MS-WAVE header are read as audio.
- "The first track automatically gets a pre-gap of at least 150 sectors."
  `sao_pregap=` sets the pre-gap of the following track; RetroBurn repeats
  it before every later track, which gives 2-second gaps whether or not the
  setting carries over.

The sheet (`V07tSheetWriter`) is written in ISO-8859-1:

```
Input Sheet Version = 0.7T
Text Code           = 8859
Language Code       = English
Album Title         = <album title>
Artist Name         = <album performer>
First Track Number  = 1
Last Track Number   = <n>
Track 01 Title      = <title>
Track 01 Artist     = <performer>
…
```

Progress uses the cdrecord-style pacifier already parsed for xorriso's
cdrecord emulation (`X of Y MB written`), confirmed against real cdrskin
output in CI. Cancel stops the process like the other engines.

### 6.2 Windows

- **Without CD-Text** (now, and the fallback): the helper's
  `burn-audio` operation uses `MsftDiscFormat2TrackAtOnce`:
  `PrepareMedia`, then `AddAudioTrack` per track with `PcmStream`, a COM
  stream over the WAV data after the 44-byte header (Microsoft Learn:
  "raw audio samples ... without the header"), then `ReleaseMedia`, which
  closes the disc (also after a cancel, through `CancelAddTrack`).
  Microsoft Learn: track-at-once adds a 2-second gap between tracks and
  pads tracks shorter than 4 seconds. Progress comes from
  `DDiscFormat2TrackAtOnceEvents` (`{2735413F-…}`, DISPID 0x200), whose
  arguments carry `CurrentTrackNumber`, `CurrentAction` (unknown,
  preparing, writing, finishing, verifying), `StartLba` and
  `LastWrittenLba`; all read from the type library with
  `tools/windows/dump-imapi2-typelib.ps1`. `read-toc` reads the table of
  contents with `IOCTL_CDROM_READ_TOC` on the drive letter.
- **Checked on the owner's hardware (no CD at hand):** `read-toc` on a
  DVD+RW returns one track and the lead-out; `IsCurrentMediaSupported` of
  the track-at-once writer is false for that disc and true for the
  recorder, so an audio burn on a DVD stops before anything is written.
- **With CD-Text** (if ADR-0014 is accepted): bundled `cdrdao write
  --device <drive> <work>/disc.toc`, with a TOC from `CdrdaoTocWriter`:

```
CD_DA
CD_TEXT {
  LANGUAGE_MAP {
    0 : EN
  }
  LANGUAGE 0 {
    TITLE "<album title>"
    PERFORMER "<album performer>"
  }
}

TRACK AUDIO
CD_TEXT {
  LANGUAGE 0 {
    TITLE "<title>"
    PERFORMER "<performer>"
  }
}
PREGAP 0:2:0
FILE "01.wav" 0
```

  Strings escape `"` and `\` as `\"` and `\\`; the file is UTF-8 and holds
  only ISO-8859-1 characters after `CdTextRules`. TITLE and PERFORMER are
  written for the disc and every track, because cdrdao requires an item to
  be present everywhere once it is used anywhere. Every track, the first
  included, gets `PREGAP 0:2:0`, as in the manual's "Standard track with two
  second pre-gap" example; CI checks the total with `cdrdao toc-size`
  (blocks = 150 per track + the WAV sectors).

### 6.3 After burning

The engine reads the disc's table of contents (`cdrskin -toc`, parsed
after the `printf` formats in `cdrskin.c`; `read-toc` on Windows) and
`TrackListCheck` compares it with what was written: the same number of
tracks; each track except the last spans its sectors plus the next track's
150-sector gap, the last one ends at the lead-out; up to 75 sectors (one
second) off is accepted for run-out sectors. A mismatch
(`error.trackListMismatch`) is reported like a failed verify. Audio
sectors have no error-free read-back on every drive, so the samples
themselves are not compared.

## 7. Pre-checks (additions to `BurnPreCheck`)

| Check | Result |
|---|---|
| Media is not CD-R or CD-RW | Blocker: "Audio CDs need a CD-R or CD-RW." |
| No tracks | Blocker (button disabled) |
| More than 99 tracks | Blocker |
| A track shorter than 4 s | Blocker naming the track |
| Total length (tracks + gaps) over the disc | Blocker with the excess in `mm:ss` |
| CD-Text on and a field `NEEDS_LATIN` or `TOO_LONG` | Blocker naming the track and field |
| CD-Text on and over the budget | Blocker |
| CD-Text on and the engine cannot write it (Windows until ADR-0014) | Warning: "This computer can't write CD-Text; the disc will play without track names." |
| CD-RW with data | Quick erase first (as for data discs) |
| Work folder: the WAV files | `sectors × 2352` bytes plus 10 % must be free |

The disc's length in sectors is its free 2048-byte blocks as reported by
the engine (`MediaInfo.freeBytes / 2048`), which for a blank CD-R is the
same count as audio sectors. So the request's `bytesToWrite` for an audio
job is the disc's sectors × 2048 (`AudioPreCheck.blocksToWrite`), and the
generic "too big" and "work folder full" results are replaced by the audio
ones above. Without a disc, the screen compares with 80 minutes.

## 7.1 Screen (`ui.AudioCdView`)

Drive bar, heading, a minutes bar (`CapacityBar.showTime`: "mm:ss of
mm:ss"), album title and performer, the CD-Text option (or, where the
engine cannot write CD-Text, a sentence saying so) and "Even out
loudness", both saved as settings. The track table has #, Title and
Performer (editable), Length and From; Add files (or drop files), Remove,
Move up, Move down. Files are probed in the background; files that cannot
be used and CD-Text fields that cannot be written are listed under the
table. Burn goes through `BurnFlow` with `AudioPreCheck` as the job's
planner.

## 8. Pipeline

| Step | Weight |
|---|---|
| Convert tracks (parallel) | total samples |
| Write WAV files | 0.1 × total samples |
| Erase first (CD-RW with data) | as for data discs |
| Burn | 2 × total samples |
| Check track list | small fixed |

The job reuses `BurnJob` (hot-plug paused, drive-lost detection, kept work
folder on failure).

## 9. Settings added

| Key | Default | Meaning |
|---|---|---|
| `audio.cdText` | `true` | Write CD-Text when the system can |
| `audio.normalizeLoudness` | `false` | Run `loudnorm` while converting |

## 10. Testing

- Unit: `CdTextRules` (Latin-1 kept, other accents stripped, Devanagari needs
  Latin, punctuation, control characters, length), `CdTextBudget` at the
  edge, `CdAudio`, `FfprobeJsonParser` on captured output, `CdWavWriter`
  (header bytes, padding), sheet and TOC against golden files,
  `AudioCompilation` defaults from tags, the pre-checks.
- Integration (Linux CI, real tools): synthetic tones with tags (FFmpeg
  `lavfi` sine sources) → probe → convert → WAV probed as 44,100 Hz stereo
  `pcm_s16le` with a whole number of sectors; the generated TOC parsed by
  `cdrdao show-toc` and sized by `cdrdao toc-size`; cdrskin's progress
  lines captured as a fixture.
- Windows CI: the same conversion test where FFmpeg is installed.
- Manual (`docs/MANUAL_TEST.md`): burn a CD-R on each system, play it in a
  CD player, check CD-Text on a capable player or read it back with
  `cdrskin cdtext_to_v07t=`.

## 11. How to change this safely

- New CD-Text fields (songwriter, composer…) go through `CdTextRules` and
  must be counted in `CdTextBudget`, then added to both writers.
- Any change to the FFmpeg command lines needs the CI conversion test to
  pass and the fixture re-captured.
