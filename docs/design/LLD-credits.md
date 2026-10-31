<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Open-source credits (`credits`, About and Licenses screens)

| | |
|---|---|
| Status | Implemented in the skeleton milestone (M1) |
| Packages | `io.aerohelper.retroburn.credits`, `.ui` (dialogs) |
| Related | [SAD §8](../architecture/SAD.md), `THIRD_PARTY_NOTICES.md` |

## 1. Purpose

Give every upstream project visible credit and ship every licence text with
the app, offline. One data file drives the in-app screens and is kept in sync
with `THIRD_PARTY_NOTICES.md`; a test fails the build when a runtime dependency
has no entry or an entry has no licence text.

## 2. Data file: `credits/components.json`

```json
{
  "schemaVersion": 1,
  "components": [
    {
      "id": "jackson",
      "name": "Jackson",
      "role": "Reads and writes JSON files",
      "authors": "FasterXML, LLC and contributors",
      "license": "Apache-2.0",
      "homepage": "https://github.com/FasterXML/jackson",
      "provision": "BUNDLED_LIBRARY",
      "platforms": ["LINUX", "WINDOWS"],
      "licenseFiles": ["Apache-2.0.txt"],
      "noticeFiles": ["jackson-NOTICE.txt"],
      "mavenArtifacts": ["tools.jackson.core:jackson-databind", "..."]
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `schemaVersion` | Integer; migrations added if the shape changes. |
| `id` | Stable identifier, lower-case with dashes. |
| `name`, `role`, `authors`, `homepage` | Shown in the Licenses screen. Names spelled as upstream does ("FFmpeg", "GNU xorriso"). |
| `license` | SPDX licence expression. |
| `provision` | `BUNDLED_RUNTIME`, `BUNDLED_LIBRARY`, `BUNDLED_TOOL`, `OS_PACKAGE`, `PART_OF_OS`, `BUNDLED_ASSET`. Drives the "how provided" line. |
| `platforms` | `LINUX`, `WINDOWS` — which OSes the component is used on. |
| `licenseFiles` | File names under `credits/licenses/`, shown in order. |
| `noticeFiles` | File names under `credits/notices/`: Apache `NOTICE` files and upstream third-party notices. May be empty. |
| `mavenArtifacts` | `groupId:artifactId` pairs this entry covers; used by the completeness test. Empty for non-Maven components. |

Licence and notice texts are copied verbatim from the component itself (from
its jar's `META-INF`, its source repository at the released tag, or the JDK's
`legal/` folder), not paraphrased and not taken from a generic template. Their
licensing for REUSE purposes is declared in `REUSE.toml`.

## 3. Classes

| Class | Responsibility |
|---|---|
| `ThirdPartyComponent` (record) | One entry, as above. |
| `Provision` (enum) | How the component reaches the user. |
| `ComponentRegistry` | Loads `components.json` with Jackson (unknown fields rejected so typos fail the build); `componentsFor(OperatingSystem)` returns entries for that OS sorted by name, or every entry for an unsupported OS; `licenseText(component)` and `noticeText(component)` read and join the bundled texts. |
| `ui.AboutDialog` | Name, version, copyright, "Licensed under the GNU General Public License, version 3 or later", source URL (selectable text — the app makes no network calls), and the no-warranty statement. |
| `ui.LicensesDialog` | List of components on the left; on the right: name, role, authors, licence, homepage, how provided, and the full licence and notice text in a scrollable, selectable text area. |

## 4. Completeness test (`CreditsCompletenessTest`)

1. During `generate-test-resources`, `maven-dependency-plugin:list` writes the
   runtime-scope dependencies to `target/runtime-dependencies.txt`.
2. The test parses that file and asserts that every `groupId:artifactId` is
   listed in some component's `mavenArtifacts`.
3. It asserts that every `licenseFiles` and `noticeFiles` entry exists on the
   classpath and is not empty. Duplicate `id`s are rejected when the register
   is loaded.

From M2 on, the test also covers external tools; from M7, every bundled
Windows binary.

## 5. Verified licence facts (M1)

| Component | Checked against | Result |
|---|---|---|
| OpenJFX 25.0.5 | `LICENSE`, `ASSEMBLY_EXCEPTION`, `ADDITIONAL_LICENSE_INFO` at tag `25.0.5+1` of `openjdk/jfx25u`; the Maven jars carry no licence files | GPL-2.0-only WITH Classpath-exception-2.0. `javafx.graphics` has third-party notices (`jpeg_fx.md`, `mesa3d.md`, `pipewire.md`), shipped as notices. |
| OpenJDK 25 runtime | `legal/java.base/` of the Temurin 25.0.4 JDK | GPL-2.0-only WITH Classpath-exception-2.0. Per-module third-party notices stay in the runtime image's `legal/` folder; the exact module set is checked when packaging. |
| jackson-databind 3.2.4, jackson-annotations 2.22 | `META-INF/LICENSE` and `META-INF/NOTICE` in the jars | Apache-2.0 (identical licence files) |
| jackson-core 3.2.4 | `META-INF/LICENSE`, `NOTICE`, `FastDoubleParser-LICENSE`, `FastDoubleParser-ThirdParty-LICENSE`, `Schubfach-LICENSE` in the jar | Apache-2.0, plus bundled FastDoubleParser (MIT, including code under BSL-1.0, MIT and BSD-2-Clause) and Schubfach (MIT). All GPL-3.0-compatible. |

## 6. How to change this safely

- Adding a dependency or bundled binary: first add the entry to
  `components.json`, the licence texts under `credits/licenses/` (and notices
  under `credits/notices/`), their licensing in `REUSE.toml`, and the same row
  in `THIRD_PARTY_NOTICES.md`. Then add the dependency. `mvnw verify` fails if
  a runtime dependency is not registered; `reuse lint` fails if a text file's
  licensing is not declared.
- Look inside the dependency's jar for bundled third-party code (shaded
  libraries bring their own licences, as jackson-core does).
- Updating a dependency's version: re-check its licence and notice files and
  replace the copies if they changed.
- Never paraphrase licence texts; copy them verbatim.
- Do not add logos. Names only.
