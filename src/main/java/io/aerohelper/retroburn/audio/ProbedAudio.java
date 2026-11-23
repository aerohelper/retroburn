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
     * Title, artist, album and album artist, each empty when the file has no such tag.
     *
     * @param title the track's title
     * @param artist the track's artist
     * @param album the album's title
     * @param albumArtist the album's artist
     */
    public record AudioTags(
            Optional<String> title, Optional<String> artist, Optional<String> album, Optional<String> albumArtist) {

        public static AudioTags none() {
            return new AudioTags(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
    }
}
