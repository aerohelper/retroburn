// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What happened during one burn, recorded for the burn reel and saved as JSON. See {@code
 * docs/design/LLD-burn-reel.md} section 2.
 *
 * @param schemaVersion file format version, {@value #SCHEMA_VERSION}
 * @param id a random id, also in the file name
 * @param started when the job started
 * @param ended when it ended
 * @param kind the job kind, such as {@code AUDIO_CD}
 * @param label the disc's name or title
 * @param theme the theme in use, for the reel's look
 * @param items file, track or title names, in disc order
 * @param bytes bytes written
 * @param outcome how it ended, such as {@code SUCCEEDED}
 * @param samples overall progress over time
 * @param markers when each step started
 */
public record BurnSession(
        int schemaVersion,
        String id,
        Instant started,
        Instant ended,
        String kind,
        String label,
        String theme,
        List<String> items,
        long bytes,
        String outcome,
        List<Sample> samples,
        List<Marker> markers) {

    public static final int SCHEMA_VERSION = 1;

    /**
     * Overall progress at a moment.
     *
     * @param atMillis time since the start
     * @param fraction 0 to 1
     * @param speed the write speed as the tool showed it, or empty
     */
    public record Sample(long atMillis, double fraction, String speed) {

        public Sample {
            speed = speed == null ? "" : speed;
        }
    }

    /**
     * A step started.
     *
     * @param atMillis time since the start
     * @param step the step's message key
     */
    public record Marker(long atMillis, String step) {}

    public BurnSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(ended, "ended");
        Objects.requireNonNull(kind, "kind");
        label = label == null ? "" : label;
        theme = theme == null ? "classic" : theme;
        items = items == null ? List.of() : List.copyOf(items);
        Objects.requireNonNull(outcome, "outcome");
        samples = samples == null ? List.of() : List.copyOf(samples);
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public Duration duration() {
        return Duration.between(started, ended);
    }

    public boolean succeeded() {
        return outcome.startsWith("SUCCEEDED");
    }

    /** The last sample at or before {@code atMillis}, or a zero sample before the first. */
    public Sample sampleAt(long atMillis) {
        Sample found = new Sample(0, 0, "");
        for (Sample sample : samples) {
            if (sample.atMillis() > atMillis) {
                break;
            }
            found = sample;
        }
        return found;
    }

    /** The step running at {@code atMillis}, or empty before the first. */
    public String stepAt(long atMillis) {
        String step = "";
        for (Marker marker : markers) {
            if (marker.atMillis() > atMillis) {
                break;
            }
            step = marker.step();
        }
        return step;
    }

    /** The last speed the tool reported, or empty. */
    public String lastSpeed() {
        for (int index = samples.size() - 1; index >= 0; index--) {
            if (!samples.get(index).speed().isEmpty()) {
                return samples.get(index).speed();
            }
        }
        return "";
    }
}
