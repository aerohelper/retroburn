// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.text.Normalizer;
import java.util.Map;

/**
 * Cleans one CD-Text field so it can be written in ISO-8859-1. See {@code
 * docs/design/LLD-audio-cd.md} section 4.1.
 */
public final class CdTextRules {

    /** Longest field RetroBurn accepts, in characters. */
    public static final int MAX_LENGTH = 80;

    private static final Map<Integer, String> PUNCTUATION = Map.ofEntries(
            Map.entry(0x2018, "'"),
            Map.entry(0x2019, "'"),
            Map.entry(0x201A, "'"),
            Map.entry(0x2032, "'"),
            Map.entry(0x201C, "\""),
            Map.entry(0x201D, "\""),
            Map.entry(0x201E, "\""),
            Map.entry(0x2033, "\""),
            Map.entry(0x2012, "-"),
            Map.entry(0x2013, "-"),
            Map.entry(0x2014, "-"),
            Map.entry(0x2212, "-"),
            Map.entry(0x2026, "..."));

    private CdTextRules() {}

    /** What happened to a field. */
    public enum Status {
        /** Written exactly as typed. */
        UNCHANGED,
        /** Written with small changes, shown to the user. */
        ADJUSTED,
        /** Has letters ISO-8859-1 cannot hold; a Latin-script version is needed. */
        NEEDS_LATIN,
        /** Longer than {@link #MAX_LENGTH}. */
        TOO_LONG;

        /** True if the field cannot be written as CD-Text. */
        public boolean blocksCdText() {
            return this == NEEDS_LATIN || this == TOO_LONG;
        }
    }

    /**
     * A cleaned field.
     *
     * @param text the text to write; only meaningful unless the status blocks CD-Text
     * @param status what happened to the input
     */
    public record Field(String text, Status status) {}

    /** Cleans {@code input} following the rules in the design. */
    public static Field clean(String input) {
        String text = whitespaceAndControls(Normalizer.normalize(input, Normalizer.Form.NFC));
        StringBuilder latin = new StringBuilder();
        boolean needsLatin = false;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint <= 0xFF) {
                latin.appendCodePoint(codePoint);
            } else if (PUNCTUATION.containsKey(codePoint)) {
                latin.append(PUNCTUATION.get(codePoint));
            } else {
                String stripped = withoutMarks(codePoint);
                if (!stripped.isEmpty() && stripped.chars().allMatch(c -> c <= 0xFF)) {
                    latin.append(stripped);
                } else if (Character.isLetterOrDigit(codePoint)) {
                    needsLatin = true;
                }
                // Other symbols (emoji, dingbats) are dropped.
            }
        }
        String cleaned = whitespaceAndControls(latin.toString());
        if (needsLatin) {
            return new Field(cleaned, Status.NEEDS_LATIN);
        }
        if (cleaned.length() > MAX_LENGTH) {
            return new Field(cleaned, Status.TOO_LONG);
        }
        return new Field(cleaned, cleaned.equals(input) ? Status.UNCHANGED : Status.ADJUSTED);
    }

    private static String withoutMarks(int codePoint) {
        String decomposed = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFD);
        StringBuilder base = new StringBuilder();
        decomposed
                .codePoints()
                .filter(c -> Character.getType(c) != Character.NON_SPACING_MARK)
                .forEach(base::appendCodePoint);
        return base.toString();
    }

    private static String whitespaceAndControls(String input) {
        StringBuilder out = new StringBuilder();
        boolean space = false;
        for (int index = 0; index < input.length(); ) {
            int codePoint = input.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                space = true;
            } else if (!Character.isISOControl(codePoint)) {
                if (space && !out.isEmpty()) {
                    out.append(' ');
                }
                space = false;
                out.appendCodePoint(codePoint);
            }
        }
        return out.toString();
    }
}
