// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/** An external program ran but failed; the technical detail holds its command line and output. */
public class ToolFailedException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    public ToolFailedException(UserMessage userMessage, String technicalDetail) {
        super(userMessage, technicalDetail);
    }

    public ToolFailedException(UserMessage userMessage, String technicalDetail, Throwable cause) {
        super(userMessage, technicalDetail, cause);
    }
}
