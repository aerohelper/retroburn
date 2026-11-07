// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FontCatalogTest {

    @TempDir
    Path folder;

    @Test
    void bundledFontsComeFirst() {
        FontCatalog catalog = FontCatalog.bundled();
        assertEquals(List.of("Lato", "VT323"), catalog.families());
        assertTrue(catalog.face("Lato", true).bold());
        assertFalse(catalog.face("Lato", false).bold());
        assertEquals("VT323", catalog.face("VT323", true).family(), "no bold VT323: the regular face");
        assertEquals("Lato", catalog.face("Missing Sans", false).family());
        assertTrue(catalog.embeddable("Lato"));
    }

    @Test
    void fsTypeDecidesEmbedding() {
        assertTrue(FontCatalog.embeddable(0x0000), "installable");
        assertFalse(FontCatalog.embeddable(0x0002), "restricted licence embedding");
        assertTrue(FontCatalog.embeddable(0x0004), "preview and print");
        assertTrue(FontCatalog.embeddable(0x0008), "editable");
        assertFalse(FontCatalog.embeddable(0x0200), "bitmap only");
    }

    @Test
    void systemFontsAreReadFromTheirHeaders() throws IOException {
        Path fonts = Files.createDirectories(folder.resolve("fonts/sub"));
        Files.write(fonts.resolve("VT323-Regular.ttf"), bundled("/fonts/vt323/VT323-Regular.ttf"));
        Files.write(fonts.resolve("Restricted.ttf"), withFsType(bundled("/fonts/lato/Lato-Regular.ttf"), 0x0002));
        Files.writeString(fonts.resolve("broken.ttf"), "not a font");
        Files.writeString(fonts.resolve("readme.txt"), "ignored");

        FontCatalog catalog = FontCatalog.withSystemFonts(List.of(folder.resolve("fonts"), folder.resolve("none")));
        List<FontFace> system = catalog.faces()
                .subList(FontCatalog.BUNDLED.size(), catalog.faces().size());
        assertEquals(2, system.size(), system.toString());
        FontFace restricted = system.stream()
                .filter(face -> face.family().equals("Lato"))
                .findFirst()
                .orElseThrow();
        assertFalse(restricted.embeddable());
        assertFalse(catalog.embeddable("Lato"), "one face of the family forbids embedding");
        assertTrue(system.stream().anyMatch(face -> face.family().equals("VT323") && face.embeddable()));
    }

    @Test
    void metricsComeFromTheFontFile() {
        FontFace lato = FontCatalog.bundled().face("Lato", false);
        assertTrue(lato.width("W") > lato.width("i"));
        assertEquals(lato.width("ab"), lato.width("a") + lato.width("b"), 1e-9);
        assertTrue(lato.ascent() > 0.5 && lato.ascent() < 1.2, "ascent " + lato.ascent());
        assertEquals("Grüße ?", lato.drawable("Grüße 中"));
    }

    @Test
    void textWrapsAtSpacesAndCutsLongWords() {
        FontFace lato = FontCatalog.bundled().face("Lato", false);
        double size = TextLayout.mm(12);
        List<String> lines = TextLayout.lines("one two three four five six seven", lato, size, 25);
        assertTrue(lines.size() > 1, lines.toString());
        for (String line : lines) {
            assertTrue(lato.width(line) * size <= 25 + 1e-9, line);
        }
        assertEquals(String.join(" ", lines), "one two three four five six seven");
        assertEquals(List.of("a", "", "b"), TextLayout.lines("a\n\nb", lato, size, 25));
        List<String> cut = TextLayout.lines("Supercalifragilistic", lato, size, 10);
        assertTrue(cut.size() > 1);
        assertEquals("Supercalifragilistic", String.join("", cut));
    }

    @Test
    void trackListsShrinkToFit() {
        FontFace lato = FontCatalog.bundled().face("Lato", false);
        List<String> tracks = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(number -> number + ". Track")
                .toList();
        double fitted = TextLayout.fitSizePt(tracks, lato, 12, Frame.of(0, 0, 60, 100));
        assertTrue(fitted < 12);
        assertTrue(TextLayout.height(30, TextLayout.mm(fitted)) <= 100);
        assertEquals(12, TextLayout.fitSizePt(List.of("1. Short"), lato, 12, Frame.of(0, 0, 60, 100)));
        assertEquals(TextLayout.MIN_FIT_SIZE_PT, TextLayout.fitSizePt(tracks, lato, 12, Frame.of(0, 0, 60, 5)));
    }

    static byte[] bundled(String resource) throws IOException {
        try (var input = FontCatalogTest.class.getResourceAsStream(resource)) {
            return input.readAllBytes();
        }
    }

    /** A copy of a TrueType font with its OS/2 fsType changed (offset 8 in the OS/2 table). */
    static byte[] withFsType(byte[] font, int fsType) {
        byte[] copy = font.clone();
        ByteBuffer buffer = ByteBuffer.wrap(copy);
        int tables = buffer.getShort(4) & 0xFFFF;
        for (int index = 0; index < tables; index++) {
            int record = 12 + index * 16;
            String tag = new String(copy, record, 4, StandardCharsets.US_ASCII);
            if (tag.equals("OS/2")) {
                buffer.putShort(buffer.getInt(record + 8) + 8, (short) fsType);
                return copy;
            }
        }
        throw new IllegalArgumentException("No OS/2 table");
    }
}
