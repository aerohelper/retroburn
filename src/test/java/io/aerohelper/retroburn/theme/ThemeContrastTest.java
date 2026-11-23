// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every theme's text and background pairs meet WCAG 2.1 AA: 4.5:1 for body text. Colours are read
 * from the stylesheets' tokens (a theme's own first, then the classic ones it imports), so changing
 * a token re-checks it.
 */
class ThemeContrastTest {

    private static final Pattern TOKEN = Pattern.compile("(-rb-[a-z0-9-]+):\\s*(#[0-9A-Fa-f]{6})\\s*;");

    private static final List<String[]> PAIRS = List.of(
            new String[] {"-rb-text", "-rb-bg"},
            new String[] {"-rb-text", "-rb-surface"},
            new String[] {"-rb-text-muted", "-rb-bg"},
            new String[] {"-rb-text-muted", "-rb-surface"},
            new String[] {"-rb-accent-text", "-rb-accent"},
            new String[] {"-rb-success", "-rb-bg"},
            new String[] {"-rb-warning", "-rb-bg"},
            new String[] {"-rb-error", "-rb-bg"},
            new String[] {"-rb-warning", "-rb-surface"},
            new String[] {"-rb-error", "-rb-surface"},
            new String[] {"#FFFFFF", "-rb-error"},
            new String[] {"-rb-text-muted", "-rb-disabled-bg"});

    static Stream<Arguments> pairs() {
        Stream<Arguments> shared = ThemeRegistry.IDS.stream()
                .flatMap(theme -> PAIRS.stream().map(pair -> Arguments.of(theme, pair[0], pair[1])));
        return Stream.concat(shared, Stream.of(Arguments.of("y2k", "-rb-led", "-rb-led-bg")));
    }

    @ParameterizedTest(name = "{0}: {1} on {2}")
    @MethodSource("pairs")
    void bodyTextMeetsAa(String theme, String foreground, String background) throws IOException {
        Map<String, String> tokens = tokens(theme);
        double ratio = contrast(colour(foreground, tokens), colour(background, tokens));
        assertTrue(ratio >= 4.5, theme + ": " + foreground + " on " + background + ": " + ratio);
    }

    private static Map<String, String> tokens(String theme) throws IOException {
        Map<String, String> tokens = new HashMap<>();
        read("/themes/" + theme + "/theme.css", tokens);
        read("/themes/classic/theme.css", tokens);
        return tokens;
    }

    private static void read(String resource, Map<String, String> tokens) throws IOException {
        try (InputStream input = ThemeContrastTest.class.getResourceAsStream(resource)) {
            String css = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = TOKEN.matcher(css);
            while (matcher.find()) {
                tokens.putIfAbsent(matcher.group(1), matcher.group(2));
            }
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
