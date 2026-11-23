// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.Messages;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The look and text of a reel: colours and fonts for the session's theme, the user-visible words,
 * and whether item names are hidden.
 *
 * @param itemNames the names shown in the progress scene (real ones, or "Item 1", "Item 2", …)
 */
public record ReelStyle(
        Color top,
        Color bottom,
        Color text,
        Color accent,
        Color led,
        Font title,
        Font body,
        Messages messages,
        List<String> itemNames) {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1920;

    public static ReelStyle of(BurnSession session, Messages messages, boolean hideNames) {
        List<String> names = hideNames
                ? IntStream.rangeClosed(1, session.items().size())
                        .mapToObj(number -> messages.format("reel.item", number))
                        .toList()
                : session.items();
        Font lato = font("/fonts/lato/Lato-Bold.ttf");
        Font latoRegular = font("/fonts/lato/Lato-Regular.ttf");
        if ("y2k".equals(session.theme())) {
            Font vt323 = font("/fonts/vt323/VT323-Regular.ttf");
            return new ReelStyle(
                    new Color(0x0B1E3A),
                    new Color(0x3C1A5C),
                    new Color(0x7CF5FF),
                    new Color(0xC04BFF),
                    new Color(0x39FF14),
                    vt323,
                    vt323,
                    messages,
                    names);
        }
        return new ReelStyle(
                new Color(0x0F1B2D),
                new Color(0x1F3B66),
                Color.WHITE,
                new Color(0xF2B33D),
                new Color(0xF2B33D),
                lato,
                latoRegular,
                messages,
                names);
    }

    public String kindName(BurnSession session) {
        return messages.get("reel.kind." + session.kind());
    }

    public String size(long bytes) {
        return ByteSizes.format(bytes);
    }

    public String duration(Duration duration) {
        long minutes = duration.toMinutes();
        int seconds = duration.toSecondsPart();
        return minutes > 0
                ? messages.format("reel.minutes", minutes, seconds)
                : messages.format("reel.seconds", seconds);
    }

    /** The step's text, or empty for an unknown or argument-taking step. */
    public String step(String key) {
        if (key.isEmpty()) {
            return "";
        }
        try {
            return messages.get(key);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static Font font(String resource) {
        try (InputStream input = ReelStyle.class.getResourceAsStream(resource)) {
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
