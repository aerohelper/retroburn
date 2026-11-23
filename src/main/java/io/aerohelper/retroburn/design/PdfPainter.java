// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;

/**
 * Draws a design on a PDF page with Apache PDFBox: vector shapes, embedded font subsets, images at
 * their own resolution (JPEG files passed through). The page is set up in millimetres with y down,
 * so the same {@link DesignRenderer} code serves the screen and the PDF.
 */
public final class PdfPainter implements Painter {

    /** PDF points per millimetre. */
    public static final float POINTS_PER_MM = 72f / 25.4f;

    /** Bézier control-point distance for a quarter circle. */
    private static final double KAPPA = 0.5522847498;

    private final PDDocument document;
    private final PDPageContentStream stream;
    private final ImageStore images;
    private final Map<FontFace, PDType0Font> fonts;
    private final Map<String, PDImageXObject> placedImages = new HashMap<>();

    /**
     * @param fonts embedded fonts of this document, shared by its pages
     * @param pageHeightMm height of the page, to turn PDF's y-up space into y-down millimetres
     */
    public PdfPainter(
            PDDocument document,
            PDPageContentStream stream,
            double pageHeightMm,
            ImageStore images,
            Map<FontFace, PDType0Font> fonts)
            throws IOException {
        this.document = document;
        this.stream = stream;
        this.images = images;
        this.fonts = fonts;
        stream.transform(new Matrix(POINTS_PER_MM, 0, 0, -POINTS_PER_MM, 0, (float) pageHeightMm * POINTS_PER_MM));
    }

    @Override
    public void save() throws IOException {
        stream.saveGraphicsState();
    }

    @Override
    public void restore() throws IOException {
        stream.restoreGraphicsState();
    }

    @Override
    public void translate(double dx, double dy) throws IOException {
        stream.transform(Matrix.getTranslateInstance((float) dx, (float) dy));
    }

    @Override
    public void rotate(double degrees, double cx, double cy) throws IOException {
        translate(cx, cy);
        stream.transform(Matrix.getRotateInstance(Math.toRadians(degrees), 0, 0));
        translate(-cx, -cy);
    }

    @Override
    public void clipRect(double x, double y, double width, double height) throws IOException {
        stream.addRect((float) x, (float) y, (float) width, (float) height);
        stream.clip();
    }

    @Override
    public void clipRing(double cx, double cy, double outerRadius, double innerRadius) throws IOException {
        ellipsePath(cx - outerRadius, cy - outerRadius, 2 * outerRadius, 2 * outerRadius);
        ellipsePath(cx - innerRadius, cy - innerRadius, 2 * innerRadius, 2 * innerRadius);
        stream.clipEvenOdd();
    }

    @Override
    public void fillRect(double x, double y, double width, double height, String colour) throws IOException {
        if (fillColour(colour)) {
            stream.addRect((float) x, (float) y, (float) width, (float) height);
            stream.fill();
        }
    }

    @Override
    public void strokeRect(double x, double y, double width, double height, String colour, double lineMm)
            throws IOException {
        if (strokeColour(colour, lineMm)) {
            stream.addRect((float) x, (float) y, (float) width, (float) height);
            stream.stroke();
        }
    }

    @Override
    public void fillEllipse(double x, double y, double width, double height, String colour) throws IOException {
        if (fillColour(colour)) {
            ellipsePath(x, y, width, height);
            stream.fill();
        }
    }

    @Override
    public void strokeEllipse(double x, double y, double width, double height, String colour, double lineMm)
            throws IOException {
        if (strokeColour(colour, lineMm)) {
            ellipsePath(x, y, width, height);
            stream.stroke();
        }
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String colour, double lineMm) throws IOException {
        if (strokeColour(colour, lineMm)) {
            stream.moveTo((float) x1, (float) y1);
            stream.lineTo((float) x2, (float) y2);
            stream.stroke();
        }
    }

    @Override
    public void image(String imageId, double x, double y, double width, double height) throws IOException {
        PDImageXObject image = placedImages.get(imageId);
        if (image == null) {
            image = images.format(imageId) == ImageStore.Format.JPEG
                    ? JPEGFactory.createFromByteArray(document, images.bytes(imageId))
                    : LosslessFactory.createFromImage(document, images.image(imageId));
            placedImages.put(imageId, image);
        }
        // The image's unit square, flipped so its top row lands at y.
        stream.drawImage(image, new Matrix((float) width, 0, 0, (float) -height, (float) x, (float) (y + height)));
    }

    @Override
    public void text(String line, FontFace face, double sizeMm, String colour, double x, double baseline)
            throws IOException {
        if (colour.isEmpty() || line.isEmpty()) {
            return;
        }
        if (!face.embeddable()) {
            throw new FontNotEmbeddableException(face);
        }
        PDType0Font font = fonts.get(face);
        if (font == null) {
            font = PDType0Font.load(document, new ByteArrayInputStream(face.bytes()), true);
            fonts.put(face, font);
        }
        fillColour(colour);
        stream.saveGraphicsState();
        // Text space is y-up: flip it back at the baseline.
        stream.transform(new Matrix(1, 0, 0, -1, (float) x, (float) baseline));
        stream.beginText();
        stream.setFont(font, (float) sizeMm);
        stream.newLineAtOffset(0, 0);
        stream.showText(line);
        stream.endText();
        stream.restoreGraphicsState();
    }

    /** A font whose licence forbids embedding was used in a design being exported to PDF. */
    public static final class FontNotEmbeddableException extends IOException {

        private final transient FontFace face;

        public FontNotEmbeddableException(FontFace face) {
            super("The font " + face + " does not allow embedding");
            this.face = face;
        }

        public FontFace face() {
            return face;
        }
    }

    private boolean fillColour(String colour) throws IOException {
        if (colour.isEmpty()) {
            return false;
        }
        int rgb = Java2dPainter.Colours.rgb(colour);
        stream.setNonStrokingColor(channel(rgb >> 16), channel(rgb >> 8), channel(rgb));
        return true;
    }

    private boolean strokeColour(String colour, double lineMm) throws IOException {
        if (colour.isEmpty() || lineMm <= 0) {
            return false;
        }
        int rgb = Java2dPainter.Colours.rgb(colour);
        stream.setStrokingColor(channel(rgb >> 16), channel(rgb >> 8), channel(rgb));
        stream.setLineWidth((float) lineMm);
        return true;
    }

    private static float channel(int value) {
        return (value & 0xFF) / 255f;
    }

    private void ellipsePath(double x, double y, double width, double height) throws IOException {
        double rx = width / 2;
        double ry = height / 2;
        double cx = x + rx;
        double cy = y + ry;
        double ox = rx * KAPPA;
        double oy = ry * KAPPA;
        stream.moveTo((float) (cx + rx), (float) cy);
        stream.curveTo((float) (cx + rx), (float) (cy + oy), (float) (cx + ox), (float) (cy + ry), (float) cx, (float)
                (cy + ry));
        stream.curveTo(
                (float) (cx - ox), (float) (cy + ry), (float) (cx - rx), (float) (cy + oy), (float) (cx - rx), (float)
                        cy);
        stream.curveTo((float) (cx - rx), (float) (cy - oy), (float) (cx - ox), (float) (cy - ry), (float) cx, (float)
                (cy - ry));
        stream.curveTo(
                (float) (cx + ox), (float) (cy - ry), (float) (cx + rx), (float) (cy - oy), (float) (cx + rx), (float)
                        cy);
        stream.closePath();
    }
}
