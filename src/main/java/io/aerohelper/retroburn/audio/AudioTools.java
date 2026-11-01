// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.DoubleConsumer;

/**
 * Runs ffprobe and FFmpeg for audio CDs: probing music files and converting them to CD audio. The
 * sources are only read. See {@code docs/design/LLD-audio-cd.md} section 5.
 */
public final class AudioTools {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration CONVERT_IDLE = Duration.ofMinutes(2);

    private final ProcessRunner runner;
    private final ToolLocator locator;

    public AudioTools(ProcessRunner runner, ToolLocator locator) {
        this.runner = runner;
        this.locator = locator;
    }

    /**
     * What a music file holds.
     *
     * @throws UnsupportedInputException if it has no usable sound or is copy-protected
     */
    public ProbedAudio probe(Path source, CancelToken cancel) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessResult result = runner.run(
                ProcessRequest.builder(tool(ExternalTool.FFPROBE), FfmpegAudio.probeArguments(source))
                        .totalTimeout(PROBE_TIMEOUT)
                        .onStdoutLine(lines::add)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new UnsupportedInputException(
                    UserMessage.of(
                            "audio.error.unreadable", source.getFileName().toString()),
                    result.technicalDetail());
        }
        ProbedAudio audio = FfprobeJsonParser.parse(String.join("\n", lines))
                .orElseThrow(() -> new UnsupportedInputException(
                        UserMessage.of(
                                "audio.error.noSound", source.getFileName().toString()),
                        source.toString()));
        if (audio.copyProtected()) {
            throw new UnsupportedInputException(
                    UserMessage.of(
                            "audio.error.copyProtected", source.getFileName().toString()),
                    source.toString());
        }
        return audio;
    }

    /** Decodes {@code source} into raw CD audio PCM at {@code pcm}, reporting the fraction done. */
    public void toCdPcm(
            Path source,
            Path pcm,
            Duration duration,
            boolean normalizeLoudness,
            DoubleConsumer progress,
            CancelToken cancel) {
        ProcessResult result = runner.run(
                ProcessRequest.builder(
                                tool(ExternalTool.FFMPEG), FfmpegAudio.toCdPcmArguments(source, pcm, normalizeLoudness))
                        .idleTimeout(CONVERT_IDLE)
                        .onStdoutLine(
                                line -> FfmpegAudio.progress(line, duration).ifPresent(progress::accept))
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(
                    UserMessage.of(
                            "audio.error.convertFailed", source.getFileName().toString()),
                    result.technicalDetail());
        }
    }

    /** Converts {@code source} to an MP3 file with ID3v2.3 and ID3v1 tags, reporting the fraction done. */
    public void toMp3(
            Path source,
            Path mp3,
            Duration duration,
            Mp3Encoding.Bitrate bitrate,
            DoubleConsumer progress,
            CancelToken cancel) {
        ProcessResult result = runner.run(
                ProcessRequest.builder(tool(ExternalTool.FFMPEG), Mp3Encoding.arguments(source, mp3, bitrate))
                        .idleTimeout(CONVERT_IDLE)
                        .onStdoutLine(
                                line -> FfmpegAudio.progress(line, duration).ifPresent(progress::accept))
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(
                    UserMessage.of(
                            "audio.error.convertFailed", source.getFileName().toString()),
                    result.technicalDetail());
        }
    }

    private Path tool(ExternalTool tool) {
        return locator.locate(tool).orElseThrow(() -> new ToolMissingException(tool.displayName()));
    }
}
