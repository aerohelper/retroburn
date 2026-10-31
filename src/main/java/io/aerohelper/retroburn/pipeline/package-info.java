// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Runs a job as ordered, weighted, cancellable steps, owns the job's work folder and keeps the
 * computer awake while a disc is written.
 *
 * <p>Main classes: {@link io.aerohelper.retroburn.pipeline.PipelineRunner}, {@link
 * io.aerohelper.retroburn.pipeline.PipelineStep}, {@link io.aerohelper.retroburn.pipeline.WorkFolders}.
 * Project types build their pipelines from steps; the UI listens to {@link
 * io.aerohelper.retroburn.pipeline.PipelineEvent}s. See {@code docs/design/LLD-pipeline.md}.
 */
package io.aerohelper.retroburn.pipeline;
