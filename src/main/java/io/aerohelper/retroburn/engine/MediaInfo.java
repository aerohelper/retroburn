// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Objects;

/**
 * The medium currently in a drive.
 *
 * @param kind kind of medium
 * @param state blank, appendable, closed, ...
 * @param freeBytes bytes that can still be written, 0 if unknown or none
 * @param usedBytes bytes already readable on the medium, 0 if unknown or none
 */
public record MediaInfo(MediaKind kind, MediaState state, long freeBytes, long usedBytes) {

    public MediaInfo {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(state, "state");
        if (freeBytes < 0 || usedBytes < 0) {
            throw new IllegalArgumentException("Byte counts must not be negative");
        }
    }

    /** The state of an empty drive. */
    public static MediaInfo noMedia() {
        return new MediaInfo(MediaKind.NONE, MediaState.NO_MEDIA, 0, 0);
    }
}
