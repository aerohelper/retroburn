// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The burn reel's acceptance check with the real FFmpeg: a demo reel is 1080 × 1920, about 15 s,
 * H.264 (or the documented fallback) in yuv420p, with an AAC audio stream. Runs wherever FFmpeg and
 * ffprobe are installed (both CI systems install them).
 */
class ReelAcceptanceTest {

    @TempDir
    Path folder;

    @Test
    void demoReelIsAFifteenSecondVerticalH264VideoWithSound() throws IOException {
        PathSearchToolLocator locator = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator),
                List.of("", ".exe"));
        assumeTrue(locator.locate(ExternalTool.FFMPEG).isPresent(), "FFmpeg is not installed");
        assumeTrue(locator.locate(ExternalTool.FFPROBE).isPresent(), "ffprobe is not installed");
        ProcessRunner processes = new ProcessRunner();

        AtomicInteger frames = new AtomicInteger();
        ReelMaker.Result result = new ReelMaker(processes, locator, Messages.load(Locale.ENGLISH))
                .make(
                        DemoSession.create("y2k"),
                        15,
                        true,
                        folder.resolve("work"),
                        folder.resolve("reels"),
                        frames::set,
                        new CancelToken());
        System.out.println("[reel] " + result.file() + " encoder "
                + result.codec().name() + ", " + Files.size(result.file()) + " bytes");
        assertEquals(450, frames.get());
        assertTrue(Files.notExists(folder.resolve("work/reel-soundtrack.wav")), "soundtrack removed");

        List<String> json = new ArrayList<>();
        ProcessResult probe = processes.run(
                ProcessRequest.builder(
                                locator.locate(ExternalTool.FFPROBE).orElseThrow(),
                                List.of(
                                        "-v",
                                        "error",
                                        "-show_streams",
                                        "-show_format",
                                        "-of",
                                        "json",
                                        result.file().toString()))
                        .totalTimeout(Duration.ofMinutes(1))
                        .onStdoutLine(json::add)
                        .build(),
                new CancelToken());
        assertTrue(probe.succeeded(), probe.technicalDetail());
        JsonNode root = JsonMapper.builder().build().readTree(String.join("\n", json));
        JsonNode video = null;
        JsonNode audio = null;
        for (JsonNode stream : root.get("streams")) {
            String type = stream.get("codec_type").asString();
            if (type.equals("video")) {
                video = stream;
            } else if (type.equals("audio")) {
                audio = stream;
            }
        }
        System.out.println(
                "[reel] video " + video + "\n[reel] audio " + audio + "\n[reel] format " + root.get("format"));
        assertTrue(video != null && audio != null, "video and audio streams");
        assertEquals(1080, video.get("width").asInt());
        assertEquals(1920, video.get("height").asInt());
        assertEquals("yuv420p", video.get("pix_fmt").asString());
        String codec = video.get("codec_name").asString();
        assertEquals(result.codec().h264() ? "h264" : "mpeg4", codec);
        assertEquals("aac", audio.get("codec_name").asString());
        double seconds = Double.parseDouble(root.get("format").get("duration").asString());
        assertTrue(seconds >= 14.5 && seconds <= 15.5, "duration " + seconds);
    }
}
