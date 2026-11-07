// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Writes a sheet, or the alignment test page, as a one-page PDF of the paper's exact size, with
 * embedded font subsets (ADR-0016). Files are written to a temporary name first, then moved.
 */
public final class PdfExporter {

    private final DesignRenderer renderer;

    public PdfExporter(DesignRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * @throws PdfPainter.FontNotEmbeddableException if the design uses a font that forbids embedding
     */
    public void export(DesignDocument document, PrintTemplate template, SheetLayout.Sheet sheet, Path target)
            throws IOException {
        write(
                target,
                sheet.widthMm(),
                sheet.heightMm(),
                painter -> new SheetRenderer(renderer).render(document, template, sheet, painter));
    }

    public void exportAlignmentPage(Paper paper, String headline, Path target) throws IOException {
        write(
                target,
                paper.widthMm(),
                paper.heightMm(),
                painter -> AlignmentPage.render(paper, renderer.fonts(), headline, painter));
    }

    private interface Drawing {
        void draw(Painter painter) throws IOException;
    }

    private void write(Path target, double widthMm, double heightMm, Drawing drawing) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try (PDDocument pdf = new PDDocument()) {
            PDDocumentInformation information = pdf.getDocumentInformation();
            information.setCreator("RetroBurn");
            PDPage page = new PDPage(new PDRectangle(
                    (float) widthMm * PdfPainter.POINTS_PER_MM, (float) heightMm * PdfPainter.POINTS_PER_MM));
            pdf.addPage(page);
            Map<FontFace, PDType0Font> fonts = new HashMap<>();
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                drawing.draw(new PdfPainter(pdf, stream, heightMm, renderer.images(), fonts));
            }
            pdf.save(temporary.toFile());
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }
}
