// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

/**
 * Where an element sits: millimetres from the item's top-left trim corner, y down, rotated about
 * its centre (positive is clockwise).
 */
public record Frame(double x, double y, double width, double height, double rotationDegrees) {

    /** Elements are never smaller than this, so they can still be grabbed. */
    public static final double MIN_SIZE_MM = 2;

    public Frame {
        width = Math.max(MIN_SIZE_MM, width);
        height = Math.max(MIN_SIZE_MM, height);
    }

    public static Frame of(double x, double y, double width, double height) {
        return new Frame(x, y, width, height, 0);
    }

    public Frame moveTo(double newX, double newY) {
        return new Frame(newX, newY, width, height, rotationDegrees);
    }

    public Frame resize(double newWidth, double newHeight) {
        return new Frame(x, y, newWidth, newHeight, rotationDegrees);
    }

    public Frame rotate(double degrees) {
        return new Frame(x, y, width, height, degrees);
    }

    public double centerX() {
        return x + width / 2;
    }

    public double centerY() {
        return y + height / 2;
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }
}
