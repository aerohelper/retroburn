<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0016 — PDF export with Apache PDFBox, and fonts for covers

- Status: Proposed
- Date: 2026-11-07
- Deciders: project owner (review pending); proposed by the developer

## Context and problem statement

The cover designer exports PDF for printing at home or at a print shop.
The PDF must have the exact page size, vector text and shapes, embedded
fonts and full-resolution images, and the result must look the same as
the screen and the PNG export. Which library writes the PDF, and which
fonts can the user pick?

## Decision drivers

- Licence compatible with GPL-3.0-or-later, maintained, pure Java.
- Font embedding with subsetting, so files stay small and print the same
  everywhere.
- The same line breaks on screen, in PNG and in PDF.
- Respect font licences that forbid embedding.

## Considered options

1. Apache PDFBox 3 (Apache-2.0), with FontBox for reading fonts.
2. OpenPDF (LGPL-2.1/MPL-2.0).
3. JavaFX printing to a PDF printer driver (no control over embedding or
   page size; not available on every system).

## Decision outcome

Proposed: **option 1**, Apache PDFBox 3.0.8 (`pdfbox`, which brings
`pdfbox-io`, `fontbox` and `commons-logging`).

- Fonts: only TrueType (`.ttf`) files, loaded with
  `PDType0Font.load(document, stream, true)` (embedded subsets). `.ttc`
  collections, CFF-based `.otf` files and bitmap fonts are not offered.
- A font whose `OS/2` `fsType` has bit 1 set ("Restricted License
  embedding", value 2) is listed but cannot be used in a PDF; the export
  names it.
- Line breaks and text widths come from FontBox's advance widths for
  every output; Java2D only draws the glyphs.
- Bundled fonts: Lato (Regular and Bold) and VT323, both under the SIL
  Open Font License 1.1, taken unchanged from the Google Fonts repository
  (`ofl/lato`, `ofl/vt323`), with their `OFL.txt`. VT323 is also the
  display font of the `y2k` skin.

### Consequences

- Good: an Apache-2.0 library already planned in the third-party register;
  embedded subsets; one text layout for every output.
- Bad: about 3.5 MB more in the package; OpenType-CFF and collection fonts
  are not available in the designer.
- Licence duties: PDFBox, FontBox and Commons Logging NOTICE files and the
  PDFBox LICENSE (which lists the external components it contains) are
  shipped in the in-app credits; the fonts' `OFL.txt` files are shipped
  next to them and listed in `assets/CREDITS.md`.

## More information

- PDFBox: https://pdfbox.apache.org/ — `PDType0Font`, `LosslessFactory`,
  `JPEGFactory`.
- `fsType`: OpenType specification, `OS/2` table.
