// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.common.WorkFolderFullException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.ToLongFunction;

/** Checks before a job starts that a folder's drive has enough free space for its output. */
public final class DiskSpaceCheck {

    private final ToLongFunction<Path> usableSpace;

    /** Uses the real free space of the folder's file system. */
    public DiskSpaceCheck() {
        this(DiskSpaceCheck::usableSpaceOf);
    }

    /** @param usableSpace returns the bytes available to RetroBurn in a folder */
    DiskSpaceCheck(ToLongFunction<Path> usableSpace) {
        this.usableSpace = Objects.requireNonNull(usableSpace, "usableSpace");
    }

    /**
     * @param folder the folder that will receive the output; must exist
     * @param requiredBytes the bytes the job will write there
     * @throws WorkFolderFullException if there is not enough space
     */
    public void requireFreeSpace(Path folder, long requiredBytes) {
        long available = usableSpace.applyAsLong(folder);
        if (available < requiredBytes) {
            throw new WorkFolderFullException(
                    UserMessage.of(
                            "error.notEnoughSpace", folder.toString(), megabytes(requiredBytes), megabytes(available)),
                    "Folder " + folder + ": need " + requiredBytes + " bytes, " + available + " available");
        }
    }

    private static long megabytes(long bytes) {
        return Math.ceilDiv(bytes, 1024L * 1024L);
    }

    private static long usableSpaceOf(Path folder) {
        try {
            return Files.getFileStore(folder).getUsableSpace();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read free space of " + folder, e);
        }
    }
}
