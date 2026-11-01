// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

/** How thoroughly {@link BurnEngine#blank} erases a rewritable disc. */
public enum BlankMode {
    /** Makes the disc reusable; usually takes seconds. */
    QUICK,
    /** Erases every sector; can take as long as writing the whole disc. */
    FULL
}
