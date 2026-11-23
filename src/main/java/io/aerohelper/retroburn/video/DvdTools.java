// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.video;

import io.aerohelper.retroburn.audio.FfmpegAudio;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.ToolMissingException;
import io.aerohelper.retroburn.common.UnsupportedInputException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Runs ffprobe, FFmpeg, spumux and dvdauthor for video DVDs. Sources are only read. See {@code
 * docs/design/LLD-video-dvd.md} section 5.
 */
public final class DvdTools {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration WORK_IDLE = Duration.ofMinutes(5);

    private final ProcessRunner runner;
    private final ToolLocator locator;

    public DvdTools(ProcessRunner runner, ToolLocator locator) {
        this.runner = runner;
        this.locator = locator;
    }

    /** What a video file holds. @throws UnsupportedInputException if it has no usable picture */
    public ProbedVideo probe(Path source, CancelToken cancel) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessResult result = runner.run(
                ProcessRequest.builder(tool(ExternalTool.FFPROBE), FfmpegAudio.probeArguments(source))
                        .totalTimeout(PROBE_TIMEOUT)
                        .onStdoutLine(lines::add)
                        .build(),
                cancel);
        String name = source.getFileName().toString();
        if (!result.succeeded()) {
            throw new UnsupportedInputException(
                    UserMessage.of("video.error.unreadable", name), result.technicalDetail());
        }
        return ProbedVideo.parse(String.join("\n", lines))
                .orElseThrow(() -> new UnsupportedInputException(
                        UserMessage.of("video.error.noPicture", name), source.toString()));
    }

    /** Converts one title, reporting the fraction done. */
    public void title(
            Path source,
            Path mpeg,
            TvStandard standard,
            String aspect,
            int videoKbps,
            Duration length,
            DoubleConsumer progress,
            CancelToken cancel) {
        run(
                ExternalTool.FFMPEG,
                FfmpegVideo.title(source, mpeg, standard, aspect, videoKbps),
                line -> FfmpegAudio.progress(line, length).ifPresent(progress::accept),
                cancel,
                "video.error.convertFailed",
                source);
    }

    public void thumbnail(Path source, Duration length, Path png, CancelToken cancel) {
        run(
                ExternalTool.FFMPEG,
                FfmpegVideo.thumbnail(source, length, png),
                line -> {},
                cancel,
                "video.error.convertFailed",
                source);
    }

    public void menuVideo(Path background, Path mpeg, TvStandard standard, CancelToken cancel) {
        run(
                ExternalTool.FFMPEG,
                FfmpegVideo.menu(background, mpeg, standard),
                line -> {},
                cancel,
                "video.error.menuFailed",
                background);
    }

    /** {@code spumux -m dvd <xml> < input > output} (spumux manual, SYNOPSIS). */
    public void spumux(Path xml, Path input, Path output, Consumer<String> log, CancelToken cancel) {
        try (OutputStream out = Files.newOutputStream(output)) {
            ProcessResult result = runner.run(
                    ProcessRequest.builder(tool(ExternalTool.SPUMUX), List.of("-m", "dvd", xml.toString()))
                            .stdinFrom(input)
                            .stdoutTo(out)
                            .idleTimeout(WORK_IDLE)
                            .onStderrLine(log)
                            .build(),
                    cancel);
            if (!result.succeeded()) {
                throw new ToolFailedException(
                        UserMessage.of(
                                "video.error.menuFailed", xml.getFileName().toString()),
                        result.technicalDetail());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code dvdauthor -x <xml>} (dvdauthor manual, SYNOPSIS). */
    public void dvdauthor(Path xml, Consumer<String> log, CancelToken cancel) {
        ProcessResult result = runner.run(
                ProcessRequest.builder(tool(ExternalTool.DVDAUTHOR), List.of("-x", xml.toString()))
                        .idleTimeout(WORK_IDLE)
                        .onStdoutLine(log)
                        .onStderrLine(log)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(UserMessage.of("video.error.authorFailed"), result.technicalDetail());
        }
    }

    private void run(
            ExternalTool tool,
            List<String> arguments,
            Consumer<String> stdout,
            CancelToken cancel,
            String failureKey,
            Path subject) {
        ProcessResult result = runner.run(
                ProcessRequest.builder(tool(tool), arguments)
                        .idleTimeout(WORK_IDLE)
                        .onStdoutLine(stdout)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(
                    UserMessage.of(failureKey, subject.getFileName().toString()), result.technicalDetail());
        }
    }

    private Path tool(ExternalTool tool) {
        return locator.locate(tool).orElseThrow(() -> new ToolMissingException(tool.displayName()));
    }
}
