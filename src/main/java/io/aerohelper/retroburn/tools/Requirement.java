// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import io.aerohelper.retroburn.common.UserMessage;
import java.util.Objects;
import java.util.Optional;

/** Something a feature needs before it can be offered. */
public sealed interface Requirement {

    /** Why the requirement is not met by {@code report}, or empty when it is met. */
    Optional<UserMessage> unmetReason(ToolReport report);

    /** The tool must be installed and working. */
    record ToolPresent(ExternalTool tool) implements Requirement {

        public ToolPresent {
            Objects.requireNonNull(tool, "tool");
        }

        @Override
        public Optional<UserMessage> unmetReason(ToolReport report) {
            return report.isUsable(tool)
                    ? Optional.empty()
                    : Optional.of(UserMessage.of("requirement.toolMissing", tool.displayName()));
        }
    }

    /** The installed FFmpeg must include the named encoder, for example {@code libmp3lame}. */
    record FfmpegEncoderPresent(String encoder) implements Requirement {

        public FfmpegEncoderPresent {
            Objects.requireNonNull(encoder, "encoder");
        }

        @Override
        public Optional<UserMessage> unmetReason(ToolReport report) {
            return report.ffmpegEncoders().contains(encoder)
                    ? Optional.empty()
                    : Optional.of(UserMessage.of("requirement.ffmpegEncoderMissing", encoder));
        }
    }
}
