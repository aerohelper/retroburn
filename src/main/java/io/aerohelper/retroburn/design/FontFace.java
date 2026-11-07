// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Supplier;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

/**
 * One TrueType font file: its names, whether its licence allows embedding, and its metrics. The file
 * is read when first needed. Thread-safe.
 */
public final class FontFace {

    private final String family;
    private final boolean bold;
    private final String source;
    private final boolean embeddable;
    private final Supplier<byte[]> loader;

    private volatile Loaded loaded;

    private record Loaded(byte[] bytes, TrueTypeFont font, CmapLookup cmap, int unitsPerEm, double ascent, Font awt) {}

    /**
     * @param family family name from the font's name table
     * @param bold whether this is the bold face of the family
     * @param source where it came from, for messages and logs: a resource or a file path
     * @param embeddable false when the font's licence forbids embedding it in a PDF
     * @param loader reads the font file's bytes
     */
    public FontFace(String family, boolean bold, String source, boolean embeddable, Supplier<byte[]> loader) {
        this.family = Objects.requireNonNull(family, "family");
        this.bold = bold;
        this.source = Objects.requireNonNull(source, "source");
        this.embeddable = embeddable;
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public String family() {
        return family;
    }

    public boolean bold() {
        return bold;
    }

    public String source() {
        return source;
    }

    public boolean embeddable() {
        return embeddable;
    }

    public byte[] bytes() {
        return loaded().bytes().clone();
    }

    /** The font for Java2D drawing, at size 1. */
    public Font awtFont() {
        return loaded().awt();
    }

    /** Height above the baseline, in units of the font size (from the {@code hhea} table). */
    public double ascent() {
        return loaded().ascent();
    }

    /** Width of a string in units of the font size (multiply by the size). */
    public double width(String text) {
        Loaded font = loaded();
        try {
            long units = 0;
            for (int index = 0; index < text.length(); ) {
                int codePoint = text.codePointAt(index);
                units += font.font().getAdvanceWidth(font.cmap().getGlyphId(codePoint));
                index += Character.charCount(codePoint);
            }
            return units / (double) font.unitsPerEm();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the metrics of " + source, e);
        }
    }

    /**
     * The text with every character this font has no glyph for replaced by {@code ?} (or dropped if
     * the font has no {@code ?} either), so screen and PDF draw the same characters.
     */
    public String drawable(String text) {
        CmapLookup cmap = loaded().cmap();
        boolean hasQuestionMark = cmap.getGlyphId('?') != 0;
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (cmap.getGlyphId(codePoint) != 0) {
                result.appendCodePoint(codePoint);
            } else if (hasQuestionMark) {
                result.append('?');
            }
        });
        return result.toString();
    }

    private Loaded loaded() {
        Loaded current = loaded;
        if (current == null) {
            synchronized (this) {
                current = loaded;
                if (current == null) {
                    current = load();
                    loaded = current;
                }
            }
        }
        return current;
    }

    private Loaded load() {
        byte[] bytes = loader.get();
        try {
            TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(bytes));
            CmapLookup cmap = font.getUnicodeCmapLookup();
            Font awt = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            int unitsPerEm = font.getUnitsPerEm();
            double ascent = font.getHorizontalHeader().getAscender() / (double) unitsPerEm;
            return new Loaded(bytes, font, cmap, unitsPerEm, ascent, awt);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the font " + source, e);
        } catch (FontFormatException e) {
            throw new IllegalStateException("Not a usable TrueType font: " + source, e);
        }
    }

    @Override
    public String toString() {
        return family + (bold ? " Bold" : "") + " (" + source + ")";
    }
}
