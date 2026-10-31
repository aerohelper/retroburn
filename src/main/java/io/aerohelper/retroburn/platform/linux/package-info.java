// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * RetroBurn on Linux: GNU xorriso for drives and ISO images, sysfs for hot-plug, USB detection and
 * power status, and systemd-inhibit for keeping the computer awake.
 *
 * <p>Main class: {@link io.aerohelper.retroburn.platform.linux.LinuxPlatform}. Every xorriso argument
 * is built in {@code XorrisoCommandBuilder}; every parser is tested against captured real output.
 * Only {@code app} may use this package. See {@code docs/design/LLD-linux-platform.md}.
 */
package io.aerohelper.retroburn.platform.linux;
