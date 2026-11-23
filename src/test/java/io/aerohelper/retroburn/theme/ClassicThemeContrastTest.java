// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The classic theme's text and background pairs meet WCAG 2.1 AA: 4.5:1 for body text. Colours are
 * read from the stylesheet's tokens, so changing a token re-checks it.
 */
class ClassicThemeContrastTest {

    private static final Pattern TOKEN = Pattern.compile("(-rb-[a-z-]+):\\s*(#[0-9A-Fa-f]{6})\\s*;");

    @ParameterizedTest(name = "{0} on {1}")
    @CsvSource({
        "-rb-text, -rb-bg",
        "-rb-text, -rb-surface",
        "-rb-text-muted, -rb-bg",
        "-rb-text-muted, -rb-surface",
        "-rb-accent-text, -rb-accent",
        "-rb-success, -rb-bg",
        "-rb-warning, -rb-bg",
        "-rb-error, -rb-bg",
        "-rb-warning, -rb-surface",
        "-rb-error, -rb-surface",
        "#FFFFFF, -rb-error",
        "-rb-text-muted, -rb-disabled-bg",
    })
    void bodyTextMeetsAa(String foreground, String background) throws IOException {
        Map<String, String> tokens = tokens();
        double ratio = contrast(colour(foreground, tokens), colour(background, tokens));
        assertTrue(ratio >= 4.5, foreground + " on " + background + ": " + ratio);
    }

    private static Map<String, String> tokens() throws IOException {
        try (InputStream input = ClassicThemeContrastTest.class.getResourceAsStream("/themes/classic/theme.css")) {
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> tokens = new HashMap<>();
            Matcher matcher = TOKEN.matcher(css);
            while (matcher.find()) {
                tokens.putIfAbsent(matcher.group(1), matcher.group(2));
            }
            return tokens;
        }
    }

    private static String colour(String nameOrHex, Map<String, String> tokens) {
        String hex = nameOrHex.startsWith("#") ? nameOrHex : tokens.get(nameOrHex);
        if (hex == null) {
            throw new AssertionError("Token not defined in the theme: " + nameOrHex);
        }
        return hex;
    }

    /** WCAG 2.1 contrast ratio of two sRGB colours written as #RRGGBB. */
    static double contrast(String first, String second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return 0.2126 * channel(rgb >> 16) + 0.7152 * channel(rgb >> 8) + 0.0722 * channel(rgb);
    }

    private static double channel(int value) {
        double c = (value & 0xFF) / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
