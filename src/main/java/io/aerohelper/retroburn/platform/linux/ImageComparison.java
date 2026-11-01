// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

/**
 * Compares bytes read back from a disc with the image file as they arrive, so no copy of the disc
 * is written. Bytes after the image's end (the rest of the last block) are ignored.
 *
 * <p>Written to by one thread and queried by another after the writer has finished; the state is
 * guarded by this object's lock.
 */
final class ImageComparison extends OutputStream {

    /** One progress event per 4 MiB compared: a few per second at DVD speeds. */
    private static final long PROGRESS_STEP = 4L * 1024 * 1024;

    private final InputStream image;
    private final long imageSize;
    private final BurnListener listener;
    private final Runnable onMismatch;
    private final byte[] expected = new byte[64 * 1024];
    private long compared;
    private long nextProgress = PROGRESS_STEP;
    private long mismatchAt = -1;

    /**
     * @param onMismatch called once, on the writing thread, at the first difference
     * @throws IOException if the image cannot be opened
     */
    ImageComparison(Path image, BurnListener listener, Runnable onMismatch) throws IOException {
        this.imageSize = Files.size(image);
        this.image = new BufferedInputStream(Files.newInputStream(image));
        this.listener = listener;
        this.onMismatch = onMismatch;
    }

    long imageSize() {
        return imageSize;
    }

    @Override
    public void write(int value) throws IOException {
        write(new byte[] {(byte) value}, 0, 1);
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        boolean mismatched;
        synchronized (this) {
            if (mismatchAt >= 0) {
                return;
            }
            mismatched = compare(buffer, offset, length);
        }
        if (mismatched) {
            onMismatch.run();
        }
    }

    /** Returns true if this chunk holds the first difference. */
    private boolean compare(byte[] buffer, int offset, int length) throws IOException {
        int remaining = (int) Math.min(length, imageSize - compared);
        int position = offset;
        while (remaining > 0) {
            int chunk = image.readNBytes(expected, 0, Math.min(remaining, expected.length));
            if (chunk <= 0) {
                throw new IOException("The image file is shorter than when the check started");
            }
            for (int i = 0; i < chunk; i++) {
                if (buffer[position + i] != expected[i]) {
                    mismatchAt = compared + i;
                    return true;
                }
            }
            compared += chunk;
            position += chunk;
            remaining -= chunk;
        }
        if (compared >= nextProgress || compared == imageSize) {
            nextProgress = compared + PROGRESS_STEP;
            listener.onEvent(BurnEvent.Progress.of(imageSize == 0 ? 1.0 : (double) compared / imageSize));
        }
        return false;
    }

    /** The offset of the first differing byte, if any. */
    synchronized OptionalLong mismatch() {
        return mismatchAt < 0 ? OptionalLong.empty() : OptionalLong.of(mismatchAt);
    }

    /** True when every byte of the image arrived and matched. */
    synchronized boolean complete() {
        return mismatchAt < 0 && compared == imageSize;
    }

    synchronized long compared() {
        return compared;
    }

    @Override
    public void close() throws IOException {
        image.close();
    }
}
