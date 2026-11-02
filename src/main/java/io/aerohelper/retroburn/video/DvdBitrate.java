// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.time.Duration;

/**
 * The video bitrate that fills the disc. See {@code docs/design/LLD-video-dvd.md} section 2.
 *
 * @param videoKbps the bitrate to encode with
 * @param lowQuality below {@link #LOW_QUALITY_KBPS}: the screen warns
 */
public record DvdBitrate(int videoKbps, boolean lowQuality) {

    public static final int AUDIO_KBPS = 192;
    public static final int MAX_VIDEO_KBPS = 8000;
    public static final int MIN_VIDEO_KBPS = 1000;
    public static final int LOW_QUALITY_KBPS = 3500;
    public static final long SINGLE_LAYER_BYTES = 4_700_000_000L;
    public static final long MENU_PAGE_BYTES = 2_000_000L;
    static final double SAFETY = 0.97;
    static final double MUX_OVERHEAD = 0.98;

    /** The bitrate for {@code total} playing time on a disc of {@code capacityBytes} with {@code menuPages}. */
    public static DvdBitrate fit(long capacityBytes, Duration total, int menuPages) {
        double seconds = Math.max(1, total.toMillis() / 1000.0);
        double usable = capacityBytes * SAFETY - menuPages * (double) MENU_PAGE_BYTES;
        double totalKbps = Math.max(0, usable) * 8 / seconds / 1000;
        int video = (int) Math.floor(totalKbps * MUX_OVERHEAD - AUDIO_KBPS);
        int clamped = Math.clamp(video, MIN_VIDEO_KBPS, MAX_VIDEO_KBPS);
        return new DvdBitrate(clamped, video < LOW_QUALITY_KBPS);
    }

    /** Bytes the titles take at this bitrate, video and audio, with the multiplexing overhead. */
    public long titleBytes(Duration total) {
        double seconds = total.toMillis() / 1000.0;
        return (long) Math.ceil((videoKbps + AUDIO_KBPS) * 1000 / 8.0 * seconds / MUX_OVERHEAD);
    }
}
