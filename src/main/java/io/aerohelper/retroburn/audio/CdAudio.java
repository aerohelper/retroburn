// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.time.Duration;

/**
 * Numbers of CD audio (Red Book) and conversions between samples, sectors and time. See {@code
 * docs/design/LLD-audio-cd.md} section 2.
 */
public final class CdAudio {

    public static final int SAMPLE_RATE = 44_100;
    public static final int CHANNELS = 2;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SECOND = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
    public static final int BYTES_PER_SECTOR = 2352;
    public static final int SAMPLES_PER_SECTOR = 588;
    public static final int SECTORS_PER_SECOND = 75;
    public static final int GAP_SECTORS = 2 * SECTORS_PER_SECOND;
    public static final int MIN_TRACK_SECTORS = 4 * SECTORS_PER_SECOND;
    public static final int MAX_TRACKS = 99;
    public static final long SECTORS_74_MINUTES = 74L * 60 * SECTORS_PER_SECOND;
    public static final long SECTORS_80_MINUTES = 80L * 60 * SECTORS_PER_SECOND;

    private CdAudio() {}

    /** Sectors needed for audio of this length, rounded up to a whole sector. */
    public static long sectorsFor(Duration duration) {
        long samples = Math.ceilDiv(duration.toNanos() * SAMPLE_RATE, 1_000_000_000L);
        return Math.max(1, Math.ceilDiv(samples, SAMPLES_PER_SECTOR));
    }

    /** Sectors needed for this many bytes of CD audio PCM, rounded up to a whole sector. */
    public static long sectorsForBytes(long pcmBytes) {
        return Math.ceilDiv(pcmBytes, BYTES_PER_SECTOR);
    }

    /** Sectors a disc with these tracks uses, counting a 2-second gap before every track. */
    public static long discSectors(long audioSectors, int trackCount) {
        return audioSectors + (long) trackCount * GAP_SECTORS;
    }

    /** "mm:ss", rounded down to whole seconds; minutes may exceed 59. */
    public static String minutesAndSeconds(long sectors) {
        long seconds = sectors / SECTORS_PER_SECOND;
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** "mm:ss:ff" as in cdrdao TOC files, where ff counts sectors (frames) 0–74. */
    public static String minutesSecondsFrames(long sectors) {
        long seconds = sectors / SECTORS_PER_SECOND;
        return "%d:%d:%d".formatted(seconds / 60, seconds % 60, sectors % SECTORS_PER_SECOND);
    }
}
