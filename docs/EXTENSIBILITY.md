<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Extending RetroBurn

| | |
|---|---|
| Status | Reviewed for v0.1.0 against the code as built (M11) |
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
| `AppPaths` | The OS's conventional folders for settings, data, logs, work folders and reels (videos). |
| `ToolLocator` | Where FFmpeg, ffprobe, dvdauthor, spumux live (bundled or system); user settings override it. |
| `fontFolders()` | Folders of the computer's fonts, for the cover designer. |
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

## 3. A new disc / project type — model, job factory, `PipelineStep`s, screen

**Contract** (as the four project types are built, see the audio CD, MP3
and video DVD LLDs):

1. A model in `project` (like `AudioCompilation`, `DvdProject`): immutable,
   no JavaFX.
2. A `JobKind` value and a job factory (like `DvdJobFactory`) that builds a
   `Pipeline` of `PipelineStep`s, reusing `BurnJobFactory`'s image, burn and
   verify steps, plus a pre-check (`BurnPreCheck`) for what blocks a burn.
3. The `Feature` it needs, with each platform's `featureRequirements()`, so
   the screen is gated with a reason when tools are missing.
4. One screen in `ui` that runs the job through `BurnFlow` and publishes its
   cover text (`CoverContents`), and one card in `HomeView`.
5. Its user-visible text in `messages.properties` and a section in
   `MANUAL_TEST.md`.

Projects are not saved to files in v0.1; a project file format would come
with its own ADR and `schemaVersion`.

**Worked example (photo slideshow DVD):** a `SlideshowProject` holds images
and durations; its job factory renders the slides to video with FFmpeg,
then reuses `DvdJobFactory`'s menu, spumux, dvdauthor, image, burn and
verify steps.

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

**Contract:** a `MenuTemplate` constant (id, background top and bottom
colours, text colour, highlight and select colours, font family, framed
thumbnails) added to `MenuTemplate.named` and the template choice of the
video DVD screen. Fonts are the bundled ones (`MenuFonts`) or a Java logical
font. The layout (six buttons a page, page buttons) is shared by all
templates in `MenuLayout`; highlight and select overlays use one colour
each, as spumux needs. `VideoRulesTest` checks a new template renders.

**Worked example:** a seasonal template with warm colours and Lato,
selectable as "Holiday".

---

## 6. A print template — `PrintTemplate`

**Contract:** a data file `src/main/resources/print-templates/<id>.json`
(see `DATA_MODEL.md`) giving the trim size in millimetres, bleed, safe zone,
fold lines or an adjustable spine, and hole diameters for disc labels; its id
added to `PrintTemplates.IDS` and its name to `messages.properties`
(`design.template.<id>`). `SheetLayout` works out how many fit on A4 and
Letter; `PrintTemplatesTest` and `SheetLayoutTest` then cover it. Dimensions
are verified by test print and recorded in `docs/PRINT_TEMPLATES.md`.

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
