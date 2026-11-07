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
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

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

    private static BufferedImage decode(byte[] file) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
            if (image == null) {
                throw new IllegalArgumentException("The image could not be read");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException("The image could not be read", e);
        }
    }
}
