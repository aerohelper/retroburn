// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.BurnListener;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;

/**
 * What a running step may use. Created by {@link PipelineRunner} for each step.
 *
 * <p>The step's {@link #burnListener()} can be handed straight to an engine: engine progress events
 * become the step's progress, and every event is passed on to the pipeline's listener.
 */
public final class StepContext {

    private final Path workFolder;
    private final CancelToken cancelToken;
    private final DoubleConsumer progress;
    private final BurnListener output;

    StepContext(Path workFolder, CancelToken cancelToken, DoubleConsumer progress, BurnListener output) {
        this.workFolder = workFolder;
        this.cancelToken = cancelToken;
        this.progress = progress;
        this.output = output;
    }

    /** This job's private temporary folder; it exists and is empty when the first step starts. */
    public Path workFolder() {
        return workFolder;
    }

    public CancelToken cancelToken() {
        return cancelToken;
    }

    /** Reports how far this step is, from 0.0 to 1.0. Values outside that range are clamped. */
    public void reportProgress(double fraction) {
        progress.accept(fraction);
    }

    /** Passes an engine event on, and takes its progress as this step's progress. */
    public BurnListener burnListener() {
        return event -> {
            if (event instanceof BurnEvent.Progress progressEvent) {
                reportProgress(progressEvent.fraction());
            }
            output.onEvent(event);
        };
    }
}
