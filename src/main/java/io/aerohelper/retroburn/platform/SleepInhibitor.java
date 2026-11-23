// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform;

/**
 * Keeps the computer from going to sleep while a disc is written.
 *
 * <p>Implementations must make sure the request ends when RetroBurn ends, even after a crash, and
 * must never change OS power settings.
 */
public interface SleepInhibitor {

    /**
     * Asks the OS not to sleep until the returned inhibition is closed.
     *
     * @param reason shown by OS tools that list sleep blockers, for example "Burning a disc"
     * @throws SleepInhibitionUnavailableException if the OS refuses or the facility is missing
     */
    SleepInhibition acquire(String reason);

    /** An active "do not sleep" request. Closing it more than once is harmless. */
    interface SleepInhibition extends AutoCloseable {

        @Override
        void close();
    }

    /** An inhibitor for platforms or tests where there is nothing to do. */
    static SleepInhibitor none() {
        return reason -> () -> {};
    }
}
