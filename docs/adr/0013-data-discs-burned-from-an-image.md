<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0013 — Data discs are burned from an image and verified against it

- Status: Proposed
- Date: 2026-11-01
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

A data disc can be written in two ways: stream the files straight to the
drive (xorriso `-map`/`-path_list` onto `/dev/sr0`; IMAPI2 `Write` on an
IMAPI2FS stream), or first write an ISO image into the job's work folder and
then burn that image. Verification can likewise be done per file (xorriso's
MD5 tags on Linux, reading every file back and hashing it on Windows) or by
comparing the whole disc with the image. Which way does the data disc
project use, on both systems?

## Decision drivers

- One code path on both systems, already built and tested on a real disc in
  the Windows engine milestone (`buildDataImage`, `burnImage`,
  `verifyImage`).
- A verification that proves the disc holds exactly what was meant, with a
  clear "fails at byte N" answer.
- Free space in the work folder: an image needs as much as the disc holds
  (4.7 GB for a DVD, 25 GB for a single-layer BD).
- Burn reliability: a drive starved of data mid-burn ruins a write-once disc;
  reading an image file is steadier than walking thousands of small files.

## Considered options

1. **Image first:** build the image in the work folder, burn it, compare the
   disc with it byte for byte, delete it.
2. **Stream the tree:** write straight to the drive; verify with xorriso MD5
   tags on Linux and per-file SHA-256 read-back on Windows.
3. Image first on Windows, streamed on Linux.

## Decision outcome

Chosen option: "Image first", because it reuses one tested path on both
systems, needs no second verification method, and the byte-for-byte check is
stronger than per-file hashes: it also covers directory structures, names and
the Joliet and UDF data. The image builder's own correctness is covered by
the integration tests that compare a mounted or extracted image with its
source tree.

### Consequences

- Good: identical behaviour and messages on Linux and Windows; the burn reads
  one sequential file; verify reports the first differing byte.
- Good: the same steps serve "Burn ISO" (minus the build) and "Create ISO"
  (minus the burn).
- Bad: the work folder needs free space for the whole image. The pre-check
  blocks the burn with a message naming the folder and the space needed; the
  work folder can be moved in the settings.
- Bad: building the image adds time before writing starts (about a minute
  for a full DVD on a typical laptop SSD; measured with the owner's machine
  in the data-burning milestone).
- Bad: no multisession ("leave the disc open to add files later") in v0.1.
  Appending a session needs the existing session's layout and is a separate
  feature; data discs are closed. Revisit with the owner if wanted.

## More information

- Engine contract: `docs/design/LLD-engine-and-platform.md` section 1.1.
- Real-disc results: `docs/design/LLD-windows-platform.md` sections 5.4–5.6.
- Linux read-back: `docs/design/LLD-linux-platform.md`, "Verify".
- Steps and pre-checks: `docs/design/LLD-data-burning.md`.
