// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import io.aerohelper.retroburn.common.CancelToken;
import java.nio.file.Path;

/**
 * Writes disc images (ISO files) without needing a drive. One implementation per operating system.
 *
 * <p>Calls block until the image is complete; never call them on the JavaFX Application Thread.
 */
public interface DiscImageBuilder {

    /**
     * Writes a data disc image readable on Windows and Linux.
     *
     * @param tree what goes on the disc
     * @param options volume label and other settings
     * @param target the ISO file to create; replaced if it exists
     * @param listener receives progress and log lines
     * @param cancel stops the build; a partial target file is deleted
     * @throws io.aerohelper.retroburn.common.RetroBurnException on failure
     */
    void buildDataImage(DiscTree tree, ImageOptions options, Path target, BurnListener listener, CancelToken cancel);

    /**
     * Writes a DVD-Video image: ISO 9660 and UDF 1.02 with the VIDEO_TS files in the order players
     * expect. See {@code docs/design/LLD-video-dvd.md} section 6.
     *
     * @param dvdFolder the folder holding {@code VIDEO_TS} (and {@code AUDIO_TS})
     * @param volumeLabel the disc's name
     * @param target the ISO file to create; replaced if it exists
     */
    void buildDvdVideoImage(Path dvdFolder, String volumeLabel, Path target, BurnListener listener, CancelToken cancel);
}
