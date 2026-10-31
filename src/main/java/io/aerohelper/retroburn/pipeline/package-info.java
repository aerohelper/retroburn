// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Runs a job as ordered, weighted, cancellable steps, owns the job's work folder and holds the
 * sleep inhibitor while writing.
 *
 * <p>Empty until the core milestone. See {@code docs/architecture/HLD.md} section 6.
 */
package io.aerohelper.retroburn.pipeline;
