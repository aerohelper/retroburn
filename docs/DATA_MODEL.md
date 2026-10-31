<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Data model and file formats

Every file RetroBurn writes carries a schema version, so older files keep
loading after an upgrade and newer files are refused clearly.

| File | Format | Where | Since |
|---|---|---|---|
| `settings.properties` | Java properties, UTF-8 | settings folder | M2 |
| `credits/components.json` | JSON | bundled resource | M1 |
| `.rbproj` project | JSON | where the user saves it | planned |
| `.rbdesign` cover design | ZIP: JSON + images | where the user saves it | planned |
| Burn session | JSON | data folder | planned |

Folders are resolved per OS by `AppPaths` (see
[LLD-engine-and-platform.md](design/LLD-engine-and-platform.md)).

## Versioning rules

- JSON documents have an integer `schemaVersion`. Loading an older version
  runs ordered migrations (`Migration_v1_to_v2`, …), each with tests. Loading
  a newer version fails with "This file was created by a newer version of
  RetroBurn".
- `settings.properties` is more forgiving because it must never stop the app
  from starting: unknown keys are ignored, invalid values fall back to their
  defaults, and `schemaVersion` is written for future migrations.
- Paths inside project files are stored relative to the project file where
  possible.

## `settings.properties` (schema version 1)

| Key | Type | Default | Meaning |
|---|---|---|---|
| `schemaVersion` | integer | `1` | Format version. |
| `burn.preventSleep` | `true`/`false` | `true` | Keep the computer awake while writing a disc. |
| `jobs.keepTemporaryFiles` | `true`/`false` | `false` | Keep job work folders after success or cancel. |
| `jobs.workDirectory` | path | (platform work folder) | Where job work folders are created. |
| `tools.path.<tool>` | path | (search) | User-chosen executable; `<tool>` is `xorriso`, `cdrskin`, `genisoimage`, `ffmpeg`, `ffprobe`, `dvdauthor` or `spumux`. Ignored if the file no longer exists. |

The file is written to a temporary file and then moved into place, so a crash
cannot leave it half-written.

## `credits/components.json` (schema version 1)

Described in [LLD-credits.md](design/LLD-credits.md). Unknown fields are
rejected so typos fail the build rather than silently dropping data.
