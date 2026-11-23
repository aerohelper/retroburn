// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PrintTemplatesTest {

    private final PrintTemplates templates = PrintTemplates.builtIn();

    @Test
    void sizesMatchThePrintTemplatesDocument() {
        assertEquals(
                PrintTemplates.IDS,
                templates.all().stream().map(PrintTemplate::id).toList());
        PrintTemplate front = templates.get("cd-front");
        assertEquals(120, front.width(0));
        assertEquals(120, front.heightMm());
        assertEquals(3, front.bleedMm());

        PrintTemplate back = templates.get("cd-back");
        assertEquals(151, back.width(0));
        assertEquals(118, back.heightMm());
        assertEquals(List.of(6.5, 144.5), back.folds(0));

        PrintTemplate wrap = templates.get("dvd-wrap");
        assertEquals(273, wrap.width(14));
        assertEquals(183, wrap.heightMm());
        assertEquals(List.of(129.5, 143.5), wrap.folds(14));
        assertEquals(259 + 30, wrap.width(99), "spine clamped to 30 mm");
        assertEquals(259 + 5, wrap.width(1), "spine clamped to 5 mm");

        PrintTemplate label = templates.get("disc-label");
        assertTrue(label.isDisc());
        assertEquals(118, label.widthMm());
        assertEquals(List.of(41.0, 17.0), label.holeDiametersMm());
    }

    @Test
    void unknownIdIsAnError() {
        assertThrows(RuntimeException.class, () -> templates.get("lp-sleeve"));
        assertThrows(IllegalStateException.class, () -> PrintTemplates.read("lp-sleeve"));
    }

    @Test
    void discTemplatesNeedAHole() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrintTemplate(
                        1, "x", PrintTemplate.Shape.DISC, 118, 118, 3, 3, List.of(), false, 0, 0, 0, List.of()));
    }

    @Test
    void paperFollowsTheCountryUnlessSaved() {
        assertEquals(Paper.LETTER, Paper.forCountry("us"));
        assertEquals(Paper.A4, Paper.forCountry("IN"));
        assertEquals(Paper.A4, Paper.fromSetting("", Locale.forLanguageTag("en-IN")));
        assertEquals(Paper.LETTER, Paper.fromSetting("", Locale.forLanguageTag("en-CA")));
        assertEquals(Paper.A4, Paper.fromSetting("a4", Locale.US));
        assertFalse(Paper.LETTER.widthMm() == Paper.A4.widthMm());
    }
}
