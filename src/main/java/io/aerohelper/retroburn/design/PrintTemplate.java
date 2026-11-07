// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.List;
import java.util.Objects;

/**
 * The size and guides of one printed item, read from {@code print-templates/<id>.json}. Millimetres
 * from the item's top-left trim corner, y down. See {@code docs/PRINT_TEMPLATES.md}.
 *
 * @param schemaVersion version of the template file format
 * @param id file name without extension, also stored in design documents
 * @param shape rectangle or disc
 * @param widthMm trim width; for a template with an adjustable spine, without the spine
 * @param heightMm trim height
 * @param bleedMm how far artwork runs past the trim
 * @param safeMm keep text this far inside the trim
 * @param foldsMm x of fold lines, for templates with fixed spines
 * @param adjustableSpine whether the user chooses the spine width (DVD wrap): the spine sits in the
 *     middle, with folds at {@code widthMm / 2} and {@code widthMm / 2 + spine}
 * @param defaultSpineMm spine width for a new design, when adjustable
 * @param minSpineMm smallest spine, when adjustable
 * @param maxSpineMm largest spine, when adjustable
 * @param holeDiametersMm hole sizes offered for a disc label, the default first
 */
public record PrintTemplate(
        int schemaVersion,
        String id,
        Shape shape,
        double widthMm,
        double heightMm,
        double bleedMm,
        double safeMm,
        List<Double> foldsMm,
        boolean adjustableSpine,
        double defaultSpineMm,
        double minSpineMm,
        double maxSpineMm,
        List<Double> holeDiametersMm) {

    public enum Shape {
        RECTANGLE,
        DISC
    }

    public PrintTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(shape, "shape");
        foldsMm = foldsMm == null ? List.of() : List.copyOf(foldsMm);
        holeDiametersMm = holeDiametersMm == null ? List.of() : List.copyOf(holeDiametersMm);
        if (widthMm <= 0 || heightMm <= 0 || bleedMm < 0 || safeMm < 0) {
            throw new IllegalArgumentException("Bad size in print template " + id);
        }
        if (shape == Shape.DISC && (widthMm != heightMm || holeDiametersMm.isEmpty())) {
            throw new IllegalArgumentException("A disc template needs a square size and a hole: " + id);
        }
    }

    /** Trim width with the given spine (ignored unless the spine is adjustable). */
    public double width(double spineMm) {
        return adjustableSpine ? widthMm + clampSpine(spineMm) : widthMm;
    }

    /** Fold lines with the given spine. */
    public List<Double> folds(double spineMm) {
        if (!adjustableSpine) {
            return foldsMm;
        }
        double panel = widthMm / 2;
        return List.of(panel, panel + clampSpine(spineMm));
    }

    public double clampSpine(double spineMm) {
        return Math.clamp(spineMm, minSpineMm, maxSpineMm);
    }

    public boolean isDisc() {
        return shape == Shape.DISC;
    }
}
