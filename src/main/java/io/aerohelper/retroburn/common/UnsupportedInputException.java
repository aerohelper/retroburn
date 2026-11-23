// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/** An input file or name cannot be used, for example a name that a disc format cannot store. */
public class UnsupportedInputException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    public UnsupportedInputException(UserMessage userMessage, String technicalDetail) {
        super(userMessage, technicalDetail);
    }
}
