// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import java.util.Objects;

/**
 * A disc's volume label that reads the same on Linux and Windows: at most 16 characters (the Joliet
 * limit), letters A–Z and a–z, digits, space, '_' and '-'. See {@code
 * docs/design/LLD-data-burning.md} section 2.1.
 *
 * @param text the label, already valid
 */
public record VolumeLabel(String text) {

    public static final int MAX_LENGTH = 16;
    public static final String FALLBACK = "RETROBURN";

    public VolumeLabel {
        Objects.requireNonNull(text, "text");
        if (!clean(text).equals(text)) {
            throw new IllegalArgumentException("Not a valid volume label: '" + text + "'");
        }
    }

    /** The closest valid label: other characters become '_', the end is cut, blank becomes RETROBURN. */
    public static VolumeLabel sanitize(String input) {
        return new VolumeLabel(clean(input));
    }

    /** True if {@code input} is valid as typed, so the UI can say when it had to change something. */
    public static boolean isValid(String input) {
        return clean(input).equals(input);
    }

    private static String clean(String input) {
        StringBuilder label = new StringBuilder();
        input.strip().codePoints().forEach(codePoint -> label.appendCodePoint(allowed(codePoint) ? codePoint : '_'));
        String text = label.length() > MAX_LENGTH ? label.substring(0, MAX_LENGTH) : label.toString();
        text = text.strip();
        return text.isEmpty() ? FALLBACK : text;
    }

    private static boolean allowed(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == ' '
                || codePoint == '_'
                || codePoint == '-';
    }
}
