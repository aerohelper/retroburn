// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/** The user cancelled the operation. Not a failure: callers report it as "cancelled". */
public class OperationCancelledException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    public OperationCancelledException() {
        super(UserMessage.of("error.cancelled"), "Cancelled by the user");
    }
}
