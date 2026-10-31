// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/** There is not enough free disk space in the work folder (or output folder) for a job. */
public class WorkFolderFullException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    public WorkFolderFullException(UserMessage userMessage, String technicalDetail) {
        super(userMessage, technicalDetail);
    }
}
