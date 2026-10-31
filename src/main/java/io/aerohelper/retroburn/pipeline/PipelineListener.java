// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.pipeline;

/**
 * Receives a running pipeline's events, one at a time, on background threads (the pipeline thread
 * or a tool's output thread). UI listeners must hand updates to the JavaFX Application Thread
 * themselves.
 */
@FunctionalInterface
public interface PipelineListener {

    void onEvent(PipelineEvent event);
}
