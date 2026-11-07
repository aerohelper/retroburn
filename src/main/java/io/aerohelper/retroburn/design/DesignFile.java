// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.design;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saves and opens {@code .rbdesign} files: a ZIP with {@code design.json} and {@code
 * images/<sha256>.<ext>}. Opening is defensive (see the threat model): only those entry names, size
 * and count limits, and each image's hash checked against its name. See {@code
 * docs/design/LLD-cover-designer.md} section 9.
 */
public final class DesignFile {

    public static final String EXTENSION = ".rbdesign";

    static final String DOCUMENT_ENTRY = "design.json";
    static final int MAX_IMAGES = 200;
    static final long MAX_IMAGE_BYTES = 50L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 200L * 1024 * 1024;
    static final long MAX_DOCUMENT_BYTES = 8L * 1024 * 1024;

    private static final Pattern IMAGE_ENTRY = Pattern.compile("images/([0-9a-f]{64})\\.(png|jpg|gif|bmp)");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    /** A design and its images. */
    public record Opened(DesignDocument document, ImageStore images) {}

    /** The file is not a design this version can open; the message says why, for the log. */
    public static final class DesignFileException extends IOException {

        private final String reasonKey;

        public DesignFileException(String reasonKey, String detail) {
            super(detail);
            this.reasonKey = reasonKey;
        }

        /** Message key for the user: damaged file, or made by a newer version. */
        public String reasonKey() {
            return reasonKey;
        }
    }

    private DesignFile() {}

    public static void save(DesignDocument document, ImageStore images, Path target) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        try (OutputStream file = Files.newOutputStream(temporary);
                ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry(DOCUMENT_ENTRY));
            zip.write(MAPPER.writeValueAsBytes(document));
            zip.closeEntry();
            for (String id : document.imageIds()) {
                zip.putNextEntry(
                        new ZipEntry("images/" + id + "." + images.format(id).extension()));
                zip.write(images.bytes(id));
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(temporary);
            throw e;
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static Opened open(Path source) throws IOException {
        try (InputStream file = Files.newInputStream(source)) {
            return read(file);
        }
    }

    static Opened read(InputStream input) throws IOException {
        byte[] json = null;
        Map<String, byte[]> images = new HashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String name = entry.getName();
                if (name.equals(DOCUMENT_ENTRY)) {
                    if (json != null) {
                        throw damaged("two " + DOCUMENT_ENTRY + " entries");
                    }
                    json = readLimited(zip, MAX_DOCUMENT_BYTES, name);
                    total += json.length;
                    continue;
                }
                Matcher image = IMAGE_ENTRY.matcher(name);
                if (!image.matches()) {
                    throw damaged("unexpected entry " + name);
                }
                if (images.size() >= MAX_IMAGES) {
                    throw damaged("more than " + MAX_IMAGES + " images");
                }
                byte[] bytes = readLimited(zip, MAX_IMAGE_BYTES, name);
                total += bytes.length;
                if (total > MAX_TOTAL_BYTES) {
                    throw damaged("more than " + MAX_TOTAL_BYTES + " bytes in all");
                }
                if (!ImageStore.sha256(bytes).equals(image.group(1))) {
                    throw damaged("image " + name + " does not match its name");
                }
                images.put(image.group(1), bytes);
            }
        } catch (IllegalArgumentException e) {
            // ZipInputStream reports malformed names this way.
            throw damaged(e.getMessage());
        }
        if (json == null) {
            throw damaged("no " + DOCUMENT_ENTRY);
        }
        DesignDocument document = parse(json);
        ImageStore store = new ImageStore();
        for (Map.Entry<String, byte[]> image : images.entrySet()) {
            try {
                store.add(image.getValue());
            } catch (RuntimeException e) {
                throw damaged("image " + image.getKey() + " cannot be read");
            }
        }
        Optional<String> missing =
                document.imageIds().stream().filter(id -> !store.contains(id)).findFirst();
        if (missing.isPresent()) {
            throw damaged("image " + missing.get() + " is missing");
        }
        return new Opened(document, store);
    }

    private static DesignDocument parse(byte[] json) throws DesignFileException {
        DesignDocument document;
        try {
            document = MAPPER.readValue(json, DesignDocument.class);
        } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
            throw damaged(DOCUMENT_ENTRY + " cannot be read: " + e.getMessage());
        }
        if (document.schemaVersion() > DesignDocument.SCHEMA_VERSION) {
            throw new DesignFileException(
                    "design.open.newer", "schema version " + document.schemaVersion() + " is newer");
        }
        if (!PrintTemplates.IDS.contains(document.templateId())) {
            throw damaged("unknown template " + document.templateId());
        }
        return document;
    }

    private static byte[] readLimited(InputStream zip, long limit, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        long read = 0;
        for (int count; (count = zip.read(buffer)) > 0; ) {
            read += count;
            if (read > limit) {
                throw damaged(name + " is larger than " + limit + " bytes");
            }
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    private static DesignFileException damaged(String detail) {
        return new DesignFileException("design.open.damaged", detail);
    }
}
