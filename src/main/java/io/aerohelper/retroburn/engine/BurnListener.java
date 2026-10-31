// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

/** Receives events from an engine, on the thread doing the work. Implementations must be quick. */
@FunctionalInterface
public interface BurnListener {

    void onEvent(BurnEvent event);

    /** A listener that ignores everything. */
    static BurnListener ignoring() {
        return event -> {};
    }
}
