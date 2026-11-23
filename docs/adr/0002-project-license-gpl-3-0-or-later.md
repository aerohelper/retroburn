<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0002 — License RetroBurn under GPL-3.0-or-later

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

RetroBurn drives GNU xorriso (GPL-3.0-or-later), bundles GPL/LGPL binaries on
Windows (FFmpeg, dvdauthor, possibly cdrdao), and uses Apache-2.0 Java
libraries (PDFBox, Jackson). Which licence should RetroBurn's own code use?

The repository was first created with the AGPL-3.0 text in `LICENSE`; the
owner has since replaced it with the unmodified GPL-3.0 text.

## Decision drivers

- Compatibility with every component we use or redistribute.
- Avoid ambiguity when shipping alongside GPL-3.0 tools.
- Keep the app free software for its users.
- Simplicity for contributors.

## Considered options

1. GPL-3.0-or-later
2. AGPL-3.0-or-later (the text currently in `LICENSE`)
3. GPL-2.0-or-later
4. A permissive licence (Apache-2.0 / MIT)

## Decision outcome

Chosen option: "GPL-3.0-or-later", because it matches GNU xorriso's licence,
is compatible with Apache-2.0 libraries (Apache-2.0 is compatible with GPLv3
but not with GPLv2-only), and keeps the app copyleft.

AGPL's extra obligation concerns software used over a network; RetroBurn makes
no network connections, so AGPL adds complexity without benefit. A permissive
licence would be legal (tools run as separate processes) but would allow
closed derivatives of a project intended to stay free.

### Consequences

- Good: no licence ambiguity with xorriso; Apache-2.0 dependencies allowed.
- Good: users and distributors get the familiar GPL terms.
- Bad: GPL-2.0-only code cannot be incorporated into RetroBurn itself (does not
  affect GPL-2.0 tools run as separate processes).
- `LICENSE` holds the GPL-3.0 text; `LICENSES/`, SPDX headers in every source
  file and `REUSE.toml` make the repository REUSE-compliant, and `reuse lint`
  runs in CI. Source files state "GPL-3.0-or-later" in their SPDX header.

## More information

- GNU licence compatibility list: https://www.gnu.org/licenses/license-list.html
- Apache on GPL compatibility: https://www.apache.org/licenses/GPL-compatibility.html
- This is engineering guidance, not legal advice.
