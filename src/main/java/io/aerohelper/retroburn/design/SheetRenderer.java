// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;
import java.util.Objects;

/**
 * Draws a whole sheet: every copy of the item, its crop marks (fold marks too) or, for disc
 * labels, thin cut circles at the outer edge and the hole. Shared by PDF export and printing.
 */
public final class SheetRenderer {

    static final String MARK_COLOUR = "#000000";
    static final String CUT_COLOUR = "#999999";

    /** 0.25 pt, in millimetres. */
    static final double HAIRLINE_MM = 0.25 * 25.4 / 72;

    private final DesignRenderer renderer;

    public SheetRenderer(DesignRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public void render(DesignDocument document, PrintTemplate template, SheetLayout.Sheet sheet, Painter painter)
            throws IOException {
        for (double[] origin : sheet.origins()) {
            painter.save();
            painter.translate(origin[0], origin[1]);
            renderer.render(document, template, painter, false, sheet.bleedMm());
            if (template.isDisc()) {
                cutCircles(document, sheet, painter);
            } else if (sheet.cropMarks()) {
                cropMarks(template, document, sheet, painter);
            }
            painter.restore();
        }
    }

    private static void cutCircles(DesignDocument document, SheetLayout.Sheet sheet, Painter painter)
            throws IOException {
        double diameter = sheet.itemWidthMm();
        painter.strokeEllipse(0, 0, diameter, diameter, CUT_COLOUR, HAIRLINE_MM);
        double hole = document.holeDiameterMm();
        double offset = (diameter - hole) / 2;
        painter.strokeEllipse(offset, offset, hole, hole, CUT_COLOUR, HAIRLINE_MM);
    }

    private static void cropMarks(
            PrintTemplate template, DesignDocument document, SheetLayout.Sheet sheet, Painter painter)
            throws IOException {
        double width = sheet.itemWidthMm();
        double height = sheet.itemHeightMm();
        double near = sheet.bleedMm() + SheetLayout.MARK_GAP_MM;
        double far = near + SheetLayout.MARK_LENGTH_MM;
        for (double x : new double[] {0, width}) {
            double outward = x == 0 ? -1 : 1;
            for (double y : new double[] {0, height}) {
                double down = y == 0 ? -1 : 1;
                // Horizontal mark on the trim line beside the corner, vertical mark above or below it.
                painter.line(x + outward * near, y, x + outward * far, y, MARK_COLOUR, HAIRLINE_MM);
                painter.line(x, y + down * near, x, y + down * far, MARK_COLOUR, HAIRLINE_MM);
            }
        }
        for (double fold : template.folds(document.spineMm())) {
            painter.line(fold, -near, fold, -far, MARK_COLOUR, HAIRLINE_MM);
            painter.line(fold, height + near, fold, height + far, MARK_COLOUR, HAIRLINE_MM);
        }
    }
}
