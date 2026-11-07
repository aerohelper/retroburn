// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import java.awt.image.BufferedImage;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** Copies Java2D images into JavaFX images (the app does not use the javafx-swing module). */
final class FxImages {

    private FxImages() {}

    static WritableImage of(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        WritableImage fx = new WritableImage(width, height);
        fx.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return fx;
    }
}
