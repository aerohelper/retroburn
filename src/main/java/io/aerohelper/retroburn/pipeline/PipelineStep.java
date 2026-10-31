// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.UserMessage;

/**
 * One stage of a job, such as "Converting tracks" or "Writing disc".
 *
 * <p>Steps run one after another on the pipeline thread. A step reports its own progress from 0 to
 * 1 through the {@link StepContext}, checks the cancel token in every long loop, and throws a
 * {@link io.aerohelper.retroburn.common.RetroBurnException} to fail the job.
 */
public interface PipelineStep {

    /** What the step does, for the progress view. */
    UserMessage name();

    /** This step's share of the whole job, relative to the other steps' weights; must be positive. */
    double weight();

    /**
     * Whether the computer must stay awake from this step on (writing a disc, for example). The
     * runner keeps it awake from the first such step until the job ends, so a following verify step
     * is covered too.
     */
    default boolean needsSystemAwake() {
        return false;
    }

    void run(StepContext context);
}
