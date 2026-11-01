// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny program that {@link ProcessRunnerTest} starts as a real child process, so the runner is
 * tested the same way on every OS without depending on shell utilities.
 */
public final class ChildProgram {

    private ChildProgram() {}

    public static void main(String[] args) throws IOException, InterruptedException {
        switch (args[0]) {
            case "both-streams" -> {
                System.out.println("out one");
                System.err.println("err one");
                System.out.println("out two");
            }
            case "carriage-returns" -> {
                System.out.print("10%\r20%\r30%\r\ndone\n");
                System.out.flush();
            }
            case "many-lines" -> {
                for (int i = 1; i <= Integer.parseInt(args[1]); i++) {
                    System.out.println("line " + i);
                }
            }
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            case "chatty-sleep" -> {
                for (int i = 0; i < Integer.parseInt(args[1]); i++) {
                    System.out.println("tick " + i);
                    Thread.sleep(100);
                }
            }
            case "echo-arguments" -> {
                for (int i = 1; i < args.length; i++) {
                    System.out.println("[" + args[i] + "]");
                }
            }
            case "binary" -> {
                // Every byte value, including line breaks, which a line reader would change.
                byte[] data = new byte[Integer.parseInt(args[1])];
                for (int i = 0; i < data.length; i++) {
                    data[i] = (byte) (i % 251);
                }
                System.out.write(data);
                System.out.flush();
                System.err.println("binary done");
            }
            case "wait-for-stdin-end" -> {
                System.out.println("waiting");
                System.out.flush();
                System.in.transferTo(OutputStream.nullOutputStream());
            }
            default -> throw new IllegalArgumentException(args[0]);
        }
    }

    /** The command that runs this program with the current JVM. */
    static List<String> command(String... arguments) {
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow());
        String classPath;
        try {
            classPath = Path.of(ChildProgram.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        List<String> command =
                new ArrayList<>(List.of(java.toString(), "-cp", classPath, ChildProgram.class.getName()));
        command.addAll(List.of(arguments));
        return command;
    }
}
