// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * The boundary between RetroBurn and the operating system.
 *
 * <p>{@link io.aerohelper.retroburn.platform.PlatformServices} is the interface the rest of the app
 * uses; each OS implements it in its own sub-package ({@code simulated} now, {@code linux} and
 * {@code windows} as the burning engines are built). Only those sub-packages may contain
 * OS-specific code, and only the composition root in {@code app} chooses between them. Architecture
 * tests enforce both rules.
 *
 * <p>See {@code docs/architecture/HLD.md} sections 1 and 4.1, {@code docs/design/LLD-app.md} and
 * {@code docs/EXTENSIBILITY.md}.
 */
package io.aerohelper.retroburn.platform;
