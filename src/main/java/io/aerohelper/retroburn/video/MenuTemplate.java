// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.awt.Color;
import java.util.Locale;

/**
 * The look of a DVD menu: colours and fonts, as data. Fonts are the JDK's logical fonts until bundled
 * fonts arrive with the skins. See {@code docs/design/LLD-video-dvd.md} section 4.
 *
 * @param id the template's name in settings
 * @param top background colour at the top
 * @param bottom background colour at the bottom
 * @param text text colour
 * @param highlight outline colour of the button the remote is on
 * @param select outline colour of a pressed button
 * @param font logical font family
 * @param framedThumbnails draw a frame around each thumbnail
 */
public record MenuTemplate(
        String id,
        Color top,
        Color bottom,
        Color text,
        Color highlight,
        Color select,
        String font,
        boolean framedThumbnails) {

    public static final MenuTemplate CLASSIC = new MenuTemplate(
            "classic",
            new Color(0x1B2A49),
            new Color(0x0B1222),
            new Color(0xF2F2F2),
            new Color(0xFFD23F),
            new Color(0xFF6B35),
            "Serif",
            true);
    public static final MenuTemplate Y2K = new MenuTemplate(
            "y2k",
            new Color(0x3A1C71),
            new Color(0x0F4C81),
            new Color(0xE6F7FF),
            new Color(0x5CFFE1),
            new Color(0xFF4FD8),
            "Monospaced",
            true);
    public static final MenuTemplate MINIMAL = new MenuTemplate(
            "minimal",
            new Color(0x202020),
            new Color(0x202020),
            new Color(0xFFFFFF),
            new Color(0xFFFFFF),
            new Color(0xAAAAAA),
            "SansSerif",
            false);

    /** The template named {@code id}, or {@link #CLASSIC}. */
    public static MenuTemplate named(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "y2k" -> Y2K;
            case "minimal" -> MINIMAL;
            default -> CLASSIC;
        };
    }
}
