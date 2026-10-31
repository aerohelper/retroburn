// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.common;

/** A required external program is not installed or could not be found. */
public class ToolMissingException extends RetroBurnException {

    private static final long serialVersionUID = 1L;

    /** @param toolName the program's name as users know it, for example "xorriso" */
    public ToolMissingException(String toolName) {
        super(UserMessage.of("error.toolMissing", toolName), toolName + " was not found");
    }
}
