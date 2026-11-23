// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.audio.AudioTools;
import io.aerohelper.retroburn.audio.CdWavWriter;
import io.aerohelper.retroburn.audio.TrackListCheck;
import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.common.OperationCancelledException;
import io.aerohelper.retroburn.common.RetroBurnException;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.AudioCdEngine;
import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.BlankMode;
import io.aerohelper.retroburn.engine.BurnEngine;
import io.aerohelper.retroburn.engine.CdText;
import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.pipeline.StepContext;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Builds the audio CD pipeline: convert the tracks in parallel, (erase a used CD-RW,) burn, check
 * the track list. Weights follow the samples each step handles. See {@code
 * docs/design/LLD-audio-cd.md} section 8.
 */
public final class AudioJobFactory {

    private final BurnEngine burnEngine;
    private final AudioCdEngine audioEngine;
    private final AudioTools tools;
    private final int workers;

    /** @param workers how many tracks to convert at once */
    public AudioJobFactory(BurnEngine burnEngine, AudioCdEngine audioEngine, AudioTools tools, int workers) {
        this.burnEngine = burnEngine;
        this.audioEngine = audioEngine;
        this.tools = tools;
        this.workers = Math.max(1, workers);
    }

    /** Workers for a machine with this many cores: all but one, at least one. */
    public static int defaultWorkers(int cores) {
        return Math.max(1, cores - 1);
    }

    /**
     * @param cdText write the compilation's CD-Text (the setting, when the engine can)
     * @param normalizeLoudness run {@code loudnorm} while converting
     */
    public Pipeline audioCd(BurnPlan plan, AudioCompilation compilation, boolean cdText, boolean normalizeLoudness) {
        if (plan.kind() != JobKind.AUDIO_CD) {
            throw new IllegalArgumentException("Expected an AUDIO_CD plan, got " + plan.kind());
        }
        if (!plan.canStart()) {
            throw new IllegalArgumentException("The plan has blockers: " + plan.blockers());
        }
        DriveId drive = plan.drive().orElseThrow().id();
        double samples = Math.max(1, compilation.audioSectors());
        AtomicReference<AudioDisc> disc = new AtomicReference<>();
        Optional<CdText> text =
                cdText && audioEngine.writesCdText() ? Optional.of(compilation.cdText()) : Optional.empty();
        List<PipelineStep> steps = new ArrayList<>();
        steps.add(new ConvertTracks(tools, compilation, text, normalizeLoudness, workers, disc, samples));
        if (plan.eraseFirst()) {
            steps.add(new BurnSteps.Blank(burnEngine, drive, BlankMode.QUICK, samples * 0.05));
        }
        steps.add(new BurnAudio(audioEngine, drive, disc, samples * 2));
        if (plan.verify()) {
            steps.add(new CheckTrackList(audioEngine, drive, disc, Math.max(1, samples * 0.01)));
        }
        return new Pipeline(steps);
    }

    /** Converts every track to a CD audio WAV file in the work folder, several at once. */
    record ConvertTracks(
            AudioTools tools,
            AudioCompilation compilation,
            Optional<CdText> cdText,
            boolean normalizeLoudness,
            int workers,
            AtomicReference<AudioDisc> disc,
            double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.convertTracks");
        }

        @Override
        public void run(StepContext context) {
            List<AudioCompilation.Track> tracks = compilation.tracks();
            double total = Math.max(1, compilation.audioSectors());
            DoubleAdder done = new DoubleAdder();
            CancelToken cancel = context.cancelToken();
            List<Future<AudioDisc.Track>> converted = new ArrayList<>();
            try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
                for (int index = 0; index < tracks.size(); index++) {
                    AudioCompilation.Track track = tracks.get(index);
                    String name = "%02d".formatted(index + 1);
                    converted.add(pool.submit(() -> {
                        Path pcm = context.workFolder().resolve(name + ".pcm");
                        Path wav = context.workFolder().resolve(name + ".wav");
                        double[] last = {0};
                        tools.toCdPcm(
                                track.source(),
                                pcm,
                                track.audio().duration(),
                                normalizeLoudness,
                                fraction -> {
                                    done.add((fraction - last[0]) * track.sectors());
                                    last[0] = fraction;
                                    context.reportProgress(Math.min(done.sum() / total, 1.0));
                                },
                                cancel);
                        try {
                            return new AudioDisc.Track(wav, CdWavWriter.fromPcm(pcm, wav, cancel));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }));
                }
                List<AudioDisc.Track> written = new ArrayList<>();
                for (Future<AudioDisc.Track> future : converted) {
                    written.add(await(future, cancel, converted));
                }
                disc.set(new AudioDisc(written, cdText));
            }
        }

        private static AudioDisc.Track await(
                Future<AudioDisc.Track> future, CancelToken cancel, List<Future<AudioDisc.Track>> all) {
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel.cancel();
                throw new OperationCancelledException();
            } catch (ExecutionException e) {
                // Stop the other conversions; one failed track fails the job.
                cancel.cancel();
                all.forEach(other -> other.cancel(true));
                if (e.getCause() instanceof RuntimeException runtime) {
                    throw runtime;
                }
                throw new IllegalStateException(e.getCause());
            }
        }
    }

    record BurnAudio(AudioCdEngine engine, DriveId drive, AtomicReference<AudioDisc> disc, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.burnAudio");
        }

        @Override
        public boolean needsSystemAwake() {
            return true;
        }

        @Override
        public void run(StepContext context) {
            engine.burnAudioCd(drive, disc.get(), context.burnListener(), context.cancelToken());
        }
    }

    record CheckTrackList(AudioCdEngine engine, DriveId drive, AtomicReference<AudioDisc> disc, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.checkTracks");
        }

        @Override
        public void run(StepContext context) {
            List<Long> written =
                    disc.get().tracks().stream().map(AudioDisc.Track::sectors).toList();
            TrackListCheck.difference(written, engine.readTrackLayout(drive)).ifPresent(difference -> {
                throw new RetroBurnException(UserMessage.of("error.trackListMismatch"), difference);
            });
            context.reportProgress(1.0);
        }
    }
}
