// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/**
 * Operating-system families RetroBurn knows about.
 *
 * <p>This is plain data, used for example to pick which third-party components the credits screen
 * lists. Working out which OS the app is running on is the job of the {@code platform} package.
 */
public enum OperatingSystem {
    LINUX,
    WINDOWS,
    /** Any OS RetroBurn does not support yet (for example macOS). */
    OTHER
}
