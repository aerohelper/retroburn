// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bundled fonts (Lato, VT323) for DVD menus, so menus look the same on every computer. Other
 * family names fall back to Java's logical fonts.
 */
final class MenuFonts {

    private static final Map<String, String> FILES = Map.of(
            "Lato/plain", "/fonts/lato/Lato-Regular.ttf",
            "Lato/bold", "/fonts/lato/Lato-Bold.ttf",
            "VT323/plain", "/fonts/vt323/VT323-Regular.ttf",
            "VT323/bold", "/fonts/vt323/VT323-Regular.ttf");

    private static final Map<String, Font> LOADED = new ConcurrentHashMap<>();

    private MenuFonts() {}

    static Font font(String family, int style, float size) {
        String key = family + ((style & Font.BOLD) != 0 ? "/bold" : "/plain");
        String resource = FILES.get(key);
        if (resource == null) {
            return new Font(family, style, Math.round(size)).deriveFont(size);
        }
        return LOADED.computeIfAbsent(resource, MenuFonts::load).deriveFont(size);
    }

    private static Font load(String resource) {
        try (InputStream input = MenuFonts.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled font " + resource);
            }
            return Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("Not a usable font: " + resource, e);
        }
    }
}
