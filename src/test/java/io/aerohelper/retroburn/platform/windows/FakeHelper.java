// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.testsupport.ToolOutput;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the Windows helper in tests: a real child process that reads a request from stdin
 * and answers with captured helper output, so {@link HelperClient} is tested on every OS.
 */
public final class FakeHelper {

    private FakeHelper() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String request = input.readLine();
        switch (args[0]) {
            case "replay" -> print(ToolOutput.lines(args[1]));
            case "record-request" -> {
                Files.writeString(Path.of(args[1]), request, StandardCharsets.UTF_8);
                print(List.of("{\"event\":\"result\"}"));
            }
            case "replay-then-wait" -> {
                print(ToolOutput.lines(args[1]));
                waitForEnd(input);
                print(List.of("{\"event\":\"result\"}"));
            }
            case "stop-when-asked" -> {
                print(List.of("{\"event\":\"progress\",\"stage\":\"writing\",\"done\":1,\"total\":10}"));
                waitForEnd(input);
                print(List.of("{\"event\":\"error\",\"code\":\"CANCELLED\",\"message\":\"Stopped on request\"}"));
            }
            case "silent" -> Thread.sleep(Long.parseLong(args[1]));
            case "crash" -> {
                System.err.println("Add-Type : Cannot add type. Compilation errors occurred.");
                System.exit(1);
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    private static void print(List<String> lines) {
        lines.forEach(System.out::println);
        System.out.flush();
    }

    private static void waitForEnd(BufferedReader input) throws IOException {
        while (input.readLine() != null) {
            // Only the end of input matters.
        }
    }

    /** How to start this program with the current JVM, in place of PowerShell. */
    static HelperClient.Launch launch(String... arguments) {
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        Path classes;
        try {
            classes = Path.of(FakeHelper.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<String> command = new ArrayList<>(List.of("-cp", classes.toString(), FakeHelper.class.getName()));
        command.addAll(List.of(arguments));
        return new HelperClient.Launch(java, command, classes);
    }
}
