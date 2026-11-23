// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Draws one item of a design into a {@link Painter}, in the item's coordinates (trim corner at the
 * origin). See {@code docs/design/LLD-cover-designer.md} section 6.
 */
public final class DesignRenderer {

    /** Colour of on-screen guides (safe zone, folds, disc edges). */
    static final String GUIDE_COLOUR = "#00A3D9";

    private final FontCatalog fonts;
    private final ImageStore images;

    public DesignRenderer(FontCatalog fonts, ImageStore images) {
        this.fonts = Objects.requireNonNull(fonts, "fonts");
        this.images = Objects.requireNonNull(images, "images");
    }

    public FontCatalog fonts() {
        return fonts;
    }

    public ImageStore images() {
        return images;
    }

    /**
     * Draws the background and the elements, clipped to the trim plus bleed (a disc label to its
     * ring), and the guides when {@code guides} is set.
     */
    public void render(DesignDocument document, PrintTemplate template, Painter painter, boolean guides)
            throws IOException {
        render(document, template, painter, guides, template.bleedMm());
    }

    /** As {@link #render(DesignDocument, PrintTemplate, Painter, boolean)} with another bleed (0 on a full sheet). */
    public void render(DesignDocument document, PrintTemplate template, Painter painter, boolean guides, double bleed)
            throws IOException {
        double width = template.width(document.spineMm());
        double height = template.heightMm();
        painter.save();
        if (template.isDisc()) {
            painter.clipRing(
                    width / 2, height / 2, width / 2 + bleed, Math.max(0, document.holeDiameterMm() / 2 - bleed));
        } else {
            painter.clipRect(-bleed, -bleed, width + 2 * bleed, height + 2 * bleed);
        }
        painter.fillRect(-bleed, -bleed, width + 2 * bleed, height + 2 * bleed, document.background());
        for (DesignElement element : document.elements()) {
            Frame frame = element.frame();
            painter.save();
            if (frame.rotationDegrees() != 0) {
                painter.rotate(frame.rotationDegrees(), frame.centerX(), frame.centerY());
            }
            draw(element, document.content(), painter);
            painter.restore();
        }
        painter.restore();
        if (guides) {
            drawGuides(document, template, painter);
        }
    }

    private void draw(DesignElement element, CoverContent content, Painter painter) throws IOException {
        Frame frame = element.frame();
        switch (element) {
            case DesignElement.ShapeElement shape -> {
                if (shape.kind() == DesignElement.ShapeElement.Kind.ELLIPSE) {
                    painter.fillEllipse(frame.x(), frame.y(), frame.width(), frame.height(), shape.fill());
                    painter.strokeEllipse(
                            frame.x(), frame.y(), frame.width(), frame.height(), shape.stroke(), shape.strokeMm());
                } else {
                    painter.fillRect(frame.x(), frame.y(), frame.width(), frame.height(), shape.fill());
                    painter.strokeRect(
                            frame.x(), frame.y(), frame.width(), frame.height(), shape.stroke(), shape.strokeMm());
                }
            }
            case DesignElement.Image image -> {
                if (images.contains(image.imageId())) {
                    painter.image(image.imageId(), frame.x(), frame.y(), frame.width(), frame.height());
                }
            }
            case DesignElement.Text text ->
                drawText(text.text(), text.style(), text.style().sizePt(), frame, painter);
            case DesignElement.DiscTitle title ->
                drawText(content.title(), title.style(), title.style().sizePt(), frame, painter);
            case DesignElement.TrackList list -> {
                FontFace face =
                        fonts.face(list.style().fontFamily(), list.style().bold());
                double size =
                        TextLayout.fitSizePt(content.lines(), face, list.style().sizePt(), frame);
                drawText(String.join("\n", content.lines()), list.style(), size, frame, painter);
            }
        }
    }

    private void drawText(String text, DesignElement.TextStyle style, double sizePt, Frame frame, Painter painter)
            throws IOException {
        FontFace face = fonts.face(style.fontFamily(), style.bold());
        double sizeMm = TextLayout.mm(sizePt);
        List<String> lines = TextLayout.lines(text, face, sizeMm, frame.width());
        double baseline = frame.y() + face.ascent() * sizeMm;
        for (String line : lines) {
            double lineWidth = face.width(line) * sizeMm;
            double x = switch (style.align()) {
                case LEFT -> frame.x();
                case CENTER -> frame.x() + (frame.width() - lineWidth) / 2;
                case RIGHT -> frame.right() - lineWidth;
            };
            painter.text(line, face, sizeMm, style.colour(), x, baseline);
            baseline += sizeMm * TextLayout.LINE_HEIGHT;
        }
    }

    private static void drawGuides(DesignDocument document, PrintTemplate template, Painter painter)
            throws IOException {
        double width = template.width(document.spineMm());
        double height = template.heightMm();
        double safe = template.safeMm();
        double line = 0.2;
        if (template.isDisc()) {
            double radius = width / 2;
            painter.strokeEllipse(0, 0, width, height, GUIDE_COLOUR, line);
            painter.strokeEllipse(safe, safe, width - 2 * safe, height - 2 * safe, GUIDE_COLOUR, line / 2);
            double hole = document.holeDiameterMm() / 2;
            painter.strokeEllipse(radius - hole, radius - hole, 2 * hole, 2 * hole, GUIDE_COLOUR, line);
            return;
        }
        painter.strokeRect(0, 0, width, height, GUIDE_COLOUR, line);
        painter.strokeRect(safe, safe, width - 2 * safe, height - 2 * safe, GUIDE_COLOUR, line / 2);
        for (double fold : template.folds(document.spineMm())) {
            painter.line(fold, 0, fold, height, GUIDE_COLOUR, line);
        }
    }
}
