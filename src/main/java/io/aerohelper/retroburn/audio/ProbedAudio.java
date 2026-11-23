// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * What ffprobe reports about a music file's first audio stream.
 *
 * @param duration how long it plays
 * @param codec FFmpeg's codec name, for example "mp3" or "flac"
 * @param codecTag the container's codec tag, for example "mp4a"; "[0][0][0][0]" when there is none
 * @param sampleRate samples per second
 * @param channels number of channels
 * @param tags title, artist and album as tagged
 */
public record ProbedAudio(
        Duration duration, String codec, String codecTag, int sampleRate, int channels, AudioTags tags) {

    /** MP4 sample entry of FairPlay-protected tracks. */
    static final String PROTECTED_MP4_TAG = "drms";

    public ProbedAudio {
        Objects.requireNonNull(duration, "duration");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(codecTag, "codecTag");
        Objects.requireNonNull(tags, "tags");
    }

    /** True if the stream is known to be copy-protected. */
    public boolean copyProtected() {
        return PROTECTED_MP4_TAG.equals(codecTag);
    }

    /**
     * Tags, each empty when the file has none.
     *
     * @param title the track's title
     * @param artist the track's artist
     * @param album the album's title
     * @param albumArtist the album's artist
     * @param track the track number as tagged, for example "3" or "3/12"
     * @param disc the disc number as tagged, for example "1/2"
     */
    public record AudioTags(
            Optional<String> title,
            Optional<String> artist,
            Optional<String> album,
            Optional<String> albumArtist,
            Optional<String> track,
            Optional<String> disc) {

        public AudioTags(
                Optional<String> title, Optional<String> artist, Optional<String> album, Optional<String> albumArtist) {
            this(title, artist, album, albumArtist, Optional.empty(), Optional.empty());
        }

        public static AudioTags none() {
            return new AudioTags(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        /** The track number's leading whole number ("3/12" gives 3), or 0 when there is none. */
        public int trackNumber() {
            return leadingNumber(track);
        }

        /** The disc number's leading whole number, or 0 when there is none. */
        public int discNumber() {
            return leadingNumber(disc);
        }

        private static int leadingNumber(Optional<String> value) {
            String digits = value.orElse("").strip().replaceFirst("^(\\d{1,4}).*$", "$1");
            return digits.matches("\\d{1,4}") ? Integer.parseInt(digits) : 0;
        }
    }
}
