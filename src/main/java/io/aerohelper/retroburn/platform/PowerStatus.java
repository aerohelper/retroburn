// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

/** Tells whether the computer runs on battery, so a burn can warn the user to plug in. */
@FunctionalInterface
public interface PowerStatus {

    /** Where power comes from right now. */
    PowerSource current();

    /** Power sources RetroBurn distinguishes. */
    enum PowerSource {
        MAINS,
        BATTERY,
        /** Could not be determined; no warning is shown. */
        UNKNOWN
    }
}
