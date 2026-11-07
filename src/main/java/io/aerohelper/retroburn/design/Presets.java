// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import io.aerohelper.retroburn.design.DesignElement.Align;
import io.aerohelper.retroburn.design.DesignElement.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Starting designs: {@code classic}, {@code y2k} and {@code minimal}. Each places a disc title, a
 * track list where it makes sense, and a few shapes, inside the template's safe zone. All original
 * artwork: colours and shapes only.
 */
public final class Presets {

    public static final List<String> NAMES = List.of("classic", "y2k", "minimal");

    private record Look(
            String background, String text, String accent, String titleFont, String bodyFont, boolean frame) {}

    private Presets() {}

    public static DesignDocument create(String preset, PrintTemplate template, CoverContent content) {
        Look look = switch (preset.toLowerCase(Locale.ROOT)) {
            case "y2k" -> new Look("#0B1E3A", "#7CF5FF", "#C04BFF", "VT323", "VT323", true);
            case "minimal" ->
                new Look(
                        "#FFFFFF", "#1B1F24", "#1B1F24", FontCatalog.DEFAULT_FAMILY, FontCatalog.DEFAULT_FAMILY, false);
            default ->
                new Look("#1F3B66", "#FFFFFF", "#F2B33D", FontCatalog.DEFAULT_FAMILY, FontCatalog.DEFAULT_FAMILY, true);
        };
        double spine = template.adjustableSpine() ? template.defaultSpineMm() : 0;
        double hole = template.holeDiametersMm().isEmpty()
                ? 0
                : template.holeDiametersMm().getFirst();
        DesignDocument empty = new DesignDocument(
                DesignDocument.SCHEMA_VERSION,
                template.id(),
                spine,
                hole,
                preset.toLowerCase(Locale.ROOT),
                look.background(),
                content,
                List.of());
        return empty.withElements(elements(template, empty, look));
    }

    private static List<DesignElement> elements(PrintTemplate template, DesignDocument document, Look look) {
        double width = template.width(document.spineMm());
        double height = template.heightMm();
        double safe = template.safeMm() + 2;
        TextStyle title = new TextStyle(look.titleFont(), 20, true, look.text(), Align.CENTER);
        TextStyle body = new TextStyle(look.bodyFont(), 9, false, look.text(), Align.LEFT);
        List<DesignElement> elements = new ArrayList<>();
        if (template.isDisc()) {
            double hole = document.holeDiameterMm();
            if (look.frame()) {
                double ring = hole + 12;
                elements.add(new DesignElement.ShapeElement(
                        "shape-1",
                        Frame.of((width - ring) / 2, (height - ring) / 2, ring, ring),
                        DesignElement.ShapeElement.Kind.ELLIPSE,
                        "",
                        look.accent(),
                        1.2));
            }
            elements.add(new DesignElement.DiscTitle(
                    "title-1", Frame.of(safe + 8, height * 0.16, width - 2 * (safe + 8), 14), title));
            elements.add(new DesignElement.TrackList(
                    "tracks-1",
                    Frame.of(width * 0.2, height / 2 + hole / 2 + 4, width * 0.6, height * 0.22),
                    new TextStyle(look.bodyFont(), 7, false, look.text(), Align.CENTER)));
            return elements;
        }
        List<Double> folds = template.folds(document.spineMm());
        // The front is the right-hand panel of a wrap, else the whole item.
        double frontLeft = template.adjustableSpine() ? folds.getLast() : folds.isEmpty() ? 0 : folds.getFirst();
        double frontRight = template.adjustableSpine() ? width : folds.isEmpty() ? width : folds.getLast();
        double frontWidth = frontRight - frontLeft;
        if (look.frame()) {
            elements.add(new DesignElement.ShapeElement(
                    "shape-1",
                    Frame.of(frontLeft + safe, height * 0.62, frontWidth - 2 * safe, 1.5),
                    DesignElement.ShapeElement.Kind.RECTANGLE,
                    look.accent(),
                    "",
                    0));
        }
        elements.add(new DesignElement.DiscTitle(
                "title-1", Frame.of(frontLeft + safe, height * 0.66, frontWidth - 2 * safe, 24), title));
        boolean back = template.id().equals("cd-back") || template.adjustableSpine();
        if (back) {
            // The track list goes on the back: the middle of a tray inlay, the left panel of a wrap.
            double listLeft = template.adjustableSpine() ? 0 : folds.getFirst();
            double listRight = template.adjustableSpine() ? folds.getFirst() : folds.getLast();
            // On a tray inlay the title shares the middle panel, so the list stops above it.
            double listHeight = template.adjustableSpine() ? height - 2 * safe : height * 0.6 - safe;
            elements.add(new DesignElement.TrackList(
                    "tracks-1", Frame.of(listLeft + safe, safe, listRight - listLeft - 2 * safe, listHeight), body));
        }
        spineTitles(template, document, look, folds, height, elements);
        return elements;
    }

    /** The title along each spine, turned a quarter turn, sized to the spine's width. */
    private static void spineTitles(
            PrintTemplate template,
            DesignDocument document,
            Look look,
            List<Double> folds,
            double height,
            List<DesignElement> elements) {
        List<double[]> spines = new ArrayList<>();
        if (template.adjustableSpine()) {
            spines.add(new double[] {folds.getFirst(), folds.getLast()});
        } else if (folds.size() == 2) {
            spines.add(new double[] {0, folds.getFirst()});
            spines.add(new double[] {folds.getLast(), template.width(document.spineMm())});
        }
        int number = 1;
        for (double[] spine : spines) {
            double thickness = spine[1] - spine[0];
            double sizePt = Math.min(10, (thickness - 1) / TextLayout.mm(1) / TextLayout.LINE_HEIGHT);
            double length = height - 2 * template.safeMm();
            double centreX = (spine[0] + spine[1]) / 2;
            double boxHeight = thickness - 1;
            elements.add(new DesignElement.DiscTitle(
                    "spine-" + number++,
                    new Frame(centreX - length / 2, height / 2 - boxHeight / 2, length, boxHeight, 90),
                    new TextStyle(look.titleFont(), sizePt, true, look.text(), Align.CENTER)));
        }
    }
}
