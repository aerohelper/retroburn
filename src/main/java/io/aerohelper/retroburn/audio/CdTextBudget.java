// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import io.aerohelper.retroburn.engine.CdText;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

/**
 * Whether CD-Text fits in one block: at most 253 packs of 12 payload bytes (libburn {@code
 * doc/cdtext.txt}). Titles and performers each fill their own packs, every text with a 0-terminator,
 * plus three size-info packs. See {@code docs/design/LLD-audio-cd.md} section 4.2.
 */
public final class CdTextBudget {

    public static final int MAX_PACKS = 253;
    static final int PAYLOAD_BYTES = 12;
    static final int SIZE_INFO_PACKS = 3;

    private CdTextBudget() {}

    /** Packs the CD-Text needs; it fits if this is at most {@link #MAX_PACKS}. */
    public static int packs(CdText text) {
        int titles = packsFor(Stream.concat(
                Stream.of(text.albumTitle()), text.tracks().stream().map(CdText.Track::title)));
        int performers = packsFor(Stream.concat(
                Stream.of(text.albumPerformer()), text.tracks().stream().map(CdText.Track::performer)));
        return titles + performers + SIZE_INFO_PACKS;
    }

    public static boolean fits(CdText text) {
        return packs(text) <= MAX_PACKS;
    }

    private static int packsFor(Stream<String> texts) {
        int bytes = texts.mapToInt(t -> t.getBytes(StandardCharsets.ISO_8859_1).length + 1)
                .sum();
        return Math.ceilDiv(bytes, PAYLOAD_BYTES);
    }
}
