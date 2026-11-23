// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.apache.fontbox.ttf.FontHeaders;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.pdfbox.io.RandomAccessReadBufferedFile;

/**
 * The fonts the designer offers: the bundled OFL fonts first, then the computer's TrueType fonts.
 * See ADR-0016: only {@code .ttf} files with TrueType outlines; a font whose licence forbids
 * embedding is listed but marked.
 */
public final class FontCatalog {

    /** The default family, always present. */
    public static final String DEFAULT_FAMILY = "Lato";

    static final List<String> BUNDLED =
            List.of("/fonts/lato/Lato-Regular.ttf", "/fonts/lato/Lato-Bold.ttf", "/fonts/vt323/VT323-Regular.ttf");

    /** At most this many font files are read from the computer's folders. */
    static final int MAX_SYSTEM_FILES = 4000;

    private static final Logger LOG = Logger.getLogger(FontCatalog.class.getName());

    private final List<FontFace> faces;

    private FontCatalog(List<FontFace> faces) {
        this.faces = List.copyOf(faces);
    }

    /** Only the bundled fonts; quick, for start-up and tests. */
    public static FontCatalog bundled() {
        return new FontCatalog(bundledFaces());
    }

    /** The bundled fonts plus the TrueType files found under {@code folders}. Reads files: run off the UI thread. */
    public static FontCatalog withSystemFonts(List<Path> folders) {
        List<FontFace> faces = new ArrayList<>(bundledFaces());
        List<FontFace> system = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        for (Path folder : folders) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(folder, 4)) {
                walk.filter(path -> path.getFileName()
                                .toString()
                                .toLowerCase(Locale.ROOT)
                                .endsWith(".ttf"))
                        .filter(Files::isRegularFile)
                        .limit(MAX_SYSTEM_FILES - files.size())
                        .forEach(files::add);
            } catch (IOException | UncheckedIOException e) {
                LOG.log(Level.INFO, "Could not list fonts in " + folder, e);
            }
        }
        for (Path file : files) {
            readHeaders(file).ifPresent(system::add);
        }
        system.sort(Comparator.comparing((FontFace face) -> face.family().toLowerCase(Locale.ROOT))
                .thenComparing(FontFace::bold));
        faces.addAll(system);
        return new FontCatalog(faces);
    }

    public List<FontFace> faces() {
        return faces;
    }

    /** Family names in display order, each once. */
    public List<String> families() {
        return faces.stream().map(FontFace::family).distinct().toList();
    }

    /** Whether every face of the family may be embedded in a PDF. */
    public boolean embeddable(String family) {
        return faces.stream().filter(face -> face.family().equals(family)).allMatch(FontFace::embeddable);
    }

    /**
     * The face for a family and weight: the bold face if asked for and present, else the regular one;
     * an unknown family falls back to {@value #DEFAULT_FAMILY}.
     */
    public FontFace face(String family, boolean bold) {
        Map<Boolean, FontFace> byWeight = new LinkedHashMap<>();
        for (FontFace face : faces) {
            if (face.family().equals(family)) {
                byWeight.putIfAbsent(face.bold(), face);
            }
        }
        if (byWeight.isEmpty()) {
            return family.equals(DEFAULT_FAMILY) ? faces.getFirst() : face(DEFAULT_FAMILY, bold);
        }
        FontFace wanted = byWeight.get(bold);
        return wanted != null ? wanted : byWeight.values().iterator().next();
    }

    /**
     * Whether an {@code OS/2} {@code fsType} allows embedding: not "Restricted License embedding"
     * (usage value 2) and not bitmap-only (bit 9). OpenType specification, OS/2 table.
     */
    static boolean embeddable(int fsType) {
        int usage = fsType & 0x000F;
        return usage != OS2WindowsMetricsTable.FSTYPE_RESTRICTED
                && (fsType & OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY) == 0;
    }

    static Optional<FontFace> readHeaders(Path file) {
        try (RandomAccessReadBufferedFile input = new RandomAccessReadBufferedFile(file.toFile())) {
            FontHeaders headers = new TTFParser().parseTableHeaders(input);
            if (headers.getError() != null || headers.isOpenTypePostScript() || headers.getFontFamily() == null) {
                return Optional.empty();
            }
            String subfamily = headers.getFontSubFamily() == null ? "" : headers.getFontSubFamily();
            if (!isRegularOrBold(subfamily)) {
                return Optional.empty();
            }
            OS2WindowsMetricsTable os2 = headers.getOS2Windows();
            boolean embeddable = os2 == null || embeddable(os2.getFsType());
            return Optional.of(new FontFace(
                    headers.getFontFamily(),
                    subfamily.equalsIgnoreCase("Bold"),
                    file.toString(),
                    embeddable,
                    () -> readFile(file)));
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.FINE, "Skipping font " + file, e);
            return Optional.empty();
        }
    }

    /** Italic and other styles are left out: the designer has a bold switch only. */
    private static boolean isRegularOrBold(String subfamily) {
        return subfamily.equalsIgnoreCase("Regular") || subfamily.equalsIgnoreCase("Bold");
    }

    private static List<FontFace> bundledFaces() {
        return BUNDLED.stream()
                .map(resource -> {
                    String name = resource.substring(resource.lastIndexOf('/') + 1);
                    String family = name.substring(0, name.indexOf('-'));
                    return new FontFace(family, name.contains("-Bold"), resource, true, () -> readResource(resource));
                })
                .toList();
    }

    private static byte[] readResource(String resource) {
        try (InputStream input = FontCatalog.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled font " + resource);
            }
            return input.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + resource, e);
        }
    }

    private static byte[] readFile(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
