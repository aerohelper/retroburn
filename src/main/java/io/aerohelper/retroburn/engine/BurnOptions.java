// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

/**
 * Settings for {@link BurnEngine#burnImage}.
 *
 * @param closeMedia finalise the disc so nothing more can be added; ignored by media that cannot be
 *     closed, such as DVD+RW
 */
public record BurnOptions(boolean closeMedia) {}
