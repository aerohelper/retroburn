// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.awt.Graphics2D;

/**
 * One part of a burn reel. {@link #render} must be pure: the same frame, session and style give the
 * same pixels. See {@code docs/EXTENSIBILITY.md} section 7.
 */
public interface ReelScene {

    String id();

    /** Draws the frame on a {@link ReelStyle#WIDTH} × {@link ReelStyle#HEIGHT} canvas. */
    void render(Graphics2D graphics, ReelFrame frame, BurnSession session, ReelStyle style);
}
