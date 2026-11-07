// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayList;
import java.util.List;

/**
 * Snapping while an element is dragged or resized: its edges and centre are pulled to nearby guide
 * lines (item edges, centre lines, safe zone, folds, other elements). Pure functions, millimetres.
 */
public final class Snap {

    /** How close a line must be to snap, in screen millimetres (divided by the zoom). */
    public static final double TOLERANCE_MM = 1.5;

    /** Guide lines to snap to: x values of vertical lines and y values of horizontal lines. */
    public record Guides(List<Double> vertical, List<Double> horizontal) {

        public Guides {
            vertical = List.copyOf(vertical);
            horizontal = List.copyOf(horizontal);
        }
    }

    /** A snapped frame and the guide lines it touches, for drawing. */
    public record Result(Frame frame, List<Double> vertical, List<Double> horizontal) {}

    private Snap() {}

    /** Guides of an item and of every element except the one being moved. */
    public static Guides guides(PrintTemplate template, DesignDocument document, String movingId) {
        double width = template.width(document.spineMm());
        double height = template.heightMm();
        double safe = template.safeMm();
        List<Double> vertical = new ArrayList<>(List.of(0.0, width / 2, width, safe, width - safe));
        List<Double> horizontal = new ArrayList<>(List.of(0.0, height / 2, height, safe, height - safe));
        for (double fold : template.folds(document.spineMm())) {
            vertical.add(fold);
            vertical.add(fold - safe);
            vertical.add(fold + safe);
        }
        for (DesignElement element : document.elements()) {
            if (element.id().equals(movingId) || element.frame().rotationDegrees() != 0) {
                continue;
            }
            Frame frame = element.frame();
            vertical.addAll(List.of(frame.x(), frame.centerX(), frame.right()));
            horizontal.addAll(List.of(frame.y(), frame.centerY(), frame.bottom()));
        }
        return new Guides(vertical, horizontal);
    }

    /** Moves a frame so its nearest edge or centre lies on a guide, on each axis separately. */
    public static Result move(Frame frame, Guides guides, double tolerance) {
        Offset x = nearest(List.of(frame.x(), frame.centerX(), frame.right()), guides.vertical(), tolerance);
        Offset y = nearest(List.of(frame.y(), frame.centerY(), frame.bottom()), guides.horizontal(), tolerance);
        return new Result(frame.moveTo(frame.x() + x.delta(), frame.y() + y.delta()), x.lines(), y.lines());
    }

    /** Snaps the right and bottom edges while the bottom-right corner is dragged. */
    public static Result resize(Frame frame, Guides guides, double tolerance) {
        Offset x = nearest(List.of(frame.right()), guides.vertical(), tolerance);
        Offset y = nearest(List.of(frame.bottom()), guides.horizontal(), tolerance);
        return new Result(frame.resize(frame.width() + x.delta(), frame.height() + y.delta()), x.lines(), y.lines());
    }

    private record Offset(double delta, List<Double> lines) {}

    private static Offset nearest(List<Double> points, List<Double> lines, double tolerance) {
        double best = Double.MAX_VALUE;
        double delta = 0;
        Double hit = null;
        for (double point : points) {
            for (double line : lines) {
                double distance = Math.abs(line - point);
                if (distance <= tolerance && distance < best) {
                    best = distance;
                    delta = line - point;
                    hit = line;
                }
            }
        }
        return hit == null ? new Offset(0, List.of()) : new Offset(delta, List.of(hit));
    }
}
