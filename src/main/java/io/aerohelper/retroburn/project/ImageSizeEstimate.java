// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

/**
 * How many bytes the image of a compilation will take, erring on the large side. The exact size is
 * checked again once the image exists. See {@code docs/design/LLD-data-burning.md} section 2.
 */
public final class ImageSizeEstimate {

    /**
     * Per file or folder: one 2048-byte UDF file entry plus room for its ISO 9660 and Joliet
     * directory records.
     */
    static final long BYTES_PER_ENTRY = 2048 + 512;

    /**
     * Volume descriptors, path tables, UDF structures and padding. Measured overhead: 1.1 MiB for an
     * IMAPI2FS image with one file, 0.4 MiB for an xorriso one, padding included.
     */
    static final long FIXED_BYTES = 2L * 1024 * 1024;

    private ImageSizeEstimate() {}

    public static long bytes(TreeSize size) {
        return size.blocks() * TreeSizeScanner.BLOCK_SIZE + size.entries() * BYTES_PER_ENTRY + FIXED_BYTES;
    }
}
