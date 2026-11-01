// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.BurnOptions;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.pipeline.StepContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * The steps of the data-burning jobs. Each one only calls the engine and reports; the decisions are
 * made by {@link BurnPreCheck}. See {@code docs/design/LLD-data-burning.md} section 4.
 */
final class BurnSteps {

    private BurnSteps() {}

    /** Where a step finds the image: in the job's work folder, or a file the user chose. */
    @FunctionalInterface
    interface ImageFile extends Function<StepContext, Path> {}

    record BuildImage(DiscImageBuilder builder, DiscTree tree, VolumeLabel label, ImageFile image, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.buildImage");
        }

        @Override
        public void run(StepContext context) {
            builder.buildDataImage(
                    tree,
                    new ImageOptions(label.text()),
                    image.apply(context),
                    context.burnListener(),
                    context.cancelToken());
        }
    }

    record Blank(BurnEngine engine, DriveId drive, BlankMode mode, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.blank");
        }

        @Override
        public boolean needsSystemAwake() {
            return true;
        }

        @Override
        public void run(StepContext context) {
            engine.blank(drive, mode, context.burnListener(), context.cancelToken());
        }
    }

    record BurnImage(BurnEngine engine, DriveId drive, ImageFile image, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.burnImage");
        }

        @Override
        public boolean needsSystemAwake() {
            return true;
        }

        @Override
        public void run(StepContext context) {
            Path file = image.apply(context);
            // The size was estimated before the image existed, or the disc may have been changed since.
            long size = sizeOf(file);
            long capacity = DiscCapacity.usableBytes(engine.readMedia(drive));
            if (size > capacity) {
                throw new RetroBurnException(
                        UserMessage.of("error.discTooSmall"),
                        "Image " + file + " has " + size + " bytes (" + ByteSizes.format(size) + "); the disc holds "
                                + capacity);
            }
            engine.burnImage(drive, file, new BurnOptions(true), context.burnListener(), context.cancelToken());
        }
    }

    record VerifyImage(BurnEngine engine, DriveId drive, ImageFile image, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.verify");
        }

        @Override
        public boolean needsSystemAwake() {
            return true;
        }

        @Override
        public void run(StepContext context) {
            engine.verifyImage(drive, image.apply(context), context.burnListener(), context.cancelToken());
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        }
    }
}
