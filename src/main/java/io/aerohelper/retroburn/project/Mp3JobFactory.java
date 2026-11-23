// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.AudioTools;
import io.aerohelper.retroburn.audio.Mp3Encoding;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.ImageOptions;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.pipeline.StepContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Builds the MP3 disc pipeline from an {@link Mp3DiscPlan}: convert what is not MP3 yet, write the
 * playlists, build the image (unchanged MP3s straight from their sources), then burn and verify as
 * for data discs. See {@code docs/design/LLD-mp3-disc.md} section 6.
 */
public final class Mp3JobFactory {

    static final String IMAGE_NAME = "disc.iso";

    private final BurnJobFactory dataJobs;
    private final DiscImageBuilder imageBuilder;
    private final AudioTools tools;
    private final int workers;

    public Mp3JobFactory(BurnJobFactory dataJobs, DiscImageBuilder imageBuilder, AudioTools tools, int workers) {
        this.dataJobs = dataJobs;
        this.imageBuilder = imageBuilder;
        this.tools = tools;
        this.workers = Math.max(1, workers);
    }

    /** Burn the plan to the disc in the plan's drive. */
    public Pipeline burn(BurnPlan burnPlan, Mp3DiscPlan plan, VolumeLabel label, Mp3Encoding.Bitrate bitrate) {
        if (burnPlan.kind() != JobKind.MP3_DISC) {
            throw new IllegalArgumentException("Expected an MP3_DISC plan, got " + burnPlan.kind());
        }
        BurnJobFactory.drive(burnPlan);
        BurnSteps.ImageFile image = context -> context.workFolder().resolve(IMAGE_NAME);
        List<PipelineStep> steps = new ArrayList<>(prepare(plan, label, bitrate, image));
        steps.addAll(dataJobs.writeSteps(burnPlan, image));
        return new Pipeline(steps);
    }

    /** The plan as an image file the user chose; no drive involved. */
    public Pipeline createIso(Mp3DiscPlan plan, VolumeLabel label, Mp3Encoding.Bitrate bitrate, Path target) {
        return new Pipeline(prepare(plan, label, bitrate, context -> target));
    }

    private List<PipelineStep> prepare(
            Mp3DiscPlan plan, VolumeLabel label, Mp3Encoding.Bitrate bitrate, BurnSteps.ImageFile image) {
        List<PipelineStep> steps = new ArrayList<>();
        if (plan.needsConversion()) {
            double converted = plan.entries().stream()
                    .filter(Mp3DiscPlan.Entry::convert)
                    .mapToDouble(entry -> entry.item().audio().duration().toMillis())
                    .sum();
            steps.add(new ConvertToMp3(tools, plan, bitrate, workers, Math.max(1, converted / 100)));
        }
        steps.add(new BuildMp3Image(imageBuilder, plan, label, image, Math.max(1, plan.estimatedBytes() / 1e6)));
        return steps;
    }

    /** Where entry {@code index}'s converted file goes in the work folder. */
    static Path convertedFile(StepContext context, int index) {
        return context.workFolder().resolve("mp3").resolve("%05d.mp3".formatted(index));
    }

    static Path playlistFile(StepContext context, int index) {
        return context.workFolder().resolve("playlists").resolve(Integer.toString(index));
    }

    record ConvertToMp3(AudioTools tools, Mp3DiscPlan plan, Mp3Encoding.Bitrate bitrate, int workers, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.convertMp3");
        }

        @Override
        public void run(StepContext context) {
            try {
                Files.createDirectories(context.workFolder().resolve("mp3"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            List<Mp3DiscPlan.Entry> entries = plan.entries();
            double total = entries.stream()
                    .filter(Mp3DiscPlan.Entry::convert)
                    .mapToDouble(entry -> entry.item().audio().duration().toMillis())
                    .sum();
            DoubleAdder done = new DoubleAdder();
            CancelToken cancel = context.cancelToken();
            List<Future<?>> running = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
                for (int index = 0; index < entries.size(); index++) {
                    Mp3DiscPlan.Entry entry = entries.get(index);
                    if (!entry.convert()) {
                        continue;
                    }
                    Path target = convertedFile(context, index);
                    double millis = entry.item().audio().duration().toMillis();
                    running.add(pool.submit(() -> {
                        double[] last = {0};
                        tools.toMp3(
                                entry.item().source(),
                                target,
                                entry.item().audio().duration(),
                                bitrate,
                                fraction -> {
                                    done.add((fraction - last[0]) * millis);
                                    last[0] = fraction;
                                    context.reportProgress(Math.min(done.sum() / Math.max(1, total), 1.0));
                                },
                                cancel);
                    }));
                }
                for (Future<?> future : running) {
                    try {
                        future.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancel.cancel();
                        throw new OperationCancelledException();
                    } catch (ExecutionException e) {
                        cancel.cancel();
                        running.forEach(other -> other.cancel(true));
                        if (e.getCause() instanceof RuntimeException runtime) {
                            throw runtime;
                        }
                        throw new IllegalStateException(e.getCause());
                    }
                }
            }
        }
    }

    record BuildMp3Image(
            DiscImageBuilder builder, Mp3DiscPlan plan, VolumeLabel label, BurnSteps.ImageFile image, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.buildImage");
        }

        @Override
        public void run(StepContext context) {
            List<DiscEntry> entries = new ArrayList<>();
            for (int index = 0; index < plan.entries().size(); index++) {
                Mp3DiscPlan.Entry entry = plan.entries().get(index);
                entries.add(new DiscEntry(
                        entry.discPath(),
                        entry.convert()
                                ? convertedFile(context, index)
                                : entry.item().source()));
            }
            try {
                for (int index = 0; index < plan.playlists().size(); index++) {
                    Mp3DiscPlan.Playlist playlist = plan.playlists().get(index);
                    Path file = playlistFile(context, index);
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, playlist.text(), playlist.charset());
                    entries.add(new DiscEntry(playlist.discPath(), file));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            builder.buildDataImage(
                    DiscTree.of(entries),
                    new ImageOptions(label.text()),
                    image.apply(context),
                    context.burnListener(),
                    context.cancelToken());
        }
    }
}
