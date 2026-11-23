// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.tools.Requirement.FfmpegEncoderPresent;
import io.aerohelper.retroburn.tools.Requirement.ToolPresent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FeatureGateTest {

    private static final Map<Feature, List<Requirement>> REQUIREMENTS = Map.of(
            Feature.BURNING, List.of(new ToolPresent(ExternalTool.XORRISO)),
            Feature.MP3_TRANSCODING,
                    List.of(new ToolPresent(ExternalTool.FFMPEG), new FfmpegEncoderPresent("libmp3lame")),
            Feature.BURN_REEL, List.of(new ToolPresent(ExternalTool.FFMPEG)));

    @Test
    void missingToolDisablesOnlyTheFeaturesThatNeedIt() {
        ToolReport report = report(Set.of(ExternalTool.FFMPEG), Set.of("libmp3lame"));

        Map<Feature, FeatureAvailability> result = FeatureGate.evaluate(report, REQUIREMENTS);

        assertFalse(result.get(Feature.BURNING).isAvailable());
        assertEquals(
                List.of(UserMessage.of("requirement.toolMissing", "GNU xorriso")),
                result.get(Feature.BURNING).reasons());
        assertTrue(result.get(Feature.MP3_TRANSCODING).isAvailable());
        assertTrue(result.get(Feature.BURN_REEL).isAvailable());
    }

    @Test
    void ffmpegWithoutMp3EncoderDisablesOnlyMp3Transcoding() {
        ToolReport report = report(Set.of(ExternalTool.FFMPEG, ExternalTool.XORRISO), Set.of("libx264"));

        Map<Feature, FeatureAvailability> result = FeatureGate.evaluate(report, REQUIREMENTS);

        assertEquals(
                List.of(UserMessage.of("requirement.ffmpegEncoderMissing", "libmp3lame")),
                result.get(Feature.MP3_TRANSCODING).reasons());
        assertTrue(result.get(Feature.BURN_REEL).isAvailable());
        assertTrue(result.get(Feature.BURNING).isAvailable());
    }

    @Test
    void featuresThePlatformDoesNotListAreNotSupported() {
        Map<Feature, FeatureAvailability> result = FeatureGate.evaluate(report(Set.of(), Set.of()), REQUIREMENTS);

        assertEquals(
                List.of(UserMessage.of("requirement.notSupportedHere")),
                result.get(Feature.VIDEO_DVD).reasons());
        assertEquals(Feature.values().length, result.size());
    }

    @Test
    void repeatsEachReasonOnlyOnce() {
        Map<Feature, List<Requirement>> duplicated = Map.of(
                Feature.AUDIO_CD, List.of(new ToolPresent(ExternalTool.FFMPEG), new ToolPresent(ExternalTool.FFMPEG)));

        FeatureAvailability audio =
                FeatureGate.evaluate(report(Set.of(), Set.of()), duplicated).get(Feature.AUDIO_CD);

        assertEquals(1, audio.reasons().size());
    }

    private static ToolReport report(Set<ExternalTool> working, Set<String> encoders) {
        Map<ExternalTool, ToolStatus> statuses = new java.util.EnumMap<>(ExternalTool.class);
        for (ExternalTool tool : ExternalTool.values()) {
            statuses.put(
                    tool,
                    working.contains(tool)
                            ? ToolStatus.found(tool, Path.of(tool.executableName()), "1.0")
                            : ToolStatus.missing(tool));
        }
        return new ToolReport(statuses, encoders);
    }
}
