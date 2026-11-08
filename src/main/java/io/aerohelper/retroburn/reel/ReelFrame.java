// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

/**
 * One frame of a reel.
 *
 * @param index frame number from 0
 * @param seconds time in the reel
 * @param scene the scene that draws it
 * @param sceneFraction how far into the scene, 0 to 1
 */
public record ReelFrame(int index, double seconds, ReelScene scene, double sceneFraction) {}
