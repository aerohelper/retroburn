// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.FfmpegEncoderListParser;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Makes a burn reel: picks the encoder, mixes the soundtrack, draws every frame straight into
 * FFmpeg's input and writes {@code RetroBurn-<time>.mp4}. Blocks; run it off the UI thread.
 */
public final class ReelMaker {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ProcessRunner processes;
    private final ToolLocator tools;
    private final Messages messages;

    /** The reel and how it was made. */
    public record Result(Path file, ReelEncoder.Codec codec) {}

    public ReelMaker(ProcessRunner processes, ToolLocator tools, Messages messages) {
        this.processes = Objects.requireNonNull(processes, "processes");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * @param seconds reel length, 12 to 18
     * @param workFolder where the soundtrack is written (and removed afterwards)
     * @param outputFolder where the reel goes, created if needed
     * @param frames receives the number of each frame as it is sent to FFmpeg
     * @throws ToolFailedException if FFmpeg is missing, has no usable encoder or fails
     * @throws OperationCancelledException if cancelled; no partial file is left
     */
    public Result make(
            BurnSession session,
            int seconds,
            boolean hideNames,
            Path workFolder,
            Path outputFolder,
            IntConsumer frames,
            CancelToken cancel)
            throws IOException {
        Path ffmpeg = tools.locate(ExternalTool.FFMPEG)
                .orElseThrow(
                        () -> new ToolFailedException(UserMessage.of("reel.error.noFfmpeg"), "FFmpeg was not found"));
        ReelEncoder.Codec codec = ReelEncoder.choose(encoders(ffmpeg, cancel))
                .orElseThrow(() -> new ToolFailedException(
                        UserMessage.of("reel.error.noEncoder"), "No libx264, libopenh264 or mpeg4 encoder"));

        ReelTimeline timeline = ReelTimeline.of(seconds);
        Files.createDirectories(workFolder);
        Files.createDirectories(outputFolder);
        Path soundtrack = workFolder.resolve("reel-soundtrack.wav");
        ReelSoundtrack.write(ReelSoundtrack.mix(timeline, session.theme()), soundtrack);
        Path output = outputFolder.resolve(fileName(session));
        ReelRenderer renderer = new ReelRenderer(timeline, session, ReelStyle.of(session, messages, hideNames));
        List<String> errors = new ArrayList<>();
        try {
            ProcessResult result = processes.run(
                    ProcessRequest.builder(ffmpeg, ReelEncoder.arguments(codec, soundtrack, output))
                            .totalTimeout(Duration.ofMinutes(15))
                            .onStderrLine(errors::add)
                            .stdinWriter(input -> {
                                for (int index = 0; index < timeline.frameCount(); index++) {
                                    if (cancel.isCancelled()) {
                                        return;
                                    }
                                    input.write(renderer.rgb(index));
                                    frames.accept(index + 1);
                                }
                            })
                            .build(),
                    cancel);
            if (!result.succeeded() || !Files.isRegularFile(output)) {
                Files.deleteIfExists(output);
                throw new ToolFailedException(UserMessage.of("reel.error.encodeFailed"), result.technicalDetail());
            }
            return new Result(output, codec);
        } catch (OperationCancelledException | ToolFailedException e) {
            Files.deleteIfExists(output);
            throw e;
        } finally {
            Files.deleteIfExists(soundtrack);
        }
    }

    /** {@code RetroBurn-<yyyyMMdd-HHmmss>.mp4} from the session's start, in local time. */
    static String fileName(BurnSession session) {
        return "RetroBurn-" + FILE_TIME.format(session.started().atZone(ZoneId.systemDefault())) + ".mp4";
    }

    private java.util.Set<String> encoders(Path ffmpeg, CancelToken cancel) {
        List<String> lines = new ArrayList<>();
        ProcessResult result = processes.run(
                ProcessRequest.builder(ffmpeg, List.of("-hide_banner", "-encoders"))
                        .totalTimeout(Duration.ofSeconds(30))
                        .onStdoutLine(lines::add)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(UserMessage.of("reel.error.noEncoder"), result.technicalDetail());
        }
        return FfmpegEncoderListParser.parse(lines);
    }
}
