// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the output of {@code ffprobe -v error -print_format json -show_format -show_streams <file>}
 * (see https://ffmpeg.org/ffprobe.html, "json" writer). Uses the first stream whose {@code
 * codec_type} is {@code audio}; tags come from the format, then from that stream, matched without
 * regard to case. See {@code docs/design/LLD-audio-cd.md} section 5.1.
 */
public final class FfprobeJsonParser {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private FfprobeJsonParser() {}

    /** The file's audio, or empty if it has no audio stream or no known duration. */
    public static Optional<ProbedAudio> parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        Optional<JsonNode> audio = firstAudioStream(root);
        if (audio.isEmpty()) {
            return Optional.empty();
        }
        JsonNode stream = audio.get();
        JsonNode format = root.path("format");
        Optional<Duration> duration = seconds(format.path("duration")).or(() -> seconds(stream.path("duration")));
        if (duration.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> tags = new TreeMap<>();
        addTags(tags, stream.path("tags"));
        addTags(tags, format.path("tags"));
        return Optional.of(new ProbedAudio(
                duration.get(),
                stream.path("codec_name").asString(""),
                stream.path("codec_tag_string").asString(""),
                integer(stream.path("sample_rate")),
                stream.path("channels").asInt(0),
                new ProbedAudio.AudioTags(
                        tag(tags, "title"),
                        tag(tags, "artist"),
                        tag(tags, "album"),
                        tag(tags, "album_artist"),
                        tag(tags, "track"),
                        tag(tags, "disc"))));
    }

    private static Optional<JsonNode> firstAudioStream(JsonNode root) {
        for (JsonNode candidate : root.path("streams")) {
            if ("audio".equals(candidate.path("codec_type").asString(""))) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static void addTags(Map<String, String> tags, JsonNode node) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String value = entry.getValue().asString("").strip();
            if (!value.isEmpty()) {
                tags.put(entry.getKey().toLowerCase(Locale.ROOT), value);
            }
        }
    }

    private static Optional<String> tag(Map<String, String> tags, String name) {
        return Optional.ofNullable(tags.get(name));
    }

    /** ffprobe prints numbers such as durations and sample rates as JSON strings. */
    private static Optional<Duration> seconds(JsonNode node) {
        try {
            double seconds = Double.parseDouble(node.asString(""));
            return seconds > 0 ? Optional.of(Duration.ofNanos(Math.round(seconds * 1e9))) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static int integer(JsonNode node) {
        try {
            return Integer.parseInt(node.asString(""));
        } catch (NumberFormatException e) {
            return node.asInt(0);
        }
    }
}
