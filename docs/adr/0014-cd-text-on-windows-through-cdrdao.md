<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0014 — CD-Text on Windows through cdrdao

- Status: Proposed (spike in progress; the outcome needs the owner's approval)
- Date: 2026-11-01
- Deciders: project owner; proposed by the developer

## Context and problem statement

Audio CDs carry their album and track names as CD-Text in the lead-in of
the disc. On Linux, cdrskin writes CD-Text from a Sony input sheet (v0.7T).
On Windows, RetroBurn burns through IMAPI2, and IMAPI2 has no documented
way to write CD-Text. Can Windows users get CD-Text, and how?

## Decision drivers

- No administrator rights: RetroBurn never asks for elevation.
- Same result on both systems where possible.
- Every bundled binary must be built in our CI from pinned source, with
  its source published ([ADR-0006](0006-external-tool-provisioning-per-os.md)).
- Audio CDs must keep working on Windows even if CD-Text cannot be offered.

## Considered options

1. **cdrdao** (GPL-2.0-or-later) bundled on Windows, writing the whole audio
   disc from a TOC file with `CD_TEXT` blocks when CD-Text is wanted; IMAPI2
   track-at-once stays as the path without CD-Text.
2. **IMAPI2 raw disc-at-once** (`MsftRawCDImageCreator` and
   `MsftDiscFormat2RawCD`) with CD-Text packs that RetroBurn encodes itself.
3. **No CD-Text on Windows in v0.1:** IMAPI2 track-at-once only; the CD-Text
   option is shown disabled with an explanation.

## Findings so far (2026-11-01)

| Question | Finding | How it was checked |
|---|---|---|
| Does IMAPI2 have a CD-Text member? | No. `MsftDiscFormat2TrackAtOnce` offers `AddAudioTrack(IStream)` and no text properties. `MsftRawCDImageCreator` offers `AddTrack`, `AddSpecialPregap`, `AddSubcodeRWGenerator`, `TrackInfo`, `DisableGaplessAudio`, `MediaCatalogNumber`, nothing for text. | `Get-Member` on the COM objects, Windows 11 Pro 10.0.26200 |
| Could option 2 work? | Unknown. `AddSubcodeRWGenerator` takes caller-made R-W subcode "for the number of sectors in the raw disc image" (Microsoft Learn); whether that reaches the lead-in, where CD-Text lives, is not documented. It would also mean writing our own CD-Text pack encoder with CRCs. | Microsoft Learn, `IRawCDImageCreator::AddSubcodeRWGenerator` |
| Is cdrdao maintained, with Windows support? | Yes. Release 1.2.6 (2025-12-05) lists "Windows native interface support". `dao/ScsiIf-nt.cc` opens the drive with `GENERIC_READ \| GENERIC_WRITE` and sends commands with `IOCTL_SCSI_PASS_THROUGH_DIRECT`. Upstream also publishes `cdrdao126.zip`, an MSYS2 build (`cdrdao.exe` plus `msys-2.0.dll` and three other runtime DLLs). | GitHub release `rel_1_2_6` and source |
| Does that drive access need administrator rights? | No. As a normal user, opening `\\.\D:` with read and write access and sending a read-only SCSI INQUIRY through `IOCTL_SCSI_PASS_THROUGH_DIRECT` returned status 0 and "Slimtype eBAU108 7 L". With read access only, Windows refused (error 5). | Spike script on the owner's USB writer, not elevated |
| Is the TOC format suitable? | Yes. `CD_DA`, a global `CD_TEXT { LANGUAGE_MAP { 0 : EN } LANGUAGE 0 { TITLE … PERFORMER … } }`, then per track `TRACK AUDIO`, `CD_TEXT { LANGUAGE 0 { … } }`, `PREGAP 0:2:0`, `FILE "x.wav" 0`. Strings take `\"`, `\\` and `\ooo` escapes; if TITLE or PERFORMER is set anywhere, it must be set for the disc and every track. | `cdrdao.1` and `trackdb/TocParser.g` at `rel_1_2_6` |

| Does cdrdao accept the TOC RetroBurn writes? | Yes, on Linux CI with Ubuntu 24.04's cdrdao 1.2.4: `show-toc` lists both tracks with one 2-second gap each (`PREGAP 00:02:00(150)`, the first track included) and ends at 976 blocks, exactly 2 × 150 plus the two tracks' sectors. That version does not print CD-Text in `show-toc`, and it predates 1.2.6's UTF-8 handling, so the CD-Text bytes themselves are checked only on a real disc. | `AudioToolsIntegrationTest` |

Still open:

1. Build cdrdao 1.2.6 from source in Windows CI (MSYS2) and check that our
   TOC files parse (`cdrdao show-toc`). Running the upstream zip on the
   owner's machine was not done: binaries from outside our build are not
   run there.
2. Burn a CD-R with CD-Text through that build, without elevation, on the
   owner's USB writer; read the CD-Text back on Linux
   (`cdrskin -v dev=… cdtext_to_v07t=…`) or on a CD-Text player.
3. Check that cdrdao can be cancelled within the app's limits.

## Decision outcome

Proposed: **option 1**, if steps 1 to 3 succeed. Audio CDs without CD-Text,
and all audio CDs until the spike ends, use IMAPI2 track-at-once (2-second
gaps, no CD-Text). If the spike fails, **option 3** applies; the owner must
approve that outcome. Option 2 is not pursued: it depends on undocumented
behaviour and a home-made encoder that only a real disc could test.

### Consequences

- Good: CD-Text on both systems, from the same track and text model.
- Good: no administrator rights, confirmed for the drive access cdrdao uses.
- Bad: one more bundled tool on Windows, with its runtime DLLs, licence
  texts, published source and CI build to maintain.
- Bad: two Windows burn paths for audio (cdrdao with CD-Text, IMAPI2
  without), so both need tests.

## More information

- cdrdao: <https://github.com/cdrdao/cdrdao>, manual `dao/cdrdao.man`.
- libburn CD-Text notes (`doc/cdtext.txt`) and the cdrskin manual for the
  Linux side; see [LLD-audio-cd.md](../design/LLD-audio-cd.md).
