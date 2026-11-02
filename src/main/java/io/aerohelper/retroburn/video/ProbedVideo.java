// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import java.time.Duration;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What ffprobe reports about a video file: its length, picture size, display aspect and whether it
 * has sound. Read from {@code ffprobe -v error -print_format json -show_format -show_streams}.
 *
 * @param duration how long it plays
 * @param width picture width in pixels
 * @param height picture height in pixels
 * @param displayAspect width ÷ height as shown, sample aspect ratio included
 * @param hasAudio the file has an audio stream
 */
public record ProbedVideo(Duration duration, int width, int height, double displayAspect, boolean hasAudio) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** "16:9" for pictures at least 1.5 times as wide as high, otherwise "4:3". */
    public String dvdAspect() {
        return displayAspect >= 1.5 ? "16:9" : "4:3";
    }

    /** The video, or empty when there is no video stream or no duration. */
    public static Optional<ProbedVideo> parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException e) {
            return Optional.empty();
        }
        JsonNode video = null;
        boolean audio = false;
        for (JsonNode stream : root.path("streams")) {
            String type = stream.path("codec_type").asString("");
            boolean picture = stream.path("disposition").path("attached_pic").asInt(0) == 1;
            if ("video".equals(type) && !picture && video == null) {
                video = stream;
            }
            audio |= "audio".equals(type);
        }
        if (video == null) {
            return Optional.empty();
        }
        double seconds = number(root.path("format").path("duration"));
        if (seconds <= 0) {
            seconds = number(video.path("duration"));
        }
        int width = video.path("width").asInt(0);
        int height = video.path("height").asInt(0);
        if (seconds <= 0 || width <= 0 || height <= 0) {
            return Optional.empty();
        }
        double sar = ratio(video.path("sample_aspect_ratio").asString("1:1"));
        return Optional.of(new ProbedVideo(
                Duration.ofMillis(Math.round(seconds * 1000)),
                width,
                height,
                width * (sar > 0 ? sar : 1) / height,
                audio));
    }

    private static double number(JsonNode node) {
        try {
            return Double.parseDouble(node.asString(""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** "16:15" as a number; 0 for "0:1" or anything unreadable. */
    private static double ratio(String text) {
        String[] parts = text.split(":");
        try {
            double denominator = Double.parseDouble(parts[1]);
            return parts.length == 2 && denominator != 0 ? Double.parseDouble(parts[0]) / denominator : 0;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return 0;
        }
    }
}
