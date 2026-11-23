<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Build and release engineering

| | |
|---|---|
| Status | Draft — packaging and release workflows are added in the release milestone |

## 1. Continuous integration (`.github/workflows/ci.yml`)

Runs on every push to `main` and every pull request.

| Job | Runner | Steps |
|---|---|---|
| Build and test | `ubuntu-latest`, `windows-latest` | Temurin JDK 25 → `mvnw -B verify` (compile, unit and architecture tests, formatting and SPDX headers, credits completeness, coverage) |
| Licence and repository checks | `ubuntu-latest` | `reuse lint`; fail if a private planning document is in the tree |

Planned additions:

- Linux: install `xorriso cdrskin ffmpeg dvdauthor genisoimage` and generate
  synthetic test media before `verify` (core milestone).
- Windows: download the CI-built tools from `windows-tools.yml` artefacts
  before `verify` (Windows engine and video DVD milestones).
- Package artefacts (`.deb`, `.msi`) on every run (release milestone).

## 2. Packaging per OS (planned)

Both packages are built on their own OS's runner. Per
[ADR-0010](adr/0010-classpath-application-on-jlinked-javafx-runtime.md),
`jlink` builds a runtime with the needed JDK modules plus the JavaFX modules
(from the JavaFX jmods), and `jpackage` adds the application jars.

| | Linux | Windows |
|---|---|---|
| Format | `.deb` (`.rpm` desirable) | `.msi` + portable `.zip` (app image) |
| Tool | `jpackage --type deb` | `jpackage --type msi` (needs the WiX Toolset version the JDK's jpackage requires) |
| External tools | Declared with `--linux-package-deps` (package names verified on Ubuntu and Debian) | Bundled under the install directory |
| Extras | Desktop entry, original icon, licence files | Start-menu shortcut, licence files, source-location notice |

Every package must contain the JDK and JavaFX `legal/` files; this is checked
in the built `.deb` and `.msi` before each release.

## 3. Windows code signing

Unsigned installers trigger SmartScreen warnings. A free code-signing
programme for open-source projects will be evaluated and recorded as an ADR.
Until then the README explains the warning.

## 4. Bundled-binary compliance checklist (every Windows release)

- [ ] Each bundled binary and DLL listed in `THIRD_PARTY_NOTICES.md`,
      `components.json` and the SBOM, with version and licence.
- [ ] Licence texts and notices of every bundled binary and its DLL
      dependencies shipped in the installer.
- [ ] Exact corresponding source (tarballs, patches, build scripts and
      configuration) attached to the GitHub Release.
- [ ] FFmpeg's configure line recorded; `--enable-nonfree` not used; licence
      stated accordingly (LGPL or GPL).
- [ ] Binaries built by `windows-tools.yml` from pinned versions and checksums,
      or a recorded exception (ADR) with pinned version, checksum and archived
      source.
- [ ] About screen and `THIRD_PARTY_NOTICES.md` point to the source location.

## 5. Release steps (planned)

1. Update `CHANGELOG.md`: move "Unreleased" entries under the new version.
2. Set the version in `pom.xml` (drop `-SNAPSHOT`), commit with sign-off.
3. Tag `vX.Y.Z` and push the tag.
4. `release.yml` builds `.deb`, `.msi`, `.zip`, SHA-256 checksums, a CycloneDX
   SBOM and the source archives of bundled Windows binaries, and attaches them
   to a GitHub Release with notes from the changelog.
5. Install each package on a clean machine and run the smoke tests from the
   manual checklist.
6. Bump to the next `-SNAPSHOT` version.
