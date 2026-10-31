// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class ToolProbeTest {

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    void reportsToolsThatAreNotInstalled() {
        ToolProbe probe = new ToolProbe(tool -> Optional.empty(), runner);

        ToolReport report = probe.probeAll(List.of(ExternalTool.XORRISO, ExternalTool.FFMPEG));

        assertEquals(
                ToolStatus.State.MISSING,
                report.statuses().get(ExternalTool.XORRISO).state());
        assertTrue(report.ffmpegEncoders().isEmpty());
    }

    @Test
    void reportsAProgramThatDoesNotPrintTheExpectedVersionAsNotWorking() {
        // The JVM stands in for a broken xorriso: it runs, but "-version" prints Java's version.
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        ToolProbe probe = new ToolProbe(tool -> Optional.of(java), runner);

        ToolStatus status = probe.probe(ExternalTool.XORRISO);

        assertEquals(ToolStatus.State.NOT_WORKING, status.state());
        assertFalse(status.detail().isBlank());
    }

    /** Runs the real tools when they are installed, as on the Linux CI runner. */
    @Test
    @EnabledOnOs(OS.LINUX)
    void recognisesTheRealToolsOnLinux() {
        PathSearchToolLocator locator = new PathSearchToolLocator(
                PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator), List.of(""));
        assumeTrue(locator.locate(ExternalTool.XORRISO).isPresent(), "xorriso is not installed");
        ToolProbe probe = new ToolProbe(locator, runner);

        ToolReport report = probe.probeAll(EnumSet.allOf(ExternalTool.class));

        for (ExternalTool tool : ExternalTool.values()) {
            if (locator.locate(tool).isPresent()) {
                assertTrue(
                        report.isUsable(tool), tool + ": " + report.statuses().get(tool));
            }
        }
        if (report.isUsable(ExternalTool.FFMPEG)) {
            assertFalse(report.ffmpegEncoders().isEmpty());
        }
    }
}
