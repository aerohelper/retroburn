// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.List;
import java.util.Objects;

/**
 * The CD-Text of an audio CD: album title and performer, then one title and performer per track,
 * in track order. Texts are already cleaned to ISO-8859-1 (see {@code
 * docs/design/LLD-audio-cd.md} section 4); empty texts are allowed.
 *
 * @param albumTitle the disc's title
 * @param albumPerformer the disc's performer
 * @param tracks one entry per track, in order
 */
public record CdText(String albumTitle, String albumPerformer, List<Track> tracks) {

    public CdText {
        Objects.requireNonNull(albumTitle, "albumTitle");
        Objects.requireNonNull(albumPerformer, "albumPerformer");
        tracks = List.copyOf(tracks);
    }

    /**
     * Title and performer of one track.
     *
     * @param title the track's title
     * @param performer the track's performer
     */
    public record Track(String title, String performer) {

        public Track {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(performer, "performer");
        }
    }
}
