// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.audio.CdAudio;
import io.aerohelper.retroburn.audio.V07tSheetWriter;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.ToolMissingException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Audio CDs on Linux with cdrskin in SAO mode, CD-Text from a v0.7T input sheet. See {@code
 * docs/design/LLD-audio-cd.md} section 6.1.
 */
final class CdrskinAudioEngine implements AudioCdEngine {

    private static final Duration WRITE_IDLE = Duration.ofMinutes(10);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(60);
    private static final double BEFORE_DONE = 0.99;
    private static final String SHEET_NAME = "cdtext.v07t";

    private final ProcessRunner runner;
    private final ToolLocator locator;

    CdrskinAudioEngine(ProcessRunner runner, ToolLocator locator) {
        this.runner = runner;
        this.locator = locator;
    }

    @Override
    public boolean writesCdText() {
        return true;
    }

    @Override
    public void burnAudioCd(DriveId drive, AudioDisc disc, BurnListener listener, CancelToken cancel) {
        List<Path> wavFiles = disc.tracks().stream().map(AudioDisc.Track::wav).toList();
        Optional<Path> sheet = disc.cdText().map(text -> {
            Path path = wavFiles.getFirst().resolveSibling(SHEET_NAME);
            try {
                V07tSheetWriter.write(text, path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return path;
        });
        List<Long> trackBytes = disc.tracks().stream()
                .map(track -> track.sectors() * CdAudio.BYTES_PER_SECTOR)
                .toList();
        long totalBytes = trackBytes.stream().mapToLong(Long::longValue).sum();
        Consumer<String> output = line -> {
            listener.onEvent(new BurnEvent.LogLine(line));
            CdrskinAudio.bytesWritten(line, trackBytes)
                    .ifPresent(bytes -> listener.onEvent(
                            BurnEvent.Progress.of(Math.min((double) bytes / totalBytes, BEFORE_DONE))));
        };
        ProcessResult result = runner.run(
                request(CdrskinAudio.burn(drive, wavFiles, sheet))
                        .idleTimeout(WRITE_IDLE)
                        .onStdoutLine(output)
                        .onStderrLine(output)
                        .build(),
                cancel);
        if (!result.succeeded()) {
            throw new ToolFailedException(
                    UserMessage.of("error.burnFailed", lastLine(result)), result.technicalDetail());
        }
        listener.onEvent(BurnEvent.Progress.of(1.0));
    }

    @Override
    public TrackLayout readTrackLayout(DriveId drive) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessResult result = runner.run(
                request(CdrskinAudio.tableOfContents(drive))
                        .totalTimeout(QUERY_TIMEOUT)
                        .onStdoutLine(lines::add)
                        .build(),
                CancelToken.none());
        if (!result.succeeded()) {
            throw new ToolFailedException(
                    UserMessage.of("error.mediaReadFailed", lastLine(result)), result.technicalDetail());
        }
        return CdrskinAudio.layout(lines)
                .orElseThrow(() -> new RetroBurnException(
                        UserMessage.of("error.mediaReadFailed", "no table of contents"), String.join("\n", lines)));
    }

    private ProcessRequest.Builder request(List<String> arguments) {
        Path cdrskin = locator.locate(ExternalTool.CDRSKIN)
                .orElseThrow(() -> new ToolMissingException(ExternalTool.CDRSKIN.displayName()));
        return ProcessRequest.builder(cdrskin, arguments);
    }

    private static String lastLine(ProcessResult result) {
        List<String> tail = result.outputTail();
        return tail.isEmpty() ? "" : tail.getLast();
    }
}
