<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Architecture Decision Records

Each significant decision gets one short record in this folder, using the
[MADR](https://adr.github.io/madr/) structure (see [template.md](template.md)).
Records are numbered and never deleted; a decision that changes gets a new
record that supersedes the old one.

| # | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-project-license-gpl-3-0-or-later.md) | License RetroBurn under GPL-3.0-or-later | Accepted |
| [0003](0003-java-lts-and-javafx.md) | Java 25 LTS, OpenJFX and Maven | Accepted |
| [0004](0004-external-tools-as-separate-processes.md) | Use external tools as separate processes | Accepted |
| [0005](0005-windows-imapi2-bridge.md) | Reach IMAPI2 through a PowerShell helper | Accepted |
| [0006](0006-external-tool-provisioning-per-os.md) | Provide external tools per OS | Proposed |
| [0007](0007-package-root.md) | Java package root `io.aerohelper.retroburn` | Accepted |
| [0008](0008-hand-wired-composition-root.md) | Hand-wired composition root, no DI framework | Accepted |
| [0009](0009-platform-isolation-enforced-by-archunit.md) | Platform isolation enforced by ArchUnit | Accepted |
| [0010](0010-classpath-application-on-jlinked-javafx-runtime.md) | Classpath application on a jlinked JavaFX runtime | Accepted |
| [0011](0011-jackson-3-for-json.md) | Jackson 3 for JSON | Accepted |
| [0012](0012-formatting-and-header-checks.md) | Automated formatting, licence headers and coverage in the build | Accepted |
| [0013](0013-data-discs-burned-from-an-image.md) | Data discs are burned from an image and verified against it | Proposed |
| [0014](0014-cd-text-on-windows-through-cdrdao.md) | CD-Text on Windows through cdrdao | Proposed |

Statuses: **Proposed** (awaiting owner review or a spike), **Accepted**,
**Superseded by NNNN**, **Rejected**.
