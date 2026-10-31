// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

import java.util.Objects;

/**
 * Base of all errors RetroBurn shows to the user.
 *
 * <p>Each error carries a {@link UserMessage} that says in plain words what went wrong and what to do,
 * and technical detail (command line, exit code, last output lines) for "Show details". It is
 * unchecked so it can cross lambdas and streams; callers that can recover catch the specific
 * subclasses.
 */
public class RetroBurnException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UserMessage userMessage;
    private final String technicalDetail;

    public RetroBurnException(UserMessage userMessage, String technicalDetail) {
        this(userMessage, technicalDetail, null);
    }

    public RetroBurnException(UserMessage userMessage, String technicalDetail, Throwable cause) {
        super(userMessage.key() + ": " + technicalDetail, cause);
        this.userMessage = Objects.requireNonNull(userMessage, "userMessage");
        this.technicalDetail = Objects.requireNonNullElse(technicalDetail, "");
    }

    /** What to tell the user. */
    public UserMessage userMessage() {
        return userMessage;
    }

    /** Details for the log and the "Show details" view; may be empty. */
    public String technicalDetail() {
        return technicalDetail;
    }
}
