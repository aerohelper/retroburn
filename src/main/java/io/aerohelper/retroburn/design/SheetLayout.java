// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Places copies of an item on a sheet of paper: as many as fit, portrait or landscape, with crop
 * marks and bleed when there is room. See {@code docs/design/LLD-cover-designer.md} section 7.
 */
public final class SheetLayout {

    /** Most printers cannot print closer to the paper's edge. */
    public static final double PAGE_MARGIN_MM = 5;

    /** Last resort for an item that is only just narrower than the paper (a DVD wrap on Letter). */
    public static final double NARROW_MARGIN_MM = 3;

    /** Room around each copy's bleed for its crop marks. */
    public static final double MARK_SPACE_MM = 5;

    /** Crop mark length and gap to the bleed. */
    public static final double MARK_LENGTH_MM = 4;

    public static final double MARK_GAP_MM = 1;

    /**
     * A planned sheet.
     *
     * @param widthMm page width as printed (landscape swaps width and height)
     * @param heightMm page height as printed
     * @param landscape whether the paper is turned
     * @param itemWidthMm trim width of one copy
     * @param itemHeightMm trim height of one copy
     * @param bleedMm bleed around each copy, 0 when there is no room for it
     * @param cropMarks whether crop marks are drawn
     * @param origins top-left trim corner of each copy on the page
     * @param warnings message keys for the user, such as no room for crop marks
     */
    public record Sheet(
            double widthMm,
            double heightMm,
            boolean landscape,
            double itemWidthMm,
            double itemHeightMm,
            double bleedMm,
            boolean cropMarks,
            List<double[]> origins,
            List<String> warnings) {

        public Sheet {
            origins = List.copyOf(origins);
            warnings = List.copyOf(warnings);
        }

        public int copies() {
            return origins.size();
        }
    }

    private SheetLayout() {}

    /**
     * The sheet for a template, or empty when even one copy without bleed does not fit the paper.
     */
    public static Optional<Sheet> plan(PrintTemplate template, double spineMm, Paper paper) {
        double width = template.width(spineMm);
        double height = template.heightMm();
        // Disc labels get cut circles, not crop marks, so they need no room for marks.
        boolean marksWanted = !template.isDisc();
        Optional<Sheet> sheet = best(paper, PAGE_MARGIN_MM, width, height, template.bleedMm(), marksWanted, List.of());
        if (sheet.isEmpty() && marksWanted) {
            sheet = best(
                    paper,
                    PAGE_MARGIN_MM,
                    width,
                    height,
                    template.bleedMm(),
                    false,
                    List.of("design.sheet.noCropMarks"));
        }
        if (sheet.isEmpty()) {
            sheet = best(paper, PAGE_MARGIN_MM, width, height, 0, false, List.of("design.sheet.noBleed"));
        }
        if (sheet.isEmpty()) {
            sheet = best(
                    paper,
                    NARROW_MARGIN_MM,
                    width,
                    height,
                    0,
                    false,
                    List.of("design.sheet.noBleed", "design.sheet.narrowMargin"));
        }
        return sheet;
    }

    private static Optional<Sheet> best(
            Paper paper,
            double margin,
            double width,
            double height,
            double bleed,
            boolean marks,
            List<String> warnings) {
        Sheet portrait =
                arrange(paper.widthMm(), paper.heightMm(), false, margin, width, height, bleed, marks, warnings);
        Sheet landscape =
                arrange(paper.heightMm(), paper.widthMm(), true, margin, width, height, bleed, marks, warnings);
        Sheet better = landscape.copies() > portrait.copies() ? landscape : portrait;
        return better.copies() == 0 ? Optional.empty() : Optional.of(better);
    }

    private static Sheet arrange(
            double pageWidth,
            double pageHeight,
            boolean landscape,
            double margin,
            double width,
            double height,
            double bleed,
            boolean marks,
            List<String> warnings) {
        double pad = bleed + (marks ? MARK_SPACE_MM : 0);
        double cellWidth = width + 2 * pad;
        double cellHeight = height + 2 * pad;
        double usableWidth = pageWidth - 2 * margin;
        double usableHeight = pageHeight - 2 * margin;
        int columns = (int) Math.floor(usableWidth / cellWidth + 1e-9);
        int rows = (int) Math.floor(usableHeight / cellHeight + 1e-9);
        List<double[]> origins = new ArrayList<>();
        if (columns > 0 && rows > 0) {
            // Centre the block of copies on the page.
            double left = (pageWidth - columns * cellWidth) / 2;
            double top = (pageHeight - rows * cellHeight) / 2;
            for (int row = 0; row < rows; row++) {
                for (int column = 0; column < columns; column++) {
                    origins.add(new double[] {left + column * cellWidth + pad, top + row * cellHeight + pad});
                }
            }
        }
        return new Sheet(pageWidth, pageHeight, landscape, width, height, bleed, marks, origins, warnings);
    }
}
