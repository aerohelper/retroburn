<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Packaging and release (`packaging/`, `windows-tools/`, workflows)

| | |
|---|---|
| Status | Designed for the release milestone (M11) |
| Files | `packaging/package.sh`, `packaging/sbom-tools.py`, `windows-tools/build-ffmpeg.sh`, `windows-tools/bundle.sh`, `.github/workflows/windows-tools.yml`, `.github/workflows/release.yml`, `tools/icon/GenerateIcon.java` |
| Related | [ADR-0006](../adr/0006-external-tool-provisioning-per-os.md), [ADR-0010](../adr/0010-classpath-application-on-jlinked-javafx-runtime.md), [ADR-0019](../adr/0019-packaging-layout-and-bundled-tools.md), [RELEASE.md](../RELEASE.md) |

## 1. Scope

Release builds produce:

| Artefact | Built on | Contents |
|---|---|---|
| `retroburn_<v>_amd64.deb` | Ubuntu | App image under `/opt/retroburn`, desktop entry, icon; depends on `xorriso`, `cdrskin`, `genisoimage`, `ffmpeg`, `dvdauthor` |
| `RetroBurn-<v>.msi` | Windows | App image with the bundled tools, Start-menu entry, licence page |
| `RetroBurn-<v>-windows-portable.zip` | Windows | The same app image, unzip and run |
| `retroburn-<v>.cdx.json` | Linux | CycloneDX SBOM: Java libraries (Maven) plus the bundled Windows tools and their DLLs |
| `windows-tools-sources-<v>.zip` | Windows | Source tarballs, MSYS2 source packages, patches and build scripts for every bundled binary and DLL |
| `SHA256SUMS` | Linux | Checksums of every asset |

Pull requests that touch packaging build all of these as workflow
artefacts (a dry run). Pushing a tag `v*` builds them again and attaches
them to a GitHub Release; tagging is done by the owner.

## 2. App image (`packaging/package.sh`)

Same steps on both systems (Git Bash on Windows):

1. `./mvnw -B -DskipTests package` and `dependency:copy-dependencies
   -DincludeScope=runtime`.
2. JavaFX's platform jars (`javafx-*-<platform>.jar`) go to `javafx/`; the
   other jars to `lib/`, the app jar renamed `retroburn.jar`.
3. `jlink --add-modules java.base,java.desktop,java.sql,java.xml,
   java.logging,jdk.unsupported,jdk.jfr,jdk.localedata --strip-debug
   --no-header-files --no-man-pages` — the modules `jdeps` reports for the
   app, its libraries and JavaFX's `requires`, plus `jdk.localedata` for
   date and number formats outside English.
4. `jpackage --type app-image --name RetroBurn --input lib --main-jar
   retroburn.jar --main-class io.aerohelper.retroburn.app.Main
   --runtime-image runtime --app-content javafx [--app-content tools]
   --java-options --module-path=$APPDIR/../javafx --java-options
   --add-modules=javafx.controls --java-options
   --enable-native-access=javafx.graphics --icon … --app-version <v>`.
   `--app-content` folders land next to the `app` folder (checked on
   Windows: `RetroBurn/app`, `RetroBurn/javafx`, `RetroBurn/tools`), so
   `$APPDIR/../javafx` finds JavaFX on both systems.
5. Linux: `jpackage --type deb --app-image … --linux-package-deps
   "xorriso, cdrskin, genisoimage, ffmpeg, dvdauthor"
   --linux-shortcut --linux-app-category AudioVideo`.
   Windows: `jpackage --type msi --app-image … --win-menu
   --win-menu-group RetroBurn --win-dir-chooser --win-upgrade-uuid
   <fixed uuid> --license-file LICENSE`, and the app image zipped as the
   portable build.

Licences in every package: the runtime image's `legal/` (JDK modules), the
JavaFX jars' licence files (inside the jars), `LICENSE`,
`THIRD_PARTY_NOTICES.md` and, on Windows, `tools/licenses/`.

## 3. Bundled Windows tools (`windows-tools/`)

| Tool | How | Licence |
|---|---|---|
| FFmpeg, ffprobe | `build-ffmpeg.sh`: pinned FFmpeg release tarball (SHA-256 checked), MSYS2 UCRT64, `--disable-autodetect --enable-gpl --enable-libx264 --enable-libmp3lame --enable-zlib --disable-ffplay --disable-doc`; libx264, LAME and zlib from MSYS2 packages | GPL-2.0-or-later (because of `--enable-gpl` and libx264); configure line recorded |
| dvdauthor, spumux | `build-dvdauthor.sh` (M7) | GPL-2.0-or-later |

`bundle.sh` collects the executables and the DLLs `ldd` reports outside
`C:\Windows`, then for each DLL asks `pacman -Qo` which MSYS2 package owns
it, copies that package's licence files from `/ucrt64/share/licenses/` and
downloads its source package from `repo.msys2.org/mingw/sources/`. It
writes `tools/manifest.json` (tool, version, licence, source file, SHA-256
of each file) that the SBOM script and the credits use.

The app finds bundled tools before `PATH`: on Windows,
`WindowsPlatform` looks in `<app image>/tools/` (the parent folder of the
launcher named by the `jpackage.app-path` system property). User settings
still override both.

## 4. Release workflow (`.github/workflows/release.yml`)

- Triggers: `push` of tags `v*`; `pull_request` touching `packaging/`,
  `windows-tools/` or the workflow (dry run).
- Jobs: `windows-tools` (reuses `windows-tools.yml` via
  `workflow_call`), `windows-package` (needs it), `linux-package`, and
  `publish` (needs both; on tags only: SBOM merge, `SHA256SUMS`,
  `gh release create` with notes from the changelog).

## 5. Icon (`tools/icon/GenerateIcon.java`)

An original drawing (a disc with an orange arc), generated as
`packaging/icons/retroburn.png` (256 px) and `retroburn.ico` (16, 32,
48, 256 px, PNG-compressed entries). Deterministic; credited in
`assets/CREDITS.md`.

## 6. Testing

- The dry run on the pull request: packages build on both systems; the
  workflow lists the `.deb` contents and the app image tree; the Windows
  app image is started headless enough to log its first line (`--version`
  is not offered, so the check is that the launcher and runtime start).
- Manual: install the `.deb` on Ubuntu and the `.msi` on Windows 11 on a
  clean machine; start; check Help → Open-source licenses lists the
  bundled tools; burn the DVD+RW once (RELEASE.md step 5).
