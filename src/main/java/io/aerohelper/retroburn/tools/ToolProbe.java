// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.RetroBurnException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Checks which external tools are installed and working, and which FFmpeg encoders exist.
 *
 * <p>Runs each tool once with its version command, so it takes up to a few seconds: call it in the
 * background, never on the JavaFX Application Thread.
 */
public final class ToolProbe {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> LIST_ENCODERS = List.of("-hide_banner", "-encoders");

    private final ToolLocator locator;
    private final ProcessRunner runner;

    public ToolProbe(ToolLocator locator, ProcessRunner runner) {
        this.locator = Objects.requireNonNull(locator, "locator");
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    /** Checks every tool in {@code tools}, plus FFmpeg's encoders when FFmpeg is among them. */
    public ToolReport probeAll(Collection<ExternalTool> tools) {
        Map<ExternalTool, ToolStatus> statuses = new EnumMap<>(ExternalTool.class);
        for (ExternalTool tool : tools) {
            statuses.put(tool, probe(tool));
        }
        Set<String> encoders = Optional.ofNullable(statuses.get(ExternalTool.FFMPEG))
                .filter(ToolStatus::isUsable)
                .flatMap(ToolStatus::path)
                .map(this::listFfmpegEncoders)
                .orElse(Set.of());
        return new ToolReport(statuses, encoders);
    }

    /** Locates one tool and checks that it runs and reports a version. */
    public ToolStatus probe(ExternalTool tool) {
        Optional<Path> path = locator.locate(tool);
        if (path.isEmpty()) {
            return ToolStatus.missing(tool);
        }
        try {
            ProcessResult result = runner.run(request(path.get(), tool.versionArguments()), CancelToken.none());
            // The exit code is ignored on purpose: dvdauthor's --help, for example, exits non-zero.
            return tool.parseVersion(result.outputTail())
                    .map(version -> ToolStatus.found(tool, path.get(), version))
                    .orElseGet(() -> ToolStatus.notWorking(tool, path.get(), result.technicalDetail()));
        } catch (RetroBurnException e) {
            return ToolStatus.notWorking(tool, path.get(), e.technicalDetail());
        }
    }

    private Set<String> listFfmpegEncoders(Path ffmpeg) {
        // The list is longer than the output tail a ProcessResult keeps, so collect every line.
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessRequest request = ProcessRequest.builder(ffmpeg, LIST_ENCODERS)
                .totalTimeout(PROBE_TIMEOUT)
                .onStdoutLine(lines::add)
                .build();
        try {
            ProcessResult result = runner.run(request, CancelToken.none());
            return result.succeeded() ? FfmpegEncoderListParser.parse(List.copyOf(lines)) : Set.of();
        } catch (RetroBurnException e) {
            return Set.of();
        }
    }

    private static ProcessRequest request(Path executable, List<String> arguments) {
        return ProcessRequest.builder(executable, arguments)
                .totalTimeout(PROBE_TIMEOUT)
                .build();
    }
}
