// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Draws a menu page: the background with title, thumbnails and labels, and the highlight and select
 * overlays spumux turns into button subpictures. See {@code docs/design/LLD-video-dvd.md} section 4.
 */
public final class MenuRenderer {

    static final int OUTLINE = 4;

    private MenuRenderer() {}

    /**
     * The three images of a page.
     *
     * @param background what the menu video shows, at the DVD frame size
     * @param highlight outlines of all buttons in the highlight colour, transparent elsewhere
     * @param select outlines of all buttons in the select colour, transparent elsewhere
     */
    public record Images(BufferedImage background, BufferedImage highlight, BufferedImage select) {

        /** Writes the three images as PNG files named {@code <stem>-bg.png}, {@code -hl.png}, {@code -sel.png}. */
        public PageFiles write(Path folder, String stem) throws IOException {
            Files.createDirectories(folder);
            Path bg = folder.resolve(stem + "-bg.png");
            Path hl = folder.resolve(stem + "-hl.png");
            Path sel = folder.resolve(stem + "-sel.png");
            ImageIO.write(background, "png", bg.toFile());
            ImageIO.write(highlight, "png", hl.toFile());
            ImageIO.write(select, "png", sel.toFile());
            return new PageFiles(bg, hl, sel);
        }
    }

    /**
     * Where the images were written.
     *
     * @param background the background PNG
     * @param highlight the highlight PNG
     * @param select the select PNG
     */
    public record PageFiles(Path background, Path highlight, Path select) {}

    /**
     * @param thumbnails thumbnail images by button name; missing ones are drawn as empty boxes
     */
    public static Images render(
            MenuLayout.Page page,
            String discTitle,
            MenuTemplate template,
            TvStandard standard,
            Map<String, BufferedImage> thumbnails) {
        int width = standard.squareMenuWidth();
        int height = standard.height();
        BufferedImage square = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setPaint(new GradientPaint(0, 0, template.top(), 0, height, template.bottom()));
            g.fillRect(0, 0, width, height);
            g.setColor(template.text());
            g.setFont(new Font(template.font(), Font.BOLD, height / 14));
            drawCentred(g, discTitle, width / 2, height * 13 / 100, width - width / 6);
            Font labelFont = new Font(template.font(), Font.PLAIN, height / 30);
            for (MenuLayout.Button button : page.buttons()) {
                MenuLayout.Rect area = button.area();
                MenuLayout.Rect thumb = page.thumbnails().get(button.name());
                g.setColor(template.text());
                if (thumb != null) {
                    BufferedImage image = thumbnails.get(button.name());
                    if (image != null) {
                        g.drawImage(image, thumb.x(), thumb.y(), thumb.width(), thumb.height(), null);
                    } else {
                        g.setColor(template.bottom().darker());
                        g.fillRect(thumb.x(), thumb.y(), thumb.width(), thumb.height());
                        g.setColor(template.text());
                    }
                    if (template.framedThumbnails()) {
                        g.setStroke(new BasicStroke(2));
                        g.drawRect(thumb.x(), thumb.y(), thumb.width(), thumb.height());
                    }
                    g.setFont(labelFont);
                    drawCentred(
                            g,
                            button.label(),
                            thumb.x() + thumb.width() / 2,
                            thumb.y() + thumb.height() + labelFont.getSize() + 8,
                            thumb.width());
                } else {
                    g.setFont(labelFont.deriveFont(Font.BOLD));
                    drawCentred(
                            g,
                            button.label(),
                            area.x() + area.width() / 2,
                            area.y() + area.height() * 2 / 3,
                            area.width());
                }
            }
        } finally {
            g.dispose();
        }
        return new Images(
                scale(square, standard),
                overlay(page, standard, template.highlight()),
                overlay(page, standard, template.select()));
    }

    /** Scales the square-pixel drawing to the 720-pixel DVD frame. */
    private static BufferedImage scale(BufferedImage square, TvStandard standard) {
        BufferedImage frame = new BufferedImage(TvStandard.WIDTH, standard.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = frame.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(square, 0, 0, TvStandard.WIDTH, standard.height(), null);
        } finally {
            g.dispose();
        }
        return frame;
    }

    /** Outlines of every button in one colour, no anti-aliasing, so the image has two colours. */
    private static BufferedImage overlay(MenuLayout.Page page, TvStandard standard, Color colour) {
        BufferedImage image = new BufferedImage(TvStandard.WIDTH, standard.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue()));
            for (MenuLayout.Button button : page.buttons()) {
                MenuLayout.Rect r = button.area().toFrame(standard);
                g.fillRect(r.x(), r.y(), r.width(), OUTLINE);
                g.fillRect(r.x(), r.y() + r.height() - OUTLINE, r.width(), OUTLINE);
                g.fillRect(r.x(), r.y(), OUTLINE, r.height());
                g.fillRect(r.x() + r.width() - OUTLINE, r.y(), OUTLINE, r.height());
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawCentred(Graphics2D g, String text, int centreX, int baseline, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();
        String shown = text;
        while (shown.length() > 1 && metrics.stringWidth(shown + "…") > maxWidth) {
            shown = shown.substring(0, shown.length() - 1);
        }
        if (!shown.equals(text)) {
            shown = shown.strip() + "…";
        }
        g.drawString(shown, centreX - metrics.stringWidth(shown) / 2, baseline);
    }
}
