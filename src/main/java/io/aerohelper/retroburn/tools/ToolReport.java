// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.util.Map;
import java.util.Set;

/**
 * The result of checking all external tools at start-up.
 *
 * @param statuses status of every checked tool
 * @param ffmpegEncoders encoder names the installed FFmpeg offers; empty if FFmpeg is unusable
 */
public record ToolReport(Map<ExternalTool, ToolStatus> statuses, Set<String> ffmpegEncoders) {

    public ToolReport {
        statuses = Map.copyOf(statuses);
        ffmpegEncoders = Set.copyOf(ffmpegEncoders);
    }

    /** Whether the tool was checked and works. */
    public boolean isUsable(ExternalTool tool) {
        ToolStatus status = statuses.get(tool);
        return status != null && status.isUsable();
    }
}
