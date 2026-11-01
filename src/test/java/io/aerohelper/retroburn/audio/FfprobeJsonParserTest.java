// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** Parses captured ffprobe and ffmpeg output. */
class FfprobeJsonParserTest {

    @Test
    void readsATaggedFlac() {
        ProbedAudio audio =
                FfprobeJsonParser.parse(json("ffmpeg/ffprobe-flac-tagged.txt")).orElseThrow();

        assertEquals(Duration.ofMillis(5500), audio.duration());
        assertEquals("flac", audio.codec());
        assertEquals("[0][0][0][0]", audio.codecTag());
        assertEquals(48_000, audio.sampleRate());
        assertEquals(1, audio.channels());
        // The capture has "ARTIST" in capitals and the other tags in lower case.
        assertEquals(
                new ProbedAudio.AudioTags(
                        Optional.of("Song of Joy"),
                        Optional.of("Felix"),
                        Optional.of("Joyful Nights"),
                        Optional.of("United Cat Orchestra")),
                audio.tags());
        assertFalse(audio.copyProtected());
    }

    @Test
    void streamTagsAreUsedWhenTheFormatHasNone() {
        String json = """
                {"streams": [{"codec_type": "video"},
                             {"codec_type": "audio", "codec_name": "aac", "codec_tag_string": "drms",
                              "sample_rate": "44100", "channels": 2, "duration": "3.0",
                              "tags": {"TITLE": "  Inside  "}}],
                 "format": {}}""";
        ProbedAudio audio = FfprobeJsonParser.parse(json).orElseThrow();
        assertEquals(Duration.ofSeconds(3), audio.duration());
        assertEquals(Optional.of("Inside"), audio.tags().title());
        assertEquals(Optional.empty(), audio.tags().artist());
        assertTrue(audio.copyProtected());
    }

    @Test
    void noAudioStreamOrNoDurationGivesNothing() {
        assertTrue(FfprobeJsonParser.parse("{\"streams\": [{\"codec_type\": \"video\"}], \"format\": {}}")
                .isEmpty());
        assertTrue(FfprobeJsonParser.parse("{\"streams\": [{\"codec_type\": \"audio\"}], \"format\": {}}")
                .isEmpty());
        assertTrue(FfprobeJsonParser.parse("not json").isEmpty());
    }

    @Test
    void capturedProgressRunsToTheEnd() {
        List<String> lines = ToolOutput.lines("ffmpeg/convert-to-cd-pcm-progress.txt");
        List<Double> fractions = lines.stream()
                .map(line -> FfmpegAudio.progress(line, Duration.ofMillis(5500)))
                .filter(OptionalDouble::isPresent)
                .map(OptionalDouble::getAsDouble)
                .toList();
        assertEquals(0.0, fractions.getFirst());
        assertEquals(5499637 / 5500000.0, fractions.get(1), 1e-9);
        assertEquals(1.0, fractions.getLast());
    }

    /** The capture without its command line and exit code. */
    private static String json(String fixture) {
        List<String> lines = ToolOutput.lines(fixture);
        return String.join("\n", lines.subList(1, lines.size() - 1));
    }
}
