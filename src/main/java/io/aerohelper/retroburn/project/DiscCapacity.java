// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaState;

/** How many bytes a burn can use on the disc in the drive. */
public final class DiscCapacity {

    private DiscCapacity() {}

    /**
     * The free bytes of a blank disc; for a rewritable disc with data, free plus used, because that
     * data will be overwritten or erased. Write-once discs with data cannot be used (no multisession,
     * ADR-0013), so they count as free bytes only.
     */
    public static long usableBytes(MediaInfo media) {
        if (media.state() != MediaState.BLANK && media.kind().isRewritable()) {
            return media.freeBytes() + media.usedBytes();
        }
        return media.freeBytes();
    }
}
