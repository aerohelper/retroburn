// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import io.aerohelper.retroburn.pipeline.PipelineListener;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Records a burn while it runs: overall progress (at most one sample a second, plus the last one),
 * the latest speed and when each step started. Thread-safe: events come from background threads.
 */
public final class SessionRecorder implements PipelineListener {

    static final long SAMPLE_INTERVAL_MILLIS = 1000;

    private final Clock clock;
    private final Instant started;
    private final String kind;
    private final String label;
    private final String theme;
    private final List<String> items;
    private final long bytes;

    private final List<BurnSession.Sample> samples = new ArrayList<>();
    private final List<BurnSession.Marker> markers = new ArrayList<>();
    private double fraction;
    private String speed = "";
    private BurnSession.Sample pending;

    public SessionRecorder(Clock clock, String kind, String label, String theme, List<String> items, long bytes) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.started = clock.instant();
        this.kind = kind;
        this.label = label;
        this.theme = theme;
        this.items = List.copyOf(items);
        this.bytes = bytes;
    }

    @Override
    public synchronized void onEvent(PipelineEvent event) {
        long at = clock.millis() - started.toEpochMilli();
        switch (event) {
            case PipelineEvent.StepStarted step ->
                markers.add(new BurnSession.Marker(at, step.name().key()));
            case PipelineEvent.OverallProgress progress -> {
                fraction = progress.fraction();
                sample(at);
            }
            case PipelineEvent.StepOutput output -> {
                if (output.event() instanceof BurnEvent.Progress progress
                        && progress.speed().isPresent()) {
                    speed = progress.speed().get();
                }
            }
            default -> {
                // Other events say nothing the reel shows.
            }
        }
    }

    /** The finished session. */
    public synchronized BurnSession finish(String outcome) {
        long at = clock.millis() - started.toEpochMilli();
        List<BurnSession.Sample> all = new ArrayList<>(samples);
        if (pending != null) {
            all.add(pending);
        }
        if (outcome.startsWith("SUCCEEDED")) {
            all.add(new BurnSession.Sample(at, 1, speed));
        }
        return new BurnSession(
                BurnSession.SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                started,
                started.plusMillis(at),
                kind,
                label,
                theme,
                items,
                bytes,
                outcome,
                all,
                markers);
    }

    /** Keeps one sample per second; the newest in-between value waits as {@code pending}. */
    private void sample(long at) {
        BurnSession.Sample sample = new BurnSession.Sample(at, fraction, speed);
        long last = samples.isEmpty() ? Long.MIN_VALUE : samples.getLast().atMillis();
        if (samples.isEmpty() || at - last >= SAMPLE_INTERVAL_MILLIS) {
            samples.add(sample);
            pending = null;
        } else {
            pending = sample;
        }
    }
}
