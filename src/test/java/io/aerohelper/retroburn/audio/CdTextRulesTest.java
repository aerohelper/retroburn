// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.aerohelper.retroburn.audio.CdTextRules.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CdTextRulesTest {

    @Test
    void plainTextIsUnchanged() {
        assertEquals(new CdTextRules.Field("Song of Joy", Status.UNCHANGED), CdTextRules.clean("Song of Joy"));
    }

    @Test
    void latinOneLettersAreKept() {
        assertEquals(
                new CdTextRules.Field("Café Müller, Niño", Status.UNCHANGED), CdTextRules.clean("Café Müller, Niño"));
    }

    @Test
    void decomposedInputIsComposedFirst() {
        // "e" followed by a combining acute accent becomes the single Latin-1 letter.
        assertEquals("Café", CdTextRules.clean("Café").text());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Dvořák|Dvorák",
                "Ōsaka|Osaka",
                "Erdős|Erdos",
            })
    void accentsOutsideLatinOneAreStripped(String input, String expected) {
        assertEquals(new CdTextRules.Field(expected, Status.ADJUSTED), CdTextRules.clean(input));
    }

    @Test
    void lettersWithoutADecompositionNeedALatinVersion() {
        // Ł is a letter of its own in Unicode, not L with a mark.
        assertEquals(Status.NEEDS_LATIN, CdTextRules.clean("Łódź").status());
    }

    @Test
    void typographicPunctuationBecomesLatinOne() {
        assertEquals(
                new CdTextRules.Field("Don't stop - \"live\"...", Status.ADJUSTED),
                CdTextRules.clean("Don’t stop – “live”…"));
    }

    @Test
    void otherScriptsNeedALatinVersion() {
        assertEquals(Status.NEEDS_LATIN, CdTextRules.clean("राग यमन").status());
        assertEquals(Status.NEEDS_LATIN, CdTextRules.clean("Raga यमन").status());
        assertEquals(Status.NEEDS_LATIN, CdTextRules.clean("Кино").status());
    }

    @Test
    void whitespaceIsTidiedAndControlsDropped() {
        assertEquals(
                new CdTextRules.Field("Side A Track 1", Status.ADJUSTED),
                CdTextRules.clean("  Side\tA\n Track\u0007 1  "));
    }

    @Test
    void symbolsWithoutLatinFormAreDropped() {
        assertEquals(new CdTextRules.Field("Party", Status.ADJUSTED), CdTextRules.clean("Party 🎉"));
    }

    @Test
    void lengthLimitIsEnforced() {
        String max = "x".repeat(CdTextRules.MAX_LENGTH);
        assertEquals(Status.UNCHANGED, CdTextRules.clean(max).status());
        assertEquals(Status.TOO_LONG, CdTextRules.clean(max + "x").status());
    }

    @Test
    void emptyStaysEmpty() {
        assertEquals(new CdTextRules.Field("", Status.UNCHANGED), CdTextRules.clean(""));
    }
}
