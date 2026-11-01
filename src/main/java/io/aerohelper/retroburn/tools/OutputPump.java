// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Drains a process's output streams line by line, keeps the most recent lines for error reports,
 * and remembers when output last arrived (for idle timeouts).
 *
 * <p>Lines end at {@code \n}, {@code \r} or {@code \r\n}: some tools redraw a progress line with a
 * bare carriage return, and each redraw should count as a new line. Thread-safe: one pump serves
 * both streams of a process.
 */
final class OutputPump {

    private static final Logger LOG = Logger.getLogger(OutputPump.class.getName());

    private final int tailSize;
    private final Deque<String> tail = new ArrayDeque<>();
    private volatile Instant lastOutput = Instant.now();
    private RuntimeException consumerFailure;

    OutputPump(int tailSize) {
        this.tailSize = tailSize;
    }

    /** Starts a virtual thread that reads {@code stream} until it ends. */
    Thread drain(InputStream stream, Charset charset, Consumer<String> consumer, String streamName) {
        return Thread.ofVirtual()
                .name("process-" + streamName)
                .start(() -> readLines(new InputStreamReader(stream, charset), consumer, streamName));
    }

    /**
     * Starts a virtual thread that copies {@code stream} into {@code sink} until it ends. After the
     * sink fails, the rest is read and discarded so the process never blocks on a full pipe.
     */
    Thread copy(InputStream stream, OutputStream sink, String streamName) {
        return Thread.ofVirtual().name("process-" + streamName).start(() -> copyBytes(stream, sink, streamName));
    }

    Instant lastOutput() {
        return lastOutput;
    }

    synchronized List<String> tail() {
        return List.copyOf(tail);
    }

    /** The first exception a line consumer or byte sink threw, if any; output kept draining regardless. */
    synchronized RuntimeException consumerFailure() {
        return consumerFailure;
    }

    private void readLines(Reader reader, Consumer<String> consumer, String streamName) {
        StringBuilder line = new StringBuilder();
        boolean previousWasCarriageReturn = false;
        try (reader) {
            int character;
            while ((character = reader.read()) != -1) {
                lastOutput = Instant.now();
                if (character == '\n' && previousWasCarriageReturn) {
                    previousWasCarriageReturn = false;
                    continue;
                }
                previousWasCarriageReturn = character == '\r';
                if (character == '\n' || character == '\r') {
                    accept(line.toString(), consumer, streamName);
                    line.setLength(0);
                } else {
                    line.append((char) character);
                }
            }
            if (!line.isEmpty()) {
                accept(line.toString(), consumer, streamName);
            }
        } catch (IOException e) {
            // The stream closes when the process is destroyed; nothing more to read.
            LOG.log(Level.FINE, "Stopped reading " + streamName, e);
        }
    }

    private void copyBytes(InputStream stream, OutputStream sink, String streamName) {
        byte[] buffer = new byte[64 * 1024];
        boolean sinkWorks = true;
        try (stream) {
            int read;
            while ((read = stream.read(buffer)) != -1) {
                lastOutput = Instant.now();
                if (sinkWorks) {
                    try {
                        sink.write(buffer, 0, read);
                    } catch (IOException e) {
                        sinkWorks = false;
                        recordFailure(new UncheckedIOException("Could not take the output of " + streamName, e));
                    } catch (RuntimeException e) {
                        sinkWorks = false;
                        recordFailure(e);
                    }
                }
            }
        } catch (IOException e) {
            LOG.log(Level.FINE, "Stopped reading " + streamName, e);
        }
    }

    private synchronized void recordFailure(RuntimeException e) {
        if (consumerFailure == null) {
            consumerFailure = e;
        }
    }

    private void accept(String line, Consumer<String> consumer, String streamName) {
        LOG.fine(() -> streamName + ": " + line);
        synchronized (this) {
            tail.addLast(line);
            if (tail.size() > tailSize) {
                tail.removeFirst();
            }
        }
        try {
            consumer.accept(line);
        } catch (RuntimeException e) {
            recordFailure(e);
        }
    }
}
