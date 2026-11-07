// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The copies-per-sheet table of {@code docs/PRINT_TEMPLATES.md}. */
class SheetLayoutTest {

    private final PrintTemplates templates = PrintTemplates.builtIn();

    private SheetLayout.Sheet plan(String id, Paper paper) {
        PrintTemplate template = templates.get(id);
        return SheetLayout.plan(template, template.defaultSpineMm(), paper).orElseThrow();
    }

    @Test
    void cdFrontFitsTwiceOnA4AndOnceOnLetterWithCropMarks() {
        SheetLayout.Sheet a4 = plan("cd-front", Paper.A4);
        assertEquals(2, a4.copies());
        assertTrue(a4.cropMarks());
        assertEquals(3, a4.bleedMm());
        assertEquals(List.of(), a4.warnings());
        assertEquals(1, plan("cd-front", Paper.LETTER).copies());
        assertEquals(1, plan("cd-slim", Paper.LETTER).copies());
    }

    @Test
    void cdBackFitsTwiceOnBoth() {
        assertEquals(2, plan("cd-back", Paper.A4).copies());
        assertEquals(2, plan("cd-back", Paper.LETTER).copies());
    }

    @Test
    void dvdWrapNeedsLandscapeAndLosesMarksOrBleed() {
        SheetLayout.Sheet a4 = plan("dvd-wrap", Paper.A4);
        assertEquals(1, a4.copies());
        assertTrue(a4.landscape());
        assertFalse(a4.cropMarks());
        assertEquals(3, a4.bleedMm());
        assertEquals(List.of("design.sheet.noCropMarks"), a4.warnings());

        SheetLayout.Sheet letter = plan("dvd-wrap", Paper.LETTER);
        assertEquals(1, letter.copies());
        assertTrue(letter.landscape());
        assertEquals(0, letter.bleedMm());
        assertEquals(List.of("design.sheet.noBleed", "design.sheet.narrowMargin"), letter.warnings());
    }

    @Test
    void discLabelsFitTwiceWithoutCropMarks() {
        SheetLayout.Sheet a4 = plan("disc-label", Paper.A4);
        assertEquals(2, a4.copies());
        assertFalse(a4.cropMarks());
        assertEquals(2, plan("disc-label", Paper.LETTER).copies());
    }

    @Test
    void copiesStayInsideThePrintableArea() {
        for (PrintTemplate template : templates.all()) {
            for (Paper paper : Paper.values()) {
                SheetLayout.Sheet sheet = SheetLayout.plan(template, template.defaultSpineMm(), paper)
                        .orElseThrow();
                double margin = sheet.warnings().contains("design.sheet.narrowMargin")
                        ? SheetLayout.NARROW_MARGIN_MM
                        : SheetLayout.PAGE_MARGIN_MM;
                for (double[] origin : sheet.origins()) {
                    double left = origin[0] - sheet.bleedMm();
                    double top = origin[1] - sheet.bleedMm();
                    double right = origin[0] + sheet.itemWidthMm() + sheet.bleedMm();
                    double bottom = origin[1] + sheet.itemHeightMm() + sheet.bleedMm();
                    String where = template.id() + " on " + paper;
                    assertTrue(left >= margin - 1e-9, where);
                    assertTrue(top >= margin - 1e-9, where);
                    assertTrue(right <= sheet.widthMm() - margin + 1e-9, where);
                    assertTrue(bottom <= sheet.heightMm() - margin + 1e-9, where);
                }
            }
        }
    }

    @Test
    void anItemLargerThanThePaperDoesNotFit() {
        PrintTemplate poster = new PrintTemplate(
                1, "poster", PrintTemplate.Shape.RECTANGLE, 400, 400, 3, 3, List.of(), false, 0, 0, 0, List.of());
        assertTrue(SheetLayout.plan(poster, 0, Paper.A4).isEmpty());
    }
}
