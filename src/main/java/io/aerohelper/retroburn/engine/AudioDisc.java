// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An audio CD ready to burn: CD audio WAV files (44.1 kHz, 16-bit, stereo, canonical 44-byte header,
 * data padded to whole 2352-byte sectors) and, optionally, CD-Text. Every track gets a 2-second gap.
 *
 * @param tracks the tracks, in order
 * @param cdText the CD-Text to write, or empty for none
 */
public record AudioDisc(List<Track> tracks, Optional<CdText> cdText) {

    /** Bytes of the WAV header in front of the samples. */
    public static final int WAV_HEADER_BYTES = 44;

    public AudioDisc {
        tracks = List.copyOf(tracks);
        Objects.requireNonNull(cdText, "cdText");
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("An audio disc needs at least one track");
        }
        int textTracks = cdText.map(text -> text.tracks().size()).orElse(tracks.size());
        if (textTracks != tracks.size()) {
            throw new IllegalArgumentException("CD-Text has " + textTracks + " tracks, the disc " + tracks.size());
        }
    }

    /** Sectors of audio on the disc, without the gaps. */
    public long audioSectors() {
        return tracks.stream().mapToLong(Track::sectors).sum();
    }

    /**
     * One track.
     *
     * @param wav the WAV file in the job's work folder
     * @param sectors 2352-byte sectors of audio in the file
     */
    public record Track(Path wav, long sectors) {

        public Track {
            Objects.requireNonNull(wav, "wav");
            if (sectors <= 0) {
                throw new IllegalArgumentException("A track needs at least one sector");
            }
        }
    }
}
