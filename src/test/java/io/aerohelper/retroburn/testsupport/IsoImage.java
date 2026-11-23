// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.testsupport;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads just enough of an ISO 9660 image for tests: the volume descriptor set (ECMA-119 section 8:
 * type byte, "CD001", 2048-byte sectors from sector 16), the UDF volume recognition identifiers
 * that follow it (ECMA-167 part 2: "BEA01", "NSR02"/"NSR03", "TEA01"), and directory records
 * (ECMA-119 section 9.1: length, extent LBA at offset 2, size at 10, flags at 25, name at 33).
 */
public final class IsoImage implements AutoCloseable {

    private static final int SECTOR = 2048;

    private final RandomAccessFile file;

    public IsoImage(Path image) throws IOException {
        this.file = new RandomAccessFile(image.toFile(), "r");
    }

    /**
     * A directory entry.
     *
     * @param name the ISO 9660 name without the ";1" version
     * @param lba the first sector of its data
     * @param size its size in bytes
     * @param directory whether it is a folder
     */
    public record Entry(String name, long lba, long size, boolean directory) {}

    /** The 5-character identifiers of the descriptors from sector 16 on, up to the first unknown one. */
    public List<String> descriptorIdentifiers() throws IOException {
        List<String> identifiers = new ArrayList<>();
        for (int sector = 16; sector < 64; sector++) {
            byte[] data = sector(sector);
            String identifier = new String(data, 1, 5, StandardCharsets.US_ASCII);
            if (!identifier.matches("CD001|BEA01|NSR0[23]|TEA01|BOOT2|CDW02")) {
                break;
            }
            identifiers.add(identifier + (identifier.equals("CD001") ? "/" + (data[0] & 0xFF) : ""));
        }
        return identifiers;
    }

    /** The entries of the folder at {@code path} ("/" or "/VIDEO_TS"), in directory order. */
    public List<Entry> list(String path) throws IOException {
        byte[] pvd = sector(16);
        if (pvd[0] != 1) {
            throw new IOException("No primary volume descriptor at sector 16");
        }
        Entry current = record(pvd, 156);
        for (String part : path.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            current = read(current).stream()
                    .filter(entry -> entry.directory() && entry.name().equalsIgnoreCase(part))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No folder " + part + " in " + path));
        }
        return read(current);
    }

    private List<Entry> read(Entry directory) throws IOException {
        List<Entry> entries = new ArrayList<>();
        long sectors = Math.ceilDiv(directory.size(), SECTOR);
        for (long index = 0; index < sectors; index++) {
            byte[] data = sector(directory.lba() + index);
            int offset = 0;
            while (offset < SECTOR && (data[offset] & 0xFF) > 0) {
                Entry entry = record(data, offset);
                // The first two records are "." and ".." (one-byte names 0 and 1).
                if (!entry.name().equals("\u0000") && !entry.name().equals("\u0001")) {
                    entries.add(entry);
                }
                offset += data[offset] & 0xFF;
            }
        }
        return entries;
    }

    private static Entry record(byte[] data, int offset) {
        long lba = littleEndian(data, offset + 2);
        long size = littleEndian(data, offset + 10);
        boolean directory = (data[offset + 25] & 0x02) != 0;
        int nameLength = data[offset + 32] & 0xFF;
        String name = new String(data, offset + 33, nameLength, StandardCharsets.ISO_8859_1);
        int version = name.indexOf(';');
        return new Entry(
                (version >= 0 ? name.substring(0, version) : name).toUpperCase(Locale.ROOT), lba, size, directory);
    }

    private static long littleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
                | (data[offset + 1] & 0xFFL) << 8
                | (data[offset + 2] & 0xFFL) << 16
                | (data[offset + 3] & 0xFFL) << 24;
    }

    private byte[] sector(long number) throws IOException {
        byte[] data = new byte[SECTOR];
        file.seek(number * SECTOR);
        file.readFully(data);
        return data;
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
