// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;

/**
 * Writes one copy of an item, with its bleed, as a 300 dpi PNG. The file records its resolution in
 * a {@code pHYs} chunk, so other programs print it at the right size. Disc labels are transparent
 * outside the ring.
 */
public final class PngExporter {

    public static final int DPI = 300;

    /** 300 dots per inch in dots per metre, as the {@code pHYs} chunk stores it. */
    static final int PIXELS_PER_METRE = (int) Math.round(DPI / 0.0254);

    private final DesignRenderer renderer;

    public PngExporter(DesignRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /** Draws a copy with its bleed at 300 dpi. */
    public BufferedImage draw(DesignDocument document, PrintTemplate template) throws IOException {
        return draw(document, template, DPI / 25.4, false);
    }

    /** Draws a copy with its bleed at any scale, for example for the editor's canvas. */
    public BufferedImage draw(DesignDocument document, PrintTemplate template, double pixelsPerMm, boolean guides)
            throws IOException {
        double bleed = template.bleedMm();
        int width = (int) Math.round((template.width(document.spineMm()) + 2 * bleed) * pixelsPerMm);
        int height = (int) Math.round((template.heightMm() + 2 * bleed) * pixelsPerMm);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            Java2dPainter painter = new Java2dPainter(graphics, pixelsPerMm, renderer.images());
            painter.translate(bleed, bleed);
            renderer.render(document, template, painter, guides);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public void export(DesignDocument document, PrintTemplate template, Path target) throws IOException {
        write(draw(document, template), target);
    }

    static void write(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            IIOMetadata metadata =
                    writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), parameters);
            IIOMetadataNode physical = new IIOMetadataNode("pHYs");
            physical.setAttribute("pixelsPerUnitXAxis", Integer.toString(PIXELS_PER_METRE));
            physical.setAttribute("pixelsPerUnitYAxis", Integer.toString(PIXELS_PER_METRE));
            physical.setAttribute("unitSpecifier", "meter");
            IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
            root.appendChild(physical);
            metadata.mergeTree("javax_imageio_png_1.0", root);
            try (ImageOutputStream output = ImageIO.createImageOutputStream(temporary.toFile())) {
                writer.setOutput(output);
                writer.write(null, new IIOImage(image, null, metadata), parameters);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        } finally {
            writer.dispose();
        }
    }
}
