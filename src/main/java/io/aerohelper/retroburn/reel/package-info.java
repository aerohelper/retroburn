// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Burn sessions and the burn reel: {@link io.aerohelper.retroburn.reel.SessionRecorder} records a
 * burn, {@link io.aerohelper.retroburn.reel.SessionStore} keeps it as JSON, and {@link
 * io.aerohelper.retroburn.reel.ReelMaker} draws the scenes of a {@link
 * io.aerohelper.retroburn.reel.ReelTimeline} with Java2D and streams them into FFmpeg. No JavaFX.
 *
 * <p>See {@code docs/design/LLD-burn-reel.md}, ADR-0018 and {@code docs/EXTENSIBILITY.md} section 7.
 */
package io.aerohelper.retroburn.reel;
