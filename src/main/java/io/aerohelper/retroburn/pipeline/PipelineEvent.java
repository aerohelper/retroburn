// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEvent;

/** What a running pipeline reports to its listener. */
public sealed interface PipelineEvent {

    /**
     * A step began.
     *
     * @param index position of the step, starting at 0
     * @param stepCount number of steps in the pipeline
     * @param name what the step does
     */
    record StepStarted(int index, int stepCount, UserMessage name) implements PipelineEvent {}

    /** A step completed successfully. */
    record StepFinished(int index, UserMessage name) implements PipelineEvent {}

    /** Progress of the whole job, 0.0 to 1.0; never decreases. */
    record OverallProgress(double fraction) implements PipelineEvent {}

    /** An event from the running step's engine, passed through (progress details, log lines). */
    record StepOutput(int index, BurnEvent event) implements PipelineEvent {}

    /** The computer could not be kept awake; the UI should warn the user. */
    record SleepInhibitionUnavailable(String technicalDetail) implements PipelineEvent {}
}
