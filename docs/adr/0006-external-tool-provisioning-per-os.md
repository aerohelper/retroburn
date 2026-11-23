<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0006 — Provide external tools per OS

- Status: Proposed
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

RetroBurn needs FFmpeg, ffprobe, dvdauthor and spumux on both OSes; on Linux
also xorriso, cdrskin and genisoimage; on Windows possibly cdrdao for CD-Text.
How do these get onto the user's machine, and what obligations follow?

## Decision drivers

- Linux users expect dependencies from their distribution, with security
  updates.
- Windows has no package manager the app can rely on; users expect a single
  installer.
- Redistributing GPL/LGPL binaries brings source and notice obligations.
- We must be able to prove the source we publish matches the binaries we ship.

## Considered options

1. Linux: distribution packages; Windows: bundle binaries **built in our CI from
   pinned source**
2. Linux: distribution packages; Windows: bundle **third-party prebuilt**
   binaries (pinned version + checksum, source archived)
3. Ask Windows users to install tools themselves
4. Bundle on both OSes

## Decision outcome

Chosen option: **1**, with option 2 as an explicitly recorded fallback per tool
if building it ourselves proves impractical.

**Linux**
- Tools are declared as `.deb` package dependencies and never bundled.
- Settings show detected versions and an install hint.

**Windows**
- FFmpeg, dvdauthor/spumux (and their DLLs), and cdrdao if adopted, are built
  by a dedicated CI workflow (MSYS2/MinGW) from pinned source versions and
  checksums kept in `windows-tools/`.
- Every release:
  1. ships each bundled binary's licence texts and notices, including those of
     its DLL dependencies as actually built;
  2. publishes the exact corresponding source and build scripts as release
     assets, and says where in the About screen and `THIRD_PARTY_NOTICES.md`;
  3. records FFmpeg's configure line — never `--enable-nonfree`; whether
     `--enable-gpl` is used (e.g. for libx264) is recorded and determines the
     licence stated;
  4. includes a CycloneDX SBOM of all bundled components.
- Bundled tools live in the read-only install directory and are run by
  absolute path; users may override paths in settings.

**CD-Text on Windows**
- IMAPI2 has no documented CD-Text API. The audio CD milestone includes a spike
  evaluating a bundled cdrdao build. If it fails, Windows audio CDs ship without
  CD-Text in v0.1, with the option shown disabled and explained — this outcome
  requires the owner's approval.

### Consequences

- Good: Linux follows distribution norms; Windows works out of the box.
- Good: building from source makes the corresponding-source obligation
  provable.
- Bad: the Windows installer is large (estimated 150–250 MB).
- Bad: maintaining the Windows tool build is ongoing work; security updates to
  bundled tools require a RetroBurn release.
