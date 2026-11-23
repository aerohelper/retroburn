<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# RetroBurn documentation

**Using RetroBurn?** Read the [user guide](USER_GUIDE.md).

**Working on RetroBurn?** Start with the Solution Architecture Document for the
big picture, then the High-Level Design for how the code is organised, then the
low-level design of the module you want to change. Each module's design was
written before the module was built and updated after it.

| Document | What it answers |
|---|---|
| [USER_GUIDE.md](USER_GUIDE.md) | How to install RetroBurn, burn each kind of disc, make covers and reels, and fix common problems. |
| [architecture/SAD.md](architecture/SAD.md) | What RetroBurn is, who it is for, what it must do, how it is deployed, how it stays license-compliant. |
| [architecture/HLD.md](architecture/HLD.md) | Layers, modules, data flow per project type, platform abstraction, pipeline, threading and error handling. |
| [adr/](adr/) | Architecture Decision Records — why things are the way they are. |
| [EXTENSIBILITY.md](EXTENSIBILITY.md) | How to add a platform, project type, theme, template or reel scene. |
| [PORTING.md](PORTING.md) | How to port RetroBurn to another operating system, step by step. |
| [SECURITY_THREAT_MODEL.md](SECURITY_THREAT_MODEL.md) | What can go wrong security-wise and how the design prevents it. |
| [RISKS.md](RISKS.md) | Project risk register. |
| [GLOSSARY.md](GLOSSARY.md) | Optical-disc and project terms (SAO, CD-Text, UDF, IFO, …). |
| [ux/UX_SPEC.md](ux/UX_SPEC.md) | Screens, flows, wording, theme tokens and accessibility rules. |
| [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) | Set up, build, run and test on Linux and Windows. |
| [TEST_STRATEGY.md](TEST_STRATEGY.md) | Test levels, fixtures policy, coverage and hardware matrix. |
| [MANUAL_TEST.md](MANUAL_TEST.md) | Manual checklist for the screens, real discs and USB behaviour on both systems. |
| [RELEASE.md](RELEASE.md) | CI, packaging per OS, bundled-binary compliance, release steps. |
| [design/LLD-app.md](design/LLD-app.md) | Application shell: start-up, composition root, home screen, platform selection. |
| [design/LLD-credits.md](design/LLD-credits.md) | Third-party component register and the About / Licenses screens. |
| [design/LLD-tools.md](design/LLD-tools.md) | Running external programs, tool detection and feature gating. |
| [design/LLD-pipeline.md](design/LLD-pipeline.md) | Jobs as weighted, cancellable steps; work folders; keeping the computer awake. |
| [design/LLD-engine-and-platform.md](design/LLD-engine-and-platform.md) | Engine contracts and the platform services interface. |
| [design/LLD-linux-platform.md](design/LLD-linux-platform.md) | Linux: xorriso, sysfs and systemd-inhibit, with what was verified and how. |
| [design/LLD-windows-platform.md](design/LLD-windows-platform.md) | Windows: the IMAPI2 helper, drive watching and power, with what was verified and how. |
| [design/LLD-data-burning.md](design/LLD-data-burning.md) | Data discs, Burn ISO, Create ISO and erasing: models, checks, jobs and screens. |
| [design/LLD-audio-cd.md](design/LLD-audio-cd.md) | Audio CDs: CD-Text rules, FFmpeg conversion, cdrskin and IMAPI2 burning. |
| [design/LLD-mp3-disc.md](design/LLD-mp3-disc.md) | MP3 discs: car stereo naming, layouts, conversion and playlists. |
| [design/LLD-video-dvd.md](design/LLD-video-dvd.md) | Video DVDs: TV standards, fit-to-disc bitrate, menus, dvdauthor and images. |
| [design/LLD-cover-designer.md](design/LLD-cover-designer.md) | Covers and labels: templates, documents, undo, fonts, rendering, PDF and PNG export, printing. |
| [design/LLD-themes.md](design/LLD-themes.md) | Themes, generated sounds and the spinning disc. |
| [design/LLD-burn-reel.md](design/LLD-burn-reel.md) | Burn sessions and the burn reel: timeline, scenes, soundtrack and FFmpeg encoding. |
| [design/LLD-packaging.md](design/LLD-packaging.md) | Packages, the bundled Windows tools and the release workflow. |
| [PRINT_TEMPLATES.md](PRINT_TEMPLATES.md) | Cover and label sizes, their sources and test-print results. |
| [DATA_MODEL.md](DATA_MODEL.md) | File formats and schema versions. |

Diagrams are written in Mermaid so they render on GitHub and can be edited as
plain text.
