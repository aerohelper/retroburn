// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/**
 * Lets the user stop a running job. Long-running code checks it regularly.
 *
 * <p>Thread-safe: {@link #cancel()} is called from the UI thread while workers check it.
 */
public final class CancelToken {

    private final CancelToken parent;
    private volatile boolean cancelled;

    public CancelToken() {
        this(null);
    }

    private CancelToken(CancelToken parent) {
        this.parent = parent;
    }

    /** A token that is never cancelled, for work the user cannot stop. */
    public static CancelToken none() {
        return new CancelToken();
    }

    /**
     * A token that is cancelled when this one is, and can also be cancelled on its own without
     * affecting this one: for stopping a step early for a reason of the code's own.
     */
    public CancelToken child() {
        return new CancelToken(this);
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled || (parent != null && parent.isCancelled());
    }

    /** @throws OperationCancelledException if the user has cancelled */
    public void throwIfCancelled() {
        if (isCancelled()) {
            throw new OperationCancelledException();
        }
    }
}
