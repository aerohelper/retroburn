<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Risk register

Reviewed at the end of every milestone. Likelihood and impact: Low / Medium /
High.

| # | Risk | Likelihood | Impact | Mitigation | Owner | Status |
|---|---|---|---|---|---|---|
| R1 | **No CD-Text on Windows.** IMAPI2 has no documented CD-Text API; a bundled cdrdao may not work with all drives or under a standard user. | High | Medium | Spike in the audio CD milestone; if it fails, ship Windows audio CDs without CD-Text, with the option shown disabled and explained (owner approval required). | Owner + dev | Open |
| R2 | **Windows DVD-Video images rejected by players.** IMAPI2FS UDF 1.02 + ISO 9660 output may not satisfy strict hardware DVD players. | Medium | High | Validate on VLC and at least one hardware player in the video DVD milestone; fallback options (e.g. a bundled image builder) recorded as an ADR if needed. | Dev | Open |
| R3 | **Bundled-binary compliance gaps.** Missing licence texts, wrong FFmpeg configuration, unpublished source for DLL dependencies. | Medium | High | Build tools in CI from pinned source; SBOM; credits-completeness test; release checklist in `docs/RELEASE.md`. | Dev | Open |
| R4 | **Drive quirks.** Drives differ in reported capabilities, CD-Text support, speeds and error behaviour. | High | Medium | Gate features on reported capabilities; capture real output from several drives as fixtures; manual test matrix with the owner's drives. | Dev | Open |
| R5 | **USB power and sleep interruptions.** Bus-powered USB drives lose power; laptops sleep mid-burn. | Medium | High | Sleep inhibition during burns; USB tips; battery warning; clean drive-lost handling with bounded timeouts; manual unplug tests on rewritable media only. | Dev | Open |
| R6 | **SmartScreen warnings on unsigned installers** discourage users. | High | Medium | Evaluate free code-signing for open-source projects (ADR); document the warning in the README until signed. | Owner | Open |
| R7 | **PowerShell helper not viable** (unreliable COM progress events, AV blocking, execution-policy issues). | Medium | Medium | Spike done (ADR-0005): progress events work from C# sinks; execution policy set per process. Remaining parts tracked in R16 and R17. | Dev | Closed |
| R8 | **Drive-less ISO creation impossible on Windows** via IMAPI2FS. | Low | Medium | Verified in the Windows engine spike: an IMAPI2FS image stream saved to a file mounts and matches the source tree. | Dev | Closed |
| R9 | **Linux tool output changes between distro versions** and breaks parsers. | Medium | Medium | Log tool versions; fixtures from Ubuntu 22.04/24.04 and Debian 12; parsers fail with full output rather than guessing. | Dev | Open |
| R10 | **jlink/jpackage with non-modular libraries** complicates packaging. | Medium | Low | Decide module strategy with an ADR in the skeleton milestone; package early in CI. | Dev | Open |
| R11 | **Scope size.** Twelve milestones across two OSes delay a first release. | High | Medium | Owner decides whether to release an earlier subset (data + audio + MP3) first. | Owner | Open |
| R12 | **Limited test hardware.** One USB drive may not support every feature (BD, DL, CD-Text). | High | Medium | Record the drive's spec; mark untestable features clearly in the manual test checklist. | Owner | Open |
| R13 | **Print alignment varies by printer and paper.** | Medium | Low | Alignment test page; documented test prints; print-at-100 % reminder. | Owner | Open |
| R14 | **Large Windows installer** (est. 150–250 MB) due to bundled FFmpeg/dvdauthor. | High | Low | Accept, or reduce with a trimmed FFmpeg build containing only needed codecs. | Owner | Open |
| R15 | **Repository licence mismatch.** The initial `LICENSE` contained AGPL-3.0, not the intended GPL-3.0-or-later. | — | Medium | Owner replaced `LICENSE` with the GPL-3.0 text; ADR-0002 accepted. | Owner | Closed |
| R16 | **Antivirus false positives** on bundled tools or the PowerShell helper. | Medium | Medium | Build reproducibly; submit false-positive reports; consider signing. Microsoft Defender did not interfere with the helper in the spike. | Dev | Open |
| R17 | **Windows helper blocked by policy**: execution policy set by Group Policy, or application control enforcing PowerShell Constrained Language Mode (no `Add-Type`). | Low | High | Detected when the helper fails to start; burning features show the reason instead of failing silently. No workaround that weakens policy. | Dev | Open |
