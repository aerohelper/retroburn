// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Node;

/** PDF and PNG output: page sizes, embedded fonts, the disc label's diameter, PNG resolution. */
class ExportTest {

    private static final double POINTS_PER_MM = 72 / 25.4;

    @TempDir
    Path folder;

    private final PrintTemplates templates = PrintTemplates.builtIn();
    private final CoverContent content = new CoverContent(
            "Audio CD", "Summer Mix 2026", "Various", List.of("1. Opening — Band (3:12)", "2. Grüße (4:01)"));

    private DesignRenderer renderer(ImageStore images) {
        return new DesignRenderer(FontCatalog.bundled(), images);
    }

    private PDDocument export(String templateId, Paper paper, String preset, ImageStore images) throws IOException {
        PrintTemplate template = templates.get(templateId);
        DesignDocument document = Presets.create(preset, template, content);
        if (!images.ids().isEmpty()) {
            List<DesignElement> elements = new ArrayList<>(document.elements());
            elements.add(new DesignElement.Image(
                    "image-1", Frame.of(20, 20, 40, 30), images.ids().iterator().next()));
            document = document.withElements(elements);
        }
        SheetLayout.Sheet sheet =
                SheetLayout.plan(template, document.spineMm(), paper).orElseThrow();
        Path file = folder.resolve(templateId + "-" + paper + ".pdf");
        new PdfExporter(renderer(images)).export(document, template, sheet, file);
        return Loader.loadPDF(file.toFile());
    }

    @Test
    void pageSizeIsTheExactPaperSize() throws IOException {
        try (PDDocument a4 = export("cd-front", Paper.A4, "classic", new ImageStore())) {
            assertEquals(1, a4.getNumberOfPages());
            assertEquals(210 * POINTS_PER_MM, a4.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(297 * POINTS_PER_MM, a4.getPage(0).getMediaBox().getHeight(), 0.01);
        }
        try (PDDocument letter = export("dvd-wrap", Paper.LETTER, "y2k", new ImageStore())) {
            // Landscape: the wrap only fits across the long side.
            assertEquals(279.4 * POINTS_PER_MM, letter.getPage(0).getMediaBox().getWidth(), 0.01);
            assertEquals(215.9 * POINTS_PER_MM, letter.getPage(0).getMediaBox().getHeight(), 0.01);
        }
    }

    @Test
    void everyFontIsEmbedded() throws IOException {
        for (String preset : Presets.NAMES) {
            try (PDDocument pdf = export("cd-back", Paper.A4, preset, new ImageStore())) {
                PDResources resources = pdf.getPage(0).getResources();
                List<COSName> names = new ArrayList<>();
                resources.getFontNames().forEach(names::add);
                assertFalse(names.isEmpty(), preset + " uses text");
                for (COSName name : names) {
                    PDFont font = resources.getFont(name);
                    assertTrue(font.isEmbedded(), preset + ": " + font.getName() + " is not embedded");
                }
            }
        }
    }

    @Test
    void discLabelCutCircleIs118Millimetres() throws IOException {
        try (PDDocument pdf = export("disc-label", Paper.A4, "classic", new ImageStore())) {
            List<Rectangle2D> stroked = strokedPaths(pdf, pdf.getPage(0));
            long cutCircles = stroked.stream()
                    .filter(box -> Math.abs(box.getWidth() / POINTS_PER_MM - 118) <= 0.5)
                    .filter(box -> Math.abs(box.getHeight() / POINTS_PER_MM - 118) <= 0.5)
                    .count();
            assertEquals(2, cutCircles, "one cut circle per label: " + stroked);
            long holes = stroked.stream()
                    .filter(box -> Math.abs(box.getWidth() / POINTS_PER_MM - 41) <= 0.5)
                    .count();
            assertTrue(holes >= 2, "hole circles: " + stroked);
        }
    }

    @Test
    void imagesKeepTheirPixels() throws IOException {
        ImageStore images = new ImageStore();
        images.add(DesignFileTest.png(1200, 900));
        try (PDDocument pdf = export("cd-front", Paper.A4, "minimal", images)) {
            PDResources resources = pdf.getPage(0).getResources();
            List<PDImage> found = new ArrayList<>();
            for (COSName name : resources.getXObjectNames()) {
                if (resources.getXObject(name) instanceof PDImageXObject image) {
                    found.add(image);
                }
            }
            assertEquals(1, found.size(), "one image object, used by both copies");
            assertEquals(1200, found.getFirst().getWidth());
            assertEquals(900, found.getFirst().getHeight());
        }
    }

    @Test
    void aFontThatForbidsEmbeddingStopsThePdf() throws IOException {
        FontFace locked = new FontFace("Locked", false, "test", false, () -> new byte[0]);
        try (PDDocument pdf = new PDDocument()) {
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                PdfPainter painter = new PdfPainter(pdf, stream, 297, new ImageStore(), new HashMap<>());
                PdfPainter.FontNotEmbeddableException error = assertThrows(
                        PdfPainter.FontNotEmbeddableException.class,
                        () -> painter.text("Hi", locked, 4, "#000000", 10, 10));
                assertEquals(locked, error.face());
            }
        }
    }

    @Test
    void alignmentPageIsThePaperSize() throws IOException {
        Path file = folder.resolve("test-page.pdf");
        new PdfExporter(renderer(new ImageStore()))
                .exportAlignmentPage(Paper.LETTER, "Each ruler should measure 100 mm", file);
        try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
            assertEquals(215.9 * POINTS_PER_MM, pdf.getPage(0).getMediaBox().getWidth(), 0.01);
            List<Rectangle2D> stroked = strokedPaths(pdf, pdf.getPage(0));
            assertTrue(
                    stroked.stream()
                            .anyMatch(box -> Math.abs(box.getWidth() / POINTS_PER_MM - 100) <= 0.1
                                    && Math.abs(box.getHeight() / POINTS_PER_MM - 100) <= 0.1),
                    "100 mm square");
        }
    }

    @Test
    void pngIsThreeHundredDpiWithItsBleed() throws IOException {
        DesignRenderer renderer = renderer(new ImageStore());
        PngExporter exporter = new PngExporter(renderer);
        for (PrintTemplate template : templates.all()) {
            DesignDocument document = Presets.create("y2k", template, content);
            BufferedImage image = exporter.draw(document, template);
            double widthMm = template.width(document.spineMm()) + 2 * template.bleedMm();
            assertEquals(Math.round(widthMm * 300 / 25.4), image.getWidth(), template.id());
        }
        PrintTemplate label = templates.get("disc-label");
        BufferedImage disc = exporter.draw(Presets.create("classic", label, content), label);
        assertEquals(0, disc.getRGB(2, 2) >>> 24, "transparent outside the disc");
        assertEquals(0, disc.getRGB(disc.getWidth() / 2, disc.getHeight() / 2) >>> 24, "transparent hole");

        Path file = folder.resolve("label.png");
        exporter.export(Presets.create("classic", label, content), label, file);
        assertEquals(11811, pixelsPerMetre(file));
    }

    private static int pixelsPerMetre(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            reader.setInput(input);
            IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(0).getAsTree("javax_imageio_png_1.0");
            Node physical = root.getElementsByTagName("pHYs").item(0);
            assertEquals("meter", ((IIOMetadataNode) physical).getAttribute("unitSpecifier"));
            return Integer.parseInt(((IIOMetadataNode) physical).getAttribute("pixelsPerUnitXAxis"));
        }
    }

    /** Bounding boxes, in points, of every stroked path on a page. */
    static List<Rectangle2D> strokedPaths(PDDocument pdf, PDPage page) throws IOException {
        List<Rectangle2D> boxes = new ArrayList<>();
        new PDFGraphicsStreamEngine(page) {
            private Rectangle2D current;

            private void add(Point2D point) {
                if (current == null) {
                    current = new Rectangle2D.Double(point.getX(), point.getY(), 0, 0);
                } else {
                    current.add(point);
                }
            }

            @Override
            public void moveTo(float x, float y) {
                add(new Point2D.Float(x, y));
            }

            @Override
            public void lineTo(float x, float y) {
                add(new Point2D.Float(x, y));
            }

            @Override
            public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
                add(new Point2D.Float(x1, y1));
                add(new Point2D.Float(x2, y2));
                add(new Point2D.Float(x3, y3));
            }

            @Override
            public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
                add(p0);
                add(p1);
                add(p2);
                add(p3);
            }

            @Override
            public void strokePath() {
                if (current != null) {
                    boxes.add(current);
                }
                current = null;
            }

            @Override
            public void fillPath(int windingRule) {
                current = null;
            }

            @Override
            public void fillAndStrokePath(int windingRule) {
                strokePath();
            }

            @Override
            public void clip(int windingRule) {}

            @Override
            public void endPath() {
                current = null;
            }

            @Override
            public void closePath() {}

            @Override
            public Point2D getCurrentPoint() {
                return new Point2D.Float(0, 0);
            }

            @Override
            public void drawImage(PDImage image) {}

            @Override
            public void shadingFill(COSName name) {}

            void run() throws IOException {
                processPage(page);
            }
        }.run();
        assertTrue(pdf.getNumberOfPages() > 0);
        return boxes;
    }
}
