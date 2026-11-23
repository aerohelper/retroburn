<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0019 — Package layout, and which Windows tools are bundled

- Status: Proposed
- Date: 2026-11-08
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

ADR-0010 planned JavaFX inside the jlinked runtime, which needs the JavaFX
`jmods` (not published to Maven Central). ADR-0006 planned bundling
FFmpeg, dvdauthor and spumux (and cdrdao if adopted) on Windows, built from
pinned source. M11 has to make both concrete.

## Decision drivers

- Only artefacts we can fetch reproducibly (Maven Central, pinned
  tarballs, MSYS2 packages with published sources).
- JavaFX in its supported configuration (module path).
- Ship only tools the app actually runs; publish the exact corresponding
  source for each binary and DLL.

## Decision outcome

Proposed:

1. **JavaFX on the module path from the app folder.** `jlink` builds a
   runtime with only JDK modules; JavaFX's platform jars from Maven Central
   are added with `jpackage --app-content` and put on the module path by
   the launcher (`--module-path=$APPDIR/../javafx --add-modules
   javafx.controls`). This refines ADR-0010 (still a classpath app on a
   jlinked runtime; JavaFX stays on the module path) without the `jmods`
   download.
2. **Bundled on Windows in v0.1: FFmpeg, ffprobe, dvdauthor, spumux.**
   FFmpeg is built from its pinned release tarball with only the features
   RetroBurn uses (`--disable-autodetect`, libx264, LAME, zlib), which
   makes it GPL-2.0-or-later. The DLLs the tools need (libx264, LAME,
   libxml2, FreeType, fontconfig, fribidi, …) are MSYS2's prebuilt packages:
   ADR-0006's fallback option, recorded here. For each, the release
   publishes the MSYS2 source package and the licence files.
3. **cdrdao is not bundled in v0.1**: the app does not run it yet (CD-Text
   on Windows waits for ADR-0014's real-disc steps).

### Consequences

- Good: everything comes from pinned or published sources; no manual
  downloads; JavaFX stays supported.
- Bad: the DLLs are MSYS2 builds, not ours; their versions move with MSYS2
  unless the release pins the package versions it recorded in
  `tools/manifest.json`.
- The Windows package grows by the size of FFmpeg and its DLLs (expected
  tens of MB).

## Findings so far (2026-11-08)

- JavaFX from `--app-content` lands beside the `app` folder on Windows
  (`RetroBurn\javafx`) and on Linux (`/opt/retroburn/lib/javafx`), so
  `--module-path=$APPDIR/../javafx` works on both; the Windows app image
  started without warnings.
- The release dry run built the `.deb`, `.msi`, portable `.zip` and SBOM;
  the bundled tools need 23 DLLs from 21 MSYS2 packages, whose licence
  fields and source packages are recorded (LLD-packaging section 6).
- Installing the `x264` package pulls in MSYS2's own FFmpeg and some 140
  packages; the build uses the `libx264` library package instead.
