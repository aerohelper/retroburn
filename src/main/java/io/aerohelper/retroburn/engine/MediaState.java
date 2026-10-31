// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

/** What is on the medium. */
public enum MediaState {
    BLANK,
    /** Written, with room for another session. */
    APPENDABLE,
    /** Written and finalised; nothing more can be added. */
    CLOSED,
    NO_MEDIA,
    UNKNOWN
}
