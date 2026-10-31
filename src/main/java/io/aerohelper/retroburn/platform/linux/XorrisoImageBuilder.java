// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.ToolFailedException;
import io.aerohelper.retroburn.common.ToolMissingException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolLocator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes ISO 9660 images with Rock Ridge and Joliet using GNU xorriso, so the result reads well on
 * Linux and Windows. See {@code docs/design/LLD-linux-platform.md} section 2.
 */
final class XorrisoImageBuilder implements DiscImageBuilder {

    private static final Logger LOG = Logger.getLogger(XorrisoImageBuilder.class.getName());

    /**
     * xorriso reports progress about once a second while writing and while adding files; two minutes
     * of silence means it is stuck.
     */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(2);

    private final ProcessRunner runner;
    private final ToolLocator locator;
    private final Path scratchFolder;

    /** @param scratchFolder where the temporary path list is written */
    XorrisoImageBuilder(ProcessRunner runner, ToolLocator locator, Path scratchFolder) {
        this.runner = runner;
        this.locator = locator;
        this.scratchFolder = scratchFolder;
    }

    @Override
    public void buildDataImage(
            DiscTree tree, ImageOptions options, Path target, BurnListener listener, CancelToken cancel) {
        Path xorriso = locator.locate(ExternalTool.XORRISO)
                .orElseThrow(() -> new ToolMissingException(ExternalTool.XORRISO.displayName()));
        Path pathList = createPathList(tree);
        boolean succeeded = false;
        try {
            // An existing file would be treated as an earlier session and appended to.
            Files.deleteIfExists(target);
            Consumer<String> output = line -> {
                listener.onEvent(new BurnEvent.LogLine(line));
                XorrisoProgressParser.parse(line).ifPresent(listener::onEvent);
            };
            ProcessRequest request = ProcessRequest.builder(
                            xorriso, XorrisoCommandBuilder.createImage(target, options.volumeLabel(), pathList))
                    .idleTimeout(IDLE_TIMEOUT)
                    .onStdoutLine(output)
                    .onStderrLine(output)
                    .build();
            ProcessResult result = runner.run(request, cancel);
            if (!result.succeeded()) {
                String reason =
                        XorrisoErrorParser.mainProblem(result.outputTail()).orElse("");
                throw new ToolFailedException(
                        UserMessage.of("error.isoCreationFailed", reason), result.technicalDetail());
            }
            listener.onEvent(BurnEvent.Progress.of(1.0));
            succeeded = true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not replace " + target, e);
        } finally {
            deleteQuietly(pathList);
            if (!succeeded) {
                deleteQuietly(target);
            }
        }
    }

    private Path createPathList(DiscTree tree) {
        try {
            Files.createDirectories(scratchFolder);
            Path pathList = Files.createTempFile(scratchFolder, "xorriso-pathspecs-", ".txt");
            try {
                PathspecListWriter.write(tree, pathList);
            } catch (RuntimeException e) {
                deleteQuietly(pathList);
                throw e;
            }
            return pathList;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create a path list in " + scratchFolder, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not delete " + file, e);
        }
    }
}
