// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.PathSearchToolLocator;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessResult;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prints what the real cdrskin reports when writing to a file standing in for a drive, so its
 * progress and table-of-contents output can be captured as fixtures. Runs on Linux when cdrskin is
 * installed.
 */
@EnabledOnOs(OS.LINUX)
class CdrskinCaptureIntegrationTest {

    @TempDir
    Path folder;

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    void printsBurnProgressAndTableOfContents() throws IOException {
        Path cdrskin = new PathSearchToolLocator(
                        PathSearchToolLocator.foldersFromPathVariable(System.getenv("PATH"), File.pathSeparator),
                        List.of(""))
                .locate(ExternalTool.CDRSKIN)
                .orElse(null);
        assumeTrue(cdrskin != null, "cdrskin is not installed");
        byte[] data = new byte[20 * 1024 * 1024];
        new Random(1).nextBytes(data);
        Path track = Files.write(folder.resolve("track.bin"), data);
        String drive = "dev=stdio:" + folder.resolve("drive.bin");

        print(cdrskin, List.of("-v", "--allow_emulated_drives", drive, track.toString()));
        print(cdrskin, List.of("--allow_emulated_drives", drive, "-toc"));
        print(cdrskin, List.of("--allow_emulated_drives", drive, "-minfo"));
    }

    private void print(Path executable, List<String> arguments) {
        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        ProcessResult result = runner.run(
                ProcessRequest.builder(executable, arguments)
                        .workingDirectory(folder)
                        .totalTimeout(Duration.ofMinutes(2))
                        .onStdoutLine(line -> lines.add("out: " + line))
                        .onStderrLine(line -> lines.add("err: " + line))
                        .build(),
                new CancelToken());
        System.out.println("CAPTURE $ cdrskin " + String.join(" ", arguments) + "\n" + String.join("\n", lines)
                + "\n[exit " + result.exitCode() + "]");
    }
}
