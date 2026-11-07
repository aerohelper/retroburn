// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * The image files a design uses, by the SHA-256 of their bytes. The bytes are kept as imported, so a
 * PDF gets the full resolution and JPEG files are passed through unchanged.
 */
public final class ImageStore {

    /** File types the designer imports, by their first bytes. */
    public enum Format {
        PNG("png"),
        JPEG("jpg"),
        GIF("gif"),
        BMP("bmp");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }

        static Optional<Format> sniff(byte[] bytes) {
            if (bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 'P'
                    && bytes[2] == 'N'
                    && bytes[3] == 'G') {
                return Optional.of(PNG);
            }
            if (bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF) {
                return Optional.of(JPEG);
            }
            if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
                return Optional.of(GIF);
            }
            if (bytes.length >= 2 && bytes[0] == 'B' && bytes[1] == 'M') {
                return Optional.of(BMP);
            }
            return Optional.empty();
        }

        static Optional<Format> fromExtension(String extension) {
            for (Format format : values()) {
                if (format.extension.equals(extension)) {
                    return Optional.of(format);
                }
            }
            return Optional.empty();
        }
    }

    /** About 18 000 × 6 000: far beyond 300 dpi on any cover, small enough to decode safely. */
    static final long MAX_PIXELS = 100_000_000L;

    private final Map<String, byte[]> bytes = new ConcurrentHashMap<>();
    private final Map<String, BufferedImage> decoded = new ConcurrentHashMap<>();

    /**
     * Adds an image file's bytes.
     *
     * @return its id
     * @throws IllegalArgumentException if the bytes are not a PNG, JPEG, GIF or BMP image Java can read
     */
    public String add(byte[] file) {
        if (Format.sniff(file).isEmpty()) {
            throw new IllegalArgumentException("Not a PNG, JPEG, GIF or BMP image");
        }
        BufferedImage image = decode(file);
        String id = sha256(file);
        bytes.putIfAbsent(id, file.clone());
        decoded.putIfAbsent(id, image);
        return id;
    }

    public boolean contains(String id) {
        return bytes.containsKey(id);
    }

    public Set<String> ids() {
        return Set.copyOf(bytes.keySet());
    }

    public byte[] bytes(String id) {
        byte[] file = bytes.get(id);
        if (file == null) {
            throw new NoSuchElementException("No image " + id);
        }
        return file.clone();
    }

    public Format format(String id) {
        return Format.sniff(bytes(id)).orElseThrow();
    }

    /** The decoded image, for drawing on screen and in PNG files. */
    public BufferedImage image(String id) {
        BufferedImage image = decoded.get(id);
        if (image == null) {
            throw new NoSuchElementException("No image " + id);
        }
        return image;
    }

    static String sha256(byte[] file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from the Java runtime", e);
        }
    }

    /**
     * Reads the size from the header first and refuses pictures larger than {@value #MAX_PIXELS}
     * pixels, so a small file that claims a huge picture cannot exhaust memory (threat model T5).
     */
    private static BufferedImage decode(byte[] file) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException("The image could not be read");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS) {
                    throw new IllegalArgumentException("The image is larger than " + MAX_PIXELS + " pixels");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The image could not be read", e);
        }
    }
}
