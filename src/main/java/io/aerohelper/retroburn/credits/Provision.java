// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.credits;

/** How a third-party component reaches the user's machine. */
public enum Provision {
    /** The Java runtime packaged with RetroBurn. */
    BUNDLED_RUNTIME,
    /** A Java library packaged with RetroBurn. */
    BUNDLED_LIBRARY,
    /** A native program shipped inside the RetroBurn installer (Windows). */
    BUNDLED_TOOL,
    /** A program installed from the user's Linux distribution. */
    OS_PACKAGE,
    /** Part of the operating system itself, used but not redistributed. */
    PART_OF_OS,
    /** A font, image or sound shipped with RetroBurn. */
    BUNDLED_ASSET
}
