// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Draws reel frames into one reused image and hands them out as RGB bytes, the layout FFmpeg's
 * {@code rawvideo} input with {@code rgb24} expects. Not thread-safe.
 */
public final class ReelRenderer {

    private final ReelTimeline timeline;
    private final BurnSession session;
    private final ReelStyle style;
    private final BufferedImage image =
            new BufferedImage(ReelStyle.WIDTH, ReelStyle.HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
    private final byte[] rgb = new byte[ReelStyle.WIDTH * ReelStyle.HEIGHT * 3];

    public ReelRenderer(ReelTimeline timeline, BurnSession session, ReelStyle style) {
        this.timeline = timeline;
        this.session = session;
        this.style = style;
    }

    /** Draws frame {@code index} and returns the image (valid until the next call). */
    public BufferedImage draw(int index) {
        ReelFrame frame = timeline.frame(index);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            frame.scene().render(graphics, frame, session, style);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Draws frame {@code index} as RGB bytes, row by row (the array is reused). */
    public byte[] rgb(int index) {
        draw(index);
        byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        for (int pixel = 0; pixel < bgr.length; pixel += 3) {
            rgb[pixel] = bgr[pixel + 2];
            rgb[pixel + 1] = bgr[pixel + 1];
            rgb[pixel + 2] = bgr[pixel];
        }
        return rgb;
    }
}
