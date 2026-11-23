// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/**
 * Lets the user stop a running job. Long-running code checks it regularly.
 *
 * <p>Thread-safe: {@link #cancel()} is called from the UI thread while workers check it.
 */
public final class CancelToken {

    private volatile boolean cancelled;

    /** A token that is never cancelled, for work the user cannot stop. */
    public static CancelToken none() {
        return new CancelToken();
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /** @throws OperationCancelledException if the user has cancelled */
    public void throwIfCancelled() {
        if (cancelled) {
            throw new OperationCancelledException();
        }
    }
}
