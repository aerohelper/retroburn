// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Everything needed to run an external program: the executable and a list of arguments, never a
 * shell command string.
 *
 * <p>Line consumers are called from background threads, possibly concurrently for stdout and
 * stderr, and must be thread-safe and quick.
 *
 * @param executable absolute path of the program
 * @param arguments arguments passed as-is, without shell interpretation
 * @param workingDirectory folder the program runs in, or the current one
 * @param totalTimeout maximum run time
 * @param idleTimeout maximum time without any output, which catches hung devices
 * @param charset encoding of the program's output
 * @param stdoutLines receives each stdout line, unless {@code stdoutSink} is set
 * @param stderrLines receives each stderr line
 * @param stdoutSink receives stdout unchanged, as bytes, instead of {@code stdoutLines}
 * @param stdinFile a file the program reads as its standard input; otherwise it gets none
 * @param stdinWriter writes the program's standard input from a background thread; the stream is
 *     closed afterwards. Not combined with {@code stdinFile}.
 */
public record ProcessRequest(
        Path executable,
        List<String> arguments,
        Optional<Path> workingDirectory,
        Optional<Duration> totalTimeout,
        Optional<Duration> idleTimeout,
        Charset charset,
        Consumer<String> stdoutLines,
        Consumer<String> stderrLines,
        Optional<OutputStream> stdoutSink,
        Optional<Path> stdinFile,
        Optional<StdinWriter> stdinWriter) {

    public ProcessRequest {
        Objects.requireNonNull(executable, "executable");
        arguments = List.copyOf(arguments);
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(totalTimeout, "totalTimeout");
        Objects.requireNonNull(idleTimeout, "idleTimeout");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(stdoutLines, "stdoutLines");
        Objects.requireNonNull(stderrLines, "stderrLines");
        Objects.requireNonNull(stdoutSink, "stdoutSink");
        Objects.requireNonNull(stdinFile, "stdinFile");
        Objects.requireNonNull(stdinWriter, "stdinWriter");
        if (stdinFile.isPresent() && stdinWriter.isPresent()) {
            throw new IllegalArgumentException("Standard input comes from a file or a writer, not both");
        }
    }

    /** Produces a program's standard input, for example raw video frames. */
    @FunctionalInterface
    public interface StdinWriter {

        /** Writes everything; an {@link IOException} usually means the program stopped reading. */
        void write(OutputStream input) throws IOException;
    }

    public static Builder builder(Path executable, List<String> arguments) {
        return new Builder(executable, arguments);
    }

    /** The executable followed by its arguments. */
    public List<String> command() {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        return command;
    }

    /**
     * The command as one readable line for logs and "Show details". Arguments with spaces are quoted
     * for readability only; this text is never executed.
     */
    public String commandLine() {
        return command().stream()
                .map(part -> part.isEmpty() || part.contains(" ") ? "'" + part + "'" : part)
                .collect(Collectors.joining(" "));
    }

    /** Builds a {@link ProcessRequest} with optional settings. */
    public static final class Builder {

        private final Path executable;
        private final List<String> arguments;
        private Path workingDirectory;
        private Duration totalTimeout;
        private Duration idleTimeout;
        private Charset charset = StandardCharsets.UTF_8;
        private Consumer<String> stdoutLines = line -> {};
        private Consumer<String> stderrLines = line -> {};
        private OutputStream stdoutSink;
        private Path stdinFile;
        private StdinWriter stdinWriter;

        private Builder(Path executable, List<String> arguments) {
            this.executable = executable;
            this.arguments = arguments;
        }

        public Builder workingDirectory(Path folder) {
            this.workingDirectory = folder;
            return this;
        }

        public Builder totalTimeout(Duration timeout) {
            this.totalTimeout = timeout;
            return this;
        }

        public Builder idleTimeout(Duration timeout) {
            this.idleTimeout = timeout;
            return this;
        }

        public Builder charset(Charset outputCharset) {
            this.charset = outputCharset;
            return this;
        }

        public Builder onStdoutLine(Consumer<String> consumer) {
            this.stdoutLines = consumer;
            return this;
        }

        public Builder onStderrLine(Consumer<String> consumer) {
            this.stderrLines = consumer;
            return this;
        }

        /**
         * Copies stdout unchanged into {@code sink}, for programs that write data rather than text.
         * The sink is called from a background thread and is not closed.
         */
        public Builder stdoutTo(OutputStream sink) {
            this.stdoutSink = sink;
            return this;
        }

        /** Gives the program {@code file} as its standard input, for tools that read data from stdin. */
        public Builder stdinFrom(Path file) {
            this.stdinFile = file;
            return this;
        }

        /**
         * Streams the program's standard input from {@code writer}, called on a background thread
         * while the program runs, for data too large for a file.
         */
        public Builder stdinWriter(StdinWriter writer) {
            this.stdinWriter = writer;
            return this;
        }

        public ProcessRequest build() {
            return new ProcessRequest(
                    executable,
                    arguments,
                    Optional.ofNullable(workingDirectory),
                    Optional.ofNullable(totalTimeout),
                    Optional.ofNullable(idleTimeout),
                    charset,
                    stdoutLines,
                    stderrLines,
                    Optional.ofNullable(stdoutSink),
                    Optional.ofNullable(stdinFile),
                    Optional.ofNullable(stdinWriter));
        }
    }
}
