<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Print templates

| | |
|---|---|
| Status | Starting values. Not yet confirmed by a test print. |
| Files | `src/main/resources/print-templates/<id>.json` |
| Design | [LLD-cover-designer.md](design/LLD-cover-designer.md) |

The sizes below are the ones commonly used for these cases and inserts.
Cases differ slightly between makers, so each value is marked verified only
after a test print has been cut and tried in a real case.

All templates use a 3 mm bleed and a 3 mm safe zone inside the trim.

| Template | id | Trim (mm) | Folds / spine | Hole | Verified |
|---|---|---|---|---|---|
| CD jewel case front | `cd-front` | 120 × 120 | — | — | no |
| CD back tray inlay | `cd-back` | 151 × 118 | spines 6.5 mm at both ends (folds at 6.5 and 144.5) | — | no |
| CD slim case insert | `cd-slim` | 120 × 120 | — | — | no |
| DVD keep-case wrap | `dvd-wrap` | 273 × 183 with a 14 mm spine (259 + spine) | folds at 129.5 and 129.5 + spine; spine 5–30 mm | — | no |
| Disc label | `disc-label` | Ø 118 | — | Ø 41 (default) or Ø 17 | no |

## Copies per sheet

From `SheetLayout` (5 mm page margin, crop marks need 5 mm around each
copy's bleed). Checked by `SheetLayoutTest`.

| Template | A4 | Letter |
|---|---|---|
| `cd-front`, `cd-slim` | 2 | 1 |
| `cd-back` | 2 | 2 |
| `dvd-wrap` | 1, landscape, no crop marks | 1, landscape, no bleed, needs a printer that prints within 3 mm of the edge |
| `disc-label` | 2 | 2 |

## How to test-print

1. In the designer, choose **Test page** and print it at 100 % ("Actual
   size", "Fit to page" off). Measure the 100 mm rulers and the square.
   If they are off by more than 0.5 mm, the printer driver is scaling;
   fix that before going on.
2. Print each template with a simple design, cut along the crop marks
   (disc labels: along the cut circles) and try it in the case or on the
   disc.
3. Record the result below. If a size is wrong, change the template file,
   note the new value and the case it was tried with.

## Results

| Date | Template | Printer, paper | Measured | Fits the case? | Notes |
|---|---|---|---|---|---|
| | | | | | |
