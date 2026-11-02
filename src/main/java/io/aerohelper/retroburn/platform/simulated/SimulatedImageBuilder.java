// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.simulated;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.ImageOptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pretends to build an image: reports progress in ten steps and writes a small text file listing
 * the entries. The result is not a real ISO image.
 */
final class SimulatedImageBuilder implements DiscImageBuilder {

    private static final int STEPS = 10;

    @Override
    public void buildDataImage(
            DiscTree tree, ImageOptions options, Path target, BurnListener listener, CancelToken cancel) {
        for (int step = 1; step <= STEPS; step++) {
            cancel.throwIfCancelled();
            listener.onEvent(BurnEvent.Progress.of(step / (double) STEPS));
        }
        String listing;
        try (Stream<DiscEntry> entries = tree.entries()) {
            listing = entries.map(entry -> entry.discPath() + " <- " + entry.source())
                    .collect(Collectors.joining("\n"));
        }
        try {
            Files.writeString(
                    target,
                    "Simulated image '" + options.volumeLabel() + "'\n" + listing + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + target, e);
        }
    }

    @Override
    public void buildDvdVideoImage(
            Path dvdFolder, String volumeLabel, Path target, BurnListener listener, CancelToken cancel) {
        buildDataImage(
                DiscTree.of(List.of(new DiscEntry("/VIDEO_TS", dvdFolder.resolve("VIDEO_TS")))),
                new ImageOptions(volumeLabel),
                target,
                listener,
                cancel);
    }
}
