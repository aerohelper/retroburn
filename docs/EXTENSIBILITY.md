<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Extending RetroBurn

| | |
|---|---|
| Status | Draft — contracts firm up as each module is built; final review before v0.1.0 |
| Background | [HLD §4 and §14](architecture/HLD.md) |

RetroBurn has a small number of deliberate extension points. Each one has a
contract, a stability promise and a worked example below.

**Stability promise (applies to all):** after v0.1.0, a breaking change to an
extension-point interface or data format needs an ADR and a note in the
changelog. Data files carry a `schemaVersion` and old versions keep loading
through migrations.

---

## 1. A new operating system — `PlatformServices`

**Contract:** implement `PlatformServices` and the engines it returns in a new
`platform.<os>` package. Nothing outside that package may change except one
line in `AppContext` that selects it.

| You provide | Notes |
|---|---|
| `AppPaths` | The OS's conventional folders for settings, data, logs, cache, videos. |
| `ToolLocator` | Where FFmpeg, ffprobe, dvdauthor, spumux live (bundled or system). |
| `BurnEngine` | Drive listing, media info, burn image, blank, verify, eject. (Data discs are burned from an image, ADR-0013.) |
| `DiscImageBuilder` | Data ISO and DVD-Video image (ISO 9660 + UDF 1.02). |
| `AudioCdEngine` (optional) | Return empty if unsupported; Audio CD is then disabled with an explanation. |
| `DriveWatcher` | Hot-plug; must pause during burns. |
| `SleepInhibitor` | Must release on success, failure, cancel and crash. |
| `PowerStatus` | May answer "unknown". |

**Worked example (macOS, sketch):** `platform.macos.MacPlatform` could use
`drutil` / `hdiutil` for burning and imaging, `caffeinate` for sleep
inhibition and `pmset` for power status — each verified against Apple's
documentation and captured as fixtures first. The architecture tests check
that no other package learned about macOS. A full walkthrough lives in
[PORTING.md](PORTING.md).

---

## 2. A new burning back-end — `BurnEngine`, `AudioCdEngine`, `DiscImageBuilder`

**Contract:** implement the interface inside a platform package; emit
`BurnEvent`s through the listener; honour the `CancelToken`; throw typed
exceptions with a user message; never block forever.

**Worked example:** a faster Linux engine that keeps one xorriso process open
in dialog mode instead of starting a new process per operation. It would be a
second `BurnEngine` in `platform.linux`, selected by `LinuxPlatform`; its parser
gets its own fixtures.

---

## 3. A new disc / project type — `ProjectType` + `PipelineStep`s

**Contract:**

1. A record implementing `Project` (add it to the sealed `permits` list).
2. A `ProjectType` declaring id, supported media, required tools, and how to
   build its `Pipeline`.
3. Its `PipelineStep`s, reusing existing ones where possible.
4. One compilation view in `ui`.
5. One registration line in `AppContext` (`ProjectTypeRegistry`).
6. A `.rbproj` schema entry and migration if it changes the file format.

**Worked example (photo slideshow DVD):** `SlideshowProject` holds images and
durations; `SlideshowProjectType` builds: render slides to video (FFmpeg) →
then reuses the Video DVD steps (menu, spumux, dvdauthor, image, burn, verify).

---

## 4. A theme — `Theme`

**Contract:** a folder `src/main/resources/themes/<name>/` containing:

| File | Purpose |
|---|---|
| `theme.properties` | `name`, `author`, `license`, `soundsDefault` (`true`/`false`), `fonts` (comma-separated resource paths) |
| `theme.css` | JavaFX CSS using the token names in [UX_SPEC.md](ux/UX_SPEC.md); may start with `@import "../classic/theme.css";` and override tokens; styles the disc through `.disc-surface`, `.disc-sheen`, `.disc-sheen-2`, `.disc-hub`, `.disc-hole` |
| `sounds/` | `spin-up.wav`, `burning-loop.wav`, `success.wav`, `error.wav`: PCM WAV, original or CC0 (`tools/sounds/GenerateSounds.java` makes the built-in ones) |

Every asset is listed in `assets/CREDITS.md`. Add the theme's id to
`ThemeRegistry.IDS`; `ThemeContrastTest` and `ThemeFilesTest` then check
it. See [LLD-themes.md](design/LLD-themes.md).

**Worked example:** a `millennium-blue` theme copies `y2k`, changes colour
tokens and the disc's colours, and reuses the `y2k` sounds by copying
them.

---

## 5. A DVD menu template — `MenuTemplate`

**Contract:** a folder under `menu-templates/<name>/` with a data file
describing background, title position, button grid (≤ 6 per page), thumbnail
frame, fonts, and the ≤ 4-colour highlight/select palette for subtitle
overlays. `MenuRenderer` draws any template that follows the schema.

**Worked example:** a seasonal template with a different background image and
a 2×2 button grid.

---

## 6. A print template — `PrintTemplate`

**Contract:** a data file under `print-templates/` giving the item's trim size
in millimetres, bleed, safe zone, fold/spine lines, hole diameters for disc
labels, and how many fit per A4/Letter sheet. Dimensions are verified by test
print and recorded in `docs/PRINT_TEMPLATES.md`.

**Worked example:** a label template for a specific brand of A4 label stock
with two discs per sheet at different offsets.

---

## 7. A burn reel scene — `ReelScene`

**Contract:** a class implementing `ReelScene`:

```java
public interface ReelScene {
    String id();
    void render(Graphics2D graphics, ReelFrame frame, BurnSession session, ReelStyle style);
}
```

`render` must be pure (same inputs → same pixels) so snapshot tests work.
`ReelStyle` carries the theme's colours and fonts, the user-visible text
and the item names (hidden or not). Register the scene with one line in
`ReelTimeline.of`. See [LLD-burn-reel.md](design/LLD-burn-reel.md).

**Worked example:** a "cassette rewind" scene used as an alternative intro.
