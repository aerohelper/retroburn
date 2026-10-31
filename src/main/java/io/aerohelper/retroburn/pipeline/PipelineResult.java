// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

import io.aerohelper.retroburn.common.RetroBurnException;
import java.nio.file.Path;
import java.util.Objects;

/** How a job ended. */
public sealed interface PipelineResult {

    /** Every step completed. */
    record Succeeded() implements PipelineResult {}

    /** The user cancelled the job. */
    record Cancelled() implements PipelineResult {}

    /**
     * A step failed.
     *
     * @param error what went wrong, with a message for the user
     * @param keptWorkFolder the job's work folder, kept so its files and logs can be inspected
     */
    record Failed(RetroBurnException error, Path keptWorkFolder) implements PipelineResult {

        public Failed {
            Objects.requireNonNull(error, "error");
            Objects.requireNonNull(keptWorkFolder, "keptWorkFolder");
        }
    }
}
