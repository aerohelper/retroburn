// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.ImageOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Writes ISO 9660 + Joliet + UDF images with IMAPI2FS, which is part of Windows, so the result reads
 * well on Windows and Linux. No drive is needed. See {@code docs/design/LLD-windows-platform.md}
 * section 5.3.
 */
final class Imapi2ImageBuilder implements DiscImageBuilder {

    private static final Logger LOG = Logger.getLogger(Imapi2ImageBuilder.class.getName());

    /**
     * Adding a very large folder is one IMAPI2FS call with no progress until it returns, so silence is
     * tolerated for a long time.
     */
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(10);

    private final HelperClient helper;

    Imapi2ImageBuilder(HelperClient helper) {
        this.helper = helper;
    }

    @Override
    public void buildDataImage(
            DiscTree tree, ImageOptions options, Path target, BurnListener listener, CancelToken cancel) {
        boolean succeeded = false;
        try {
            helper.run(
                    HelperRequest.buildImage(tree, options.volumeLabel(), target),
                    event -> {
                        switch (event) {
                            case HelperEvent.Progress progress ->
                                listener.onEvent(BurnEvent.Progress.of(Imapi2Mapping.fraction(progress)));
                            case HelperEvent.Log log -> listener.onEvent(new BurnEvent.LogLine(log.text()));
                            default -> {}
                        }
                    },
                    cancel,
                    HelperClient.Limits.silentFor(IDLE_TIMEOUT));
            listener.onEvent(BurnEvent.Progress.of(1.0));
            succeeded = true;
        } finally {
            // The helper deletes a partial image itself, unless it had to be stopped forcibly.
            if (!succeeded) {
                deleteQuietly(target);
            }
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
