// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;

/**
 * A test page for checking that the printer prints at 100 %: 100 mm rulers along both axes, a 100 ×
 * 100 mm square and the outline of a 118 mm disc label.
 */
public final class AlignmentPage {

    static final double LEFT_MM = 20;
    static final double TOP_MM = 25;
    static final double RULER_MM = 100;
    static final double DISC_MM = 118;
    static final String INK = "#000000";

    private AlignmentPage() {}

    /**
     * @param headline text above the rulers, such as "Measure: each line should be 100 mm"
     */
    public static void render(Paper paper, FontCatalog fonts, String headline, Painter painter) throws IOException {
        FontFace face = fonts.face(FontCatalog.DEFAULT_FAMILY, false);
        double textMm = TextLayout.mm(11);
        painter.text(face.drawable(headline), face, textMm, INK, LEFT_MM, TOP_MM - 10);

        double line = 0.15;
        // Horizontal ruler with ticks every millimetre, longer every 5 and 10 mm.
        painter.line(LEFT_MM, TOP_MM, LEFT_MM + RULER_MM, TOP_MM, INK, line);
        painter.line(LEFT_MM, TOP_MM, LEFT_MM, TOP_MM + RULER_MM, INK, line);
        for (int mm = 0; mm <= RULER_MM; mm++) {
            double tick = mm % 10 == 0 ? 4 : mm % 5 == 0 ? 2.5 : 1.5;
            painter.line(LEFT_MM + mm, TOP_MM, LEFT_MM + mm, TOP_MM + tick, INK, line / 2);
            painter.line(LEFT_MM, TOP_MM + mm, LEFT_MM + tick, TOP_MM + mm, INK, line / 2);
        }
        double small = TextLayout.mm(7);
        for (int mm = 10; mm <= RULER_MM; mm += 10) {
            painter.text(Integer.toString(mm), face, small, INK, LEFT_MM + mm - 2, TOP_MM - 1.5);
            painter.text(Integer.toString(mm), face, small, INK, LEFT_MM - 7, TOP_MM + mm + 1);
        }

        // The square, then the disc outline below it.
        double squareLeft = LEFT_MM + 10;
        double squareTop = TOP_MM + 10;
        painter.strokeRect(squareLeft, squareTop, RULER_MM, RULER_MM, INK, line);
        painter.text("100 × 100 mm", face, small, INK, squareLeft + 2, squareTop + 6);

        double discTop = squareTop + RULER_MM + 15;
        double discLeft = (paper.widthMm() - DISC_MM) / 2;
        painter.strokeEllipse(discLeft, discTop, DISC_MM, DISC_MM, INK, line);
        painter.line(discLeft, discTop + DISC_MM / 2, discLeft + DISC_MM, discTop + DISC_MM / 2, INK, line / 2);
        painter.text("118 mm", face, small, INK, discLeft + DISC_MM / 2 - 5, discTop + DISC_MM / 2 - 1.5);
    }
}
