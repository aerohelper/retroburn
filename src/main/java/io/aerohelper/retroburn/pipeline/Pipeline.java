// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import java.util.List;

/**
 * The ordered steps of a job.
 *
 * @param steps at least one step, each with a positive weight
 */
public record Pipeline(List<PipelineStep> steps) {

    public Pipeline {
        steps = List.copyOf(steps);
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("A pipeline needs at least one step");
        }
        for (PipelineStep step : steps) {
            if (!(step.weight() > 0)) {
                throw new IllegalArgumentException("Step weight must be positive: " + step.name());
            }
        }
    }

    public static Pipeline of(PipelineStep... steps) {
        return new Pipeline(List.of(steps));
    }

    double totalWeight() {
        return steps.stream().mapToDouble(PipelineStep::weight).sum();
    }
}
