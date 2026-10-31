// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.List;
import java.util.stream.Stream;

/**
 * Everything that goes onto a disc, as a stream of entries.
 *
 * <p>A stream rather than a list so that very large compilations can be produced on demand instead
 * of being held in memory twice. Each call returns a new stream; callers close it.
 */
@FunctionalInterface
public interface DiscTree {

    Stream<DiscEntry> entries();

    /** A tree backed by a fixed list, for small compilations and tests. */
    static DiscTree of(List<DiscEntry> entries) {
        List<DiscEntry> copy = List.copyOf(entries);
        return copy::stream;
    }
}
