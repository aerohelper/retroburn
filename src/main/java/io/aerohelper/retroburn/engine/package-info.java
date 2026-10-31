// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * OS-neutral contracts for drives, media and disc images, and their value types and events.
 *
 * <p>Main types: {@link io.aerohelper.retroburn.engine.BurnEngine}, {@link
 * io.aerohelper.retroburn.engine.DiscImageBuilder}, {@link io.aerohelper.retroburn.engine.DiscTree},
 * {@link io.aerohelper.retroburn.engine.BurnEvent}. Depends only on {@code common}; implementations
 * live in the {@code platform.<os>} packages. See {@code docs/design/LLD-engine-and-platform.md}.
 */
package io.aerohelper.retroburn.engine;
