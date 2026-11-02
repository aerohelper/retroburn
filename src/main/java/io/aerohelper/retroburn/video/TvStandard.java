// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.util.Locale;
import java.util.Set;

/**
 * PAL or NTSC: frame size, frame rate and FFmpeg's DVD target. See {@code
 * docs/design/LLD-video-dvd.md} section 2.
 */
public enum TvStandard {
    PAL(576, "25", 25.0, "pal-dvd", "pal", 768),
    NTSC(480, "30000/1001", 30000.0 / 1001, "ntsc-dvd", "ntsc", 640);

    /** Countries that use NTSC for DVDs (ISO 3166 codes); everywhere else defaults to PAL. */
    private static final Set<String> NTSC_COUNTRIES = Set.of(
            "US", "CA", "MX", "JP", "KR", "TW", "PH", "CU", "DO", "GT", "HN", "NI", "SV", "CR", "PA", "CO", "VE", "EC",
            "PE", "BO", "CL", "BZ", "BS", "JM", "TT", "PR", "GU", "MM", "AG", "BB", "DM", "GD", "KN", "LC", "VC", "MS",
            "AI", "VG", "VI", "AS", "MP", "FM", "MH", "PW", "SR", "HT");

    public static final int WIDTH = 720;

    private final int height;
    private final String frameRate;
    private final double fps;
    private final String ffmpegTarget;
    private final String dvdauthorFormat;
    private final int squareMenuWidth;

    TvStandard(
            int height,
            String frameRate,
            double fps,
            String ffmpegTarget,
            String dvdauthorFormat,
            int squareMenuWidth) {
        this.height = height;
        this.frameRate = frameRate;
        this.fps = fps;
        this.ffmpegTarget = ffmpegTarget;
        this.dvdauthorFormat = dvdauthorFormat;
        this.squareMenuWidth = squareMenuWidth;
    }

    public int height() {
        return height;
    }

    /** As FFmpeg writes a rate: "25" or "30000/1001". */
    public String frameRate() {
        return frameRate;
    }

    public double fps() {
        return fps;
    }

    /** FFmpeg's {@code -target} value. */
    public String ffmpegTarget() {
        return ffmpegTarget;
    }

    /** dvdauthor's {@code <video format>} value. */
    public String dvdauthorFormat() {
        return dvdauthorFormat;
    }

    /** Width of a 4:3 menu drawn with square pixels, before it is scaled to the 720-pixel frame. */
    public int squareMenuWidth() {
        return squareMenuWidth;
    }

    /** The usual standard where {@code locale} is: NTSC in most of the Americas and parts of Asia. */
    public static TvStandard forLocale(Locale locale) {
        return NTSC_COUNTRIES.contains(locale.getCountry().toUpperCase(Locale.ROOT)) ? NTSC : PAL;
    }
}
