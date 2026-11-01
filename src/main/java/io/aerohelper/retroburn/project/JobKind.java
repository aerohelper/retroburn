// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

/** Jobs that use a drive. Creating an ISO needs none and is planned separately. */
public enum JobKind {
    /** Files and folders: build an image in the work folder, burn it, verify it. */
    DATA_DISC,
    /** An existing image file: burn it, verify it. */
    BURN_ISO,
    /** Erase a rewritable disc. */
    ERASE,
    /** Music files: convert to CD audio in the work folder, burn, check the track list. */
    AUDIO_CD
}
