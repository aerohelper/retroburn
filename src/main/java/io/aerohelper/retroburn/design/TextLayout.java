// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.util.ArrayList;
import java.util.List;

/**
 * Breaks text into lines that fit a width, using the font file's own advance widths so the screen,
 * PNG and PDF break at the same words (ADR-0016). Sizes in millimetres.
 */
public final class TextLayout {

    /** Line height as a multiple of the font size. */
    public static final double LINE_HEIGHT = 1.2;

    /** Track lists shrink to fit, but not below this size. */
    public static final double MIN_FIT_SIZE_PT = 5;

    private static final double MM_PER_PT = 25.4 / 72;

    private TextLayout() {}

    public static double mm(double points) {
        return points * MM_PER_PT;
    }

    /**
     * The lines of {@code text}: its own line breaks are kept, and a line longer than {@code widthMm}
     * breaks at spaces; a single word wider than the box is cut between characters.
     */
    public static List<String> lines(String text, FontFace face, double sizeMm, double widthMm) {
        List<String> result = new ArrayList<>();
        // Split before dropping undrawable characters: fonts have no glyph for a line break.
        for (String paragraph : text.replace("\r", "").split("\n", -1)) {
            wrap(face.drawable(paragraph), face, sizeMm, widthMm, result);
        }
        return result;
    }

    /** Height of {@code lineCount} lines at a size, in millimetres. */
    public static double height(int lineCount, double sizeMm) {
        return lineCount * sizeMm * LINE_HEIGHT;
    }

    /**
     * The largest size, from {@code sizePt} down to {@value #MIN_FIT_SIZE_PT} pt in half points, at
     * which all lines fit the box.
     */
    public static double fitSizePt(List<String> lines, FontFace face, double sizePt, Frame box) {
        String text = String.join("\n", lines);
        for (double size = sizePt; size > MIN_FIT_SIZE_PT; size -= 0.5) {
            double sizeMm = mm(size);
            if (height(lines(text, face, sizeMm, box.width()).size(), sizeMm) <= box.height()) {
                return size;
            }
        }
        return MIN_FIT_SIZE_PT;
    }

    private static void wrap(String paragraph, FontFace face, double sizeMm, double widthMm, List<String> out) {
        if (paragraph.isEmpty()) {
            out.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ", -1)) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (face.width(candidate) * sizeMm <= widthMm) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
            }
            // The word alone may still be too wide: cut it.
            String rest = word;
            while (face.width(rest) * sizeMm > widthMm && rest.length() > 1) {
                int cut = rest.length() - 1;
                while (cut > 1 && face.width(rest.substring(0, cut)) * sizeMm > widthMm) {
                    cut--;
                }
                out.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        out.add(line.toString());
    }
}
