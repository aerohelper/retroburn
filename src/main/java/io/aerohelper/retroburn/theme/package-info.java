// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later

/**
 * Themes (JavaFX stylesheets, fonts, sounds) and the spinning disc: {@link
 * io.aerohelper.retroburn.theme.ThemeRegistry} reads {@code themes/<id>/}, {@link
 * io.aerohelper.retroburn.theme.Appearance} applies the current theme and plays its sounds, {@link
 * io.aerohelper.retroburn.theme.DiscAnimation} draws the disc moved by {@link
 * io.aerohelper.retroburn.theme.DiscMotion}.
 *
 * <p>See {@code docs/design/LLD-themes.md}, {@code docs/EXTENSIBILITY.md} section 4 and the theme
 * tokens in {@code docs/ux/UX_SPEC.md}.
 */
package io.aerohelper.retroburn.theme;
