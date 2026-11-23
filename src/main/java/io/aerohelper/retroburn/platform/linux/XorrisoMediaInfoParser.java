// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.engine.MediaInfo;
import io.aerohelper.retroburn.engine.MediaKind;
import io.aerohelper.retroburn.engine.MediaState;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the medium description printed by {@code xorriso -toc}.
 *
 * <p>Line formats are from {@code Xorriso_toc()} in libisoburn's {@code xorriso/drive_mgt.c};
 * profile names are libburn's table in {@code libburn/mmc.c}. When a line appears more than once
 * (xorriso repeats the drive summary), the last one wins.
 */
final class XorrisoMediaInfoParser {

    static final long BLOCK_SIZE = 2048;

    private static final String MEDIA_CURRENT = "Media current: ";
    private static final String MEDIA_STATUS = "Media status : ";
    private static final Pattern MEDIA_BLOCKS =
            Pattern.compile("^Media blocks :\\s*(\\d+)\\s+\\w+\\s*,\\s*(\\d+)\\s+\\w+");

    private static final Map<String, MediaKind> PROFILES = Map.ofEntries(
            Map.entry("CD-ROM", MediaKind.CD_ROM),
            Map.entry("CD-R", MediaKind.CD_R),
            Map.entry("CD-RW", MediaKind.CD_RW),
            Map.entry("DVD-ROM", MediaKind.DVD_ROM),
            Map.entry("DVD-R sequential recording", MediaKind.DVD_R),
            Map.entry("DVD-RAM", MediaKind.DVD_RAM),
            Map.entry("DVD-RW restricted overwrite", MediaKind.DVD_RW),
            Map.entry("DVD-RW sequential recording", MediaKind.DVD_RW),
            Map.entry("DVD-R/DL sequential recording", MediaKind.DVD_R_DL),
            Map.entry("DVD-R/DL layer jump recording", MediaKind.DVD_R_DL),
            Map.entry("DVD+RW", MediaKind.DVD_PLUS_RW),
            Map.entry("DVD+R", MediaKind.DVD_PLUS_R),
            Map.entry("DVD+R/DL", MediaKind.DVD_PLUS_R_DL),
            Map.entry("BD-ROM", MediaKind.BD_ROM),
            Map.entry("BD-R sequential recording", MediaKind.BD_R),
            Map.entry("BD-R random recording", MediaKind.BD_R),
            Map.entry("BD-RE", MediaKind.BD_RE));

    private XorrisoMediaInfoParser() {}

    static MediaInfo parse(List<String> outputLines) {
        MediaKind kind = MediaKind.UNKNOWN;
        MediaState state = MediaState.UNKNOWN;
        long usedBlocks = 0;
        long freeBlocks = 0;
        for (String line : outputLines) {
            if (line.startsWith(MEDIA_CURRENT)) {
                kind = kindOf(line.substring(MEDIA_CURRENT.length()));
            } else if (line.startsWith(MEDIA_STATUS)) {
                state = stateOf(line.substring(MEDIA_STATUS.length()));
            } else {
                Matcher blocks = MEDIA_BLOCKS.matcher(line);
                if (blocks.find()) {
                    usedBlocks = Long.parseLong(blocks.group(1));
                    freeBlocks = Long.parseLong(blocks.group(2));
                }
            }
        }
        if (state == MediaState.NO_MEDIA) {
            return MediaInfo.noMedia();
        }
        return new MediaInfo(kind, state, freeBlocks * BLOCK_SIZE, usedBlocks * BLOCK_SIZE);
    }

    /** "DVD+RW, sequential" → DVD+RW; the part after the comma is the drive's role. */
    private static MediaKind kindOf(String text) {
        int comma = text.indexOf(", ");
        String profile = (comma >= 0 ? text.substring(0, comma) : text).strip();
        return PROFILES.getOrDefault(profile, MediaKind.UNKNOWN);
    }

    private static MediaState stateOf(String text) {
        if (text.startsWith("is blank")) {
            return MediaState.BLANK;
        }
        if (text.startsWith("is written , is appendable")) {
            return MediaState.APPENDABLE;
        }
        if (text.startsWith("is written , is closed")) {
            return MediaState.CLOSED;
        }
        if (text.startsWith("is not present")) {
            return MediaState.NO_MEDIA;
        }
        return MediaState.UNKNOWN;
    }
}
