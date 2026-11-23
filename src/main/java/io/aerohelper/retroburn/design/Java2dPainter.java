// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

/** Draws a design with Java2D: the editor canvas, PNG export and printing. */
public final class Java2dPainter implements Painter {

    private record State(AffineTransform transform, Shape clip) {}

    private final Graphics2D graphics;
    private final ImageStore images;
    private final Deque<State> states = new ArrayDeque<>();

    /** @param pixelsPerMm scale from millimetres to the graphics' pixels */
    public Java2dPainter(Graphics2D graphics, double pixelsPerMm, ImageStore images) {
        this.graphics = graphics;
        this.images = images;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.scale(pixelsPerMm, pixelsPerMm);
    }

    @Override
    public void save() {
        states.push(new State(graphics.getTransform(), graphics.getClip()));
    }

    @Override
    public void restore() {
        State state = states.pop();
        graphics.setTransform(state.transform());
        graphics.setClip(state.clip());
    }

    @Override
    public void translate(double dx, double dy) {
        graphics.translate(dx, dy);
    }

    @Override
    public void rotate(double degrees, double cx, double cy) {
        graphics.rotate(Math.toRadians(degrees), cx, cy);
    }

    @Override
    public void clipRect(double x, double y, double width, double height) {
        graphics.clip(new Rectangle2D.Double(x, y, width, height));
    }

    @Override
    public void clipRing(double cx, double cy, double outerRadius, double innerRadius) {
        Area ring = new Area(circle(cx, cy, outerRadius));
        ring.subtract(new Area(circle(cx, cy, innerRadius)));
        graphics.clip(ring);
    }

    @Override
    public void fillRect(double x, double y, double width, double height, String colour) {
        fill(new Rectangle2D.Double(x, y, width, height), colour);
    }

    @Override
    public void strokeRect(double x, double y, double width, double height, String colour, double lineMm) {
        stroke(new Rectangle2D.Double(x, y, width, height), colour, lineMm);
    }

    @Override
    public void fillEllipse(double x, double y, double width, double height, String colour) {
        fill(new Ellipse2D.Double(x, y, width, height), colour);
    }

    @Override
    public void strokeEllipse(double x, double y, double width, double height, String colour, double lineMm) {
        stroke(new Ellipse2D.Double(x, y, width, height), colour, lineMm);
    }

    @Override
    public void line(double x1, double y1, double x2, double y2, String colour, double lineMm) {
        stroke(new Line2D.Double(x1, y1, x2, y2), colour, lineMm);
    }

    @Override
    public void image(String imageId, double x, double y, double width, double height) {
        BufferedImage image = images.image(imageId);
        AffineTransform place = new AffineTransform();
        place.translate(x, y);
        place.scale(width / image.getWidth(), height / image.getHeight());
        graphics.drawImage(image, place, null);
    }

    @Override
    public void text(String line, FontFace face, double sizeMm, String colour, double x, double baseline) {
        if (colour.isEmpty() || line.isEmpty()) {
            return;
        }
        graphics.setColor(Colours.awt(colour));
        graphics.setFont(face.awtFont().deriveFont((float) sizeMm));
        graphics.drawString(line, (float) x, (float) baseline);
    }

    private void fill(Shape shape, String colour) {
        if (colour.isEmpty()) {
            return;
        }
        graphics.setColor(Colours.awt(colour));
        graphics.fill(shape);
    }

    private void stroke(Shape shape, String colour, double lineMm) {
        if (colour.isEmpty() || lineMm <= 0) {
            return;
        }
        graphics.setColor(Colours.awt(colour));
        graphics.setStroke(new BasicStroke((float) lineMm));
        graphics.draw(shape);
    }

    private static Ellipse2D circle(double cx, double cy, double radius) {
        return new Ellipse2D.Double(cx - radius, cy - radius, 2 * radius, 2 * radius);
    }

    /** Colour strings of designs. */
    static final class Colours {

        private Colours() {}

        static Color awt(String colour) {
            return new Color(rgb(colour));
        }

        static int rgb(String colour) {
            if (!colour.matches("#[0-9A-Fa-f]{6}")) {
                throw new IllegalArgumentException("Not a #RRGGBB colour: " + colour);
            }
            return Integer.parseInt(colour.substring(1), 16);
        }
    }
}
