// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

import io.aerohelper.retroburn.engine.DriveInfo;
import java.util.List;
import java.util.function.Consumer;

/**
 * Notices drives being plugged in or removed while the app runs.
 *
 * <p>Must stay quiet while paused, because looking at drives can disturb one that is writing. The
 * burn pipeline pauses it for the whole burn.
 */
public interface DriveWatcher extends AutoCloseable {

    /** Starts watching; the listener first receives the current drive list. */
    void start(Consumer<List<DriveInfo>> listener);

    /** Stops looking at drives until {@link #resume()}. */
    void pause();

    /** Looks at drives again, immediately and then regularly. */
    void resume();

    /** Stops watching for good. */
    @Override
    void close();
}
