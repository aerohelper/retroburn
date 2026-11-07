<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Cover and label designer (`design`, screen)

| | |
|---|---|
| Status | Designed for the cover and label milestone (M8) |
| Packages | `io.aerohelper.retroburn.design` (templates, documents, commands, layout, fonts, rendering, PDF/PNG export, `.rbdesign` files; no JavaFX), `.ui` (`CoverDesignerView`, printing), `.platform` (`fontFolders()`) |
| Related | [ADR-0016](../adr/0016-pdf-export-and-fonts.md), [PRINT_TEMPLATES.md](../PRINT_TEMPLATES.md), [HLD §2](../architecture/HLD.md), [EXTENSIBILITY.md §6](../EXTENSIBILITY.md) |

## 1. Scope

The user designs a cover or a disc label and prints it at home, or saves
it as PDF or PNG for a print shop.

- Items: CD jewel case front, CD back tray inlay with two spines, CD slim
  case insert, DVD keep-case wrap with a spine of chosen width, disc label
  with a 41 mm or 17 mm hole.
- Paper: A4 or US Letter (default from the computer's country: Letter for
  the US, Canada, Mexico and the Philippines, A4 elsewhere), crop marks,
  3 mm bleed, safe-zone guides, as many items per sheet as fit.
- Elements: image, text, shape (rectangle, ellipse), track list, disc
  title. Move, resize, rotate, snap with guides, bring forward/back,
  align to the item, zoom, undo/redo (100 steps).
- Fonts: the bundled OFL fonts (Lato, VT323) and the computer's TrueType
  fonts. A font whose licence forbids embedding is marked and cannot be
  exported to PDF.
- Presets `classic`, `y2k`, `minimal` fill a new design.
- Auto-fill from the last project opened in this session (track list, MP3
  albums, DVD titles, data disc name and date), with **Re-sync**.
- Save and open `.rbdesign` files. Export PDF (vector text and shapes,
  embedded fonts, images at full resolution) and PNG at 300 dpi. Print
  with a "print at 100 %" reminder, and an alignment test page.
- A one-time notice on the first image import: use only images you have
  the right to print.

Not in this milestone: multi-selection, free-form paths, text on a curve,
CMYK output, label-stock templates of particular brands (they can be added
as template files, EXTENSIBILITY §6).

## 2. Templates (`design.PrintTemplate`, `design.PrintTemplates`)

Data files under `src/main/resources/print-templates/<id>.json`, read with
Jackson into:

```java
record PrintTemplate(int schemaVersion, String id, Shape shape,
                     double widthMm, double heightMm, double bleedMm, double safeMm,
                     List<Double> foldsMm,       // x of vertical fold/spine lines
                     double spineMm,             // 0 unless the spine is adjustable
                     double outerDiameterMm,     // disc labels only
                     List<Double> holeDiametersMm)
```

| id | Trim (mm) | Folds | Notes |
|---|---|---|---|
| `cd-front` | 120 × 120 | — | |
| `cd-back` | 151 × 118 | 6.5, 144.5 | spines 6.5 mm |
| `cd-slim` | 120 × 120 | — | |
| `dvd-wrap` | 259 + spine × 183 | 129.5, 129.5 + spine | spine 14 mm by default, 5–30 mm |
| `disc-label` | Ø 118 | — | hole 41 mm (default) or 17 mm |

Sources and test-print results are in `docs/PRINT_TEMPLATES.md`. The
numbers are the common ones for these cases; they become "verified" only
after the owner's test print.

Coordinates are millimetres from the item's top-left trim corner, y down.
For a disc label that corner is the corner of the 118 mm square around the
disc.

## 3. Documents (`design.DesignDocument`)

Immutable records; every edit makes a new document.

```java
record DesignDocument(int schemaVersion, String templateId, double spineMm,
                      double holeDiameterMm, String preset, String background,
                      CoverContent content, List<DesignElement> elements)   // z-order: first is bottom
record CoverContent(String kind, String title, String subtitle, List<String> lines, String date)
sealed interface DesignElement permits TextElement, ImageElement, ShapeElement,
                                       TrackListElement, DiscTitleElement {
    String id(); Frame frame();
}
record Frame(double x, double y, double width, double height, double rotationDegrees)
```

Colours are `#RRGGBB` strings. Text elements hold the font family, size in
points, bold, colour and alignment. An image element holds the SHA-256 of
the image bytes; the bytes live in the document's `ImageStore`, not in the
record. Track list and disc title elements draw `content.lines()` and
`content.title()`, so Re-sync changes the text without touching the
layout.

## 4. Editing (`design.EditorCommand`, `design.CommandHistory`)

Each edit is a command: `DesignDocument apply(DesignDocument)` plus a name
for the Undo menu text. `CommandHistory` keeps documents before and after
each command, at most 100 steps; a new command clears redo. Commands with
the same merge key in a row (one drag, typing in one field) become one
undo step.

Commands: `AddElement`, `RemoveElement`, `ReplaceElement` (any property
change, including the frame), `MoveInZOrder`, `AlignToItem`
(left/centre/right/top/middle/bottom), `SetContent` (Re-sync), `SetSpine`,
`SetHole`, `ApplyPreset`.

**Snapping (`design.Snap`).** While an element is dragged or resized,
each of its edges and its centre snaps to the nearest candidate within
1.5 mm at the current zoom: the item's trim edges, centre lines, safe-zone
lines, fold lines, and the edges and centres of the other elements. The
candidates hit are returned as guide lines for the canvas. Holding Alt
turns snapping off for that drag.

## 5. Fonts (`design.FontCatalog`)

- Bundled: `fonts/lato/Lato-Regular.ttf`, `Lato-Bold.ttf`,
  `fonts/vt323/VT323-Regular.ttf` (SIL OFL 1.1, `assets/CREDITS.md`).
- System: `.ttf` files under `PlatformServices.fontFolders()` (Windows:
  `%WINDIR%\Fonts` and `%LOCALAPPDATA%\Microsoft\Windows\Fonts`; Linux:
  `/usr/share/fonts`, `/usr/local/share/fonts`, `~/.local/share/fonts`,
  `~/.fonts`), scanned once in the background. `.ttc`, `.otf` and bitmap
  fonts are skipped (ADR-0016).
- Each font is read with FontBox: family and subfamily from the `name`
  table, `fsType` from the `OS/2` table. Bit 1 (value 2, "Restricted
  License embedding") makes the font non-embeddable; the font list marks
  it and PDF export names it as the reason it cannot continue.
- Text layout (line breaks, widths) uses FontBox advance widths for both
  outputs, so the PNG, the screen and the PDF break lines at the same
  words. Java2D draws with `Font.createFont` of the same file.

## 6. Rendering (`design.DesignRenderer`, `design.Painter`)

`DesignRenderer` draws one item into a `Painter`, an interface with
`save/restore`, `translate/rotate`, `clipRect/clipAnnulus`,
`fillRect/fillEllipse/strokeRect/strokeEllipse`, `drawImage`,
`drawText(line, font, sizePt, colour, x, baseline)` and `line`. Two
implementations:

- `Java2dPainter` (`Graphics2D`): the editor canvas, PNG export and
  printing.
- `PdfPainter` (PDFBox `PDPageContentStream`): PDF export. Fonts are
  embedded as subsets with `PDType0Font.load(document, stream, true)`;
  images with `LosslessFactory` (PNG and others) or `JPEGFactory` (JPEG
  files, passed through). Units are converted with 1 mm = 72 / 25.4 pt.

Order: background colour over the bleed area, then elements bottom to
top, each clipped to the trim plus bleed (a disc label to the ring between
the hole minus bleed and the outer edge plus bleed). Guides (safe zone,
folds) are drawn only on screen.

## 7. Sheets (`design.SheetLayout`)

Places as many copies of an item as fit on the paper, trying portrait and
landscape and keeping the one with more copies:

- page margin 5 mm (most printers cannot print closer to the edge);
- each copy takes trim + 2 × bleed, plus 5 mm on each side for crop marks
  (4 mm marks, 1 mm away from the bleed);
- disc labels get a 0.25 pt grey cut circle at 118 mm and at the hole, not
  crop marks.

If nothing fits, it tries without crop marks (warning "No room for crop
marks"), then without bleed (warning "Bleed is cut off on this paper"),
then gives up ("Too large for this paper"). The DVD wrap on Letter ends
up without bleed; on A4 it fits landscape without crop marks.

## 8. Export and printing

- **PDF (`design.PdfExporter`):** one page of the paper size with all
  copies and their marks. Acceptance tests: the page size, the disc
  label's cut circle within ±0.5 mm of 118 mm, and every font embedded.
- **PNG (`design.PngExporter`):** one copy with its bleed at 300 dpi
  (11.811 pixels per mm), with a `pHYs` chunk of 11 811 pixels per metre so
  other programs print it at the right size.
- **Print (`ui.CoverPrinting`):** before the print dialog, a reminder:
  "Print at 100 % (Actual size). Turn off 'Fit to page'." The sheet is
  rendered at 300 dpi and placed in paper coordinates, shifted by the
  printable area's margins so the page lands where the PDF would. The
  alignment test page has 100 mm rulers along both axes, a 100 × 100 mm
  square and the outline of a 118 mm disc, so the user can measure and
  correct the printer.

## 9. `.rbdesign` files (`design.DesignFile`)

A ZIP with `design.json` (the document, `schemaVersion` 1) and
`images/<sha256>.<png|jpg|gif|bmp>`. Reading is defensive (threat model):
only those entry names are accepted (no folders, no `..`), at most 200
images, 50 MB per image and 200 MB in total, the hash of each image is
checked against its name, and unknown JSON fields are ignored so newer
files still open.

## 10. Screen (`ui.CoverDesignerView`)

```
[Template ▾] [Paper ▾] [Spine/Hole] | Undo Redo | − Zoom + Fit | Snap | Re-sync | Open Save | PDF PNG Print Test page
+-----------+---------------------------------------+------------------+
| Add       |                                       | Selected element |
|  Text     |        canvas (item at zoom,          |  X Y W H Rotate  |
|  Image    |        guides, selection handles)     |  Text / font /   |
|  Shape    |                                       |  size / colour   |
|  Tracks   |                                       |  Forward Back    |
|  Title    |                                       |  Align  Delete   |
| Presets   |                                       |                  |
+-----------+---------------------------------------+------------------+
```

The canvas is the `Java2dPainter` output shown in an `ImageView`, with
selection handles and snap guides as JavaFX shapes on top. Drag moves;
the corner handle resizes; arrow keys nudge by 0.5 mm (Shift: 5 mm);
Delete removes; Ctrl+Z / Ctrl+Y undo and redo.

Each project screen publishes a `CoverContent` when its contents change
(`UiServices.coverContent()`); the designer reads it on open and on
Re-sync.

## 11. Settings (`Settings.Design`)

| Key | Default |
|---|---|
| `design.paper` | empty (from the computer's country), `A4` or `LETTER` |
| `design.imageNoticeShown` | `false` |

## 12. Testing

- Unit: template files load and match the table; sheet layout counts and
  warnings for each template on A4 and Letter; snapping; command history
  limits, merging and redo clearing; text layout with FontBox widths;
  `fsType` read from a bundled font and from a copy patched to
  "restricted"; `.rbdesign` round trip and rejection of bad entry names,
  oversized and mismatched images.
- PDF: page size in points for A4 and Letter; all fonts embedded; the cut
  circle of the disc label measured from the content stream is 118 mm
  ±0.5 mm; an image keeps its pixel size.
- PNG: pixel size of each template at 300 dpi and the `pHYs` value.
- Manual (`docs/MANUAL_TEST.md`, cover section): print the alignment test
  page and each template at 100 %, measure, and record the results in
  `docs/PRINT_TEMPLATES.md`.
