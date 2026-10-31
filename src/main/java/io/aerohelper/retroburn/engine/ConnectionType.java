// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

/** How a drive is attached, as far as the platform can tell. */
public enum ConnectionType {
    /** Attached over USB, so power and unplugging advice applies. */
    USB,
    /** Known not to be USB (for example SATA). */
    OTHER,
    /** Could not be determined; the UI shows nothing rather than guessing. */
    UNKNOWN
}
