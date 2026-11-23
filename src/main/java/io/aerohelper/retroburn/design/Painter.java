// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;

/**
 * Where a design is drawn: the screen and PNG files ({@link Java2dPainter}) or a PDF page ({@link
 * PdfPainter}). Coordinates are millimetres, y down. Colours are {@code #RRGGBB}; an empty colour
 * draws nothing.
 */
public interface Painter {

    void save() throws IOException;

    void restore() throws IOException;

    void translate(double dx, double dy) throws IOException;

    /** Rotates the following drawing by {@code degrees} (clockwise) about a point. */
    void rotate(double degrees, double cx, double cy) throws IOException;

    void clipRect(double x, double y, double width, double height) throws IOException;

    /** Clips to the ring between two circles around a centre. */
    void clipRing(double cx, double cy, double outerRadius, double innerRadius) throws IOException;

    void fillRect(double x, double y, double width, double height, String colour) throws IOException;

    void strokeRect(double x, double y, double width, double height, String colour, double lineMm) throws IOException;

    void fillEllipse(double x, double y, double width, double height, String colour) throws IOException;

    void strokeEllipse(double x, double y, double width, double height, String colour, double lineMm)
            throws IOException;

    void line(double x1, double y1, double x2, double y2, String colour, double lineMm) throws IOException;

    /** Draws an image of the store stretched to the box. */
    void image(String imageId, double x, double y, double width, double height) throws IOException;

    /** Draws one line of text, already made {@linkplain FontFace#drawable drawable}, from its baseline. */
    void text(String line, FontFace face, double sizeMm, String colour, double x, double baseline) throws IOException;
}
