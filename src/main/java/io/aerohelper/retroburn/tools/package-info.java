// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Running external programs safely, finding them, learning their versions, and deciding which
 * features they make available.
 *
 * <p>Main classes: {@link io.aerohelper.retroburn.tools.ProcessRunner} (the only class that starts
 * processes: argument lists, drained output, timeouts, cancellation), {@link
 * io.aerohelper.retroburn.tools.ToolProbe} and {@link io.aerohelper.retroburn.tools.FeatureGate}.
 * Tool-specific command lines and output parsing live with their callers, mostly in the platform
 * packages. See {@code docs/design/LLD-tools.md}.
 */
package io.aerohelper.retroburn.tools;
