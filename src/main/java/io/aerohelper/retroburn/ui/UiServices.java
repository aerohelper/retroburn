// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.ui;

import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.design.CoverContent;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.platform.PlatformServices;
import io.aerohelper.retroburn.settings.SettingsStore;
import io.aerohelper.retroburn.theme.Appearance;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * What the screens need from the rest of the app, wired by {@code AppContext}.
 *
 * @param processes runs external tools such as FFmpeg for the screens' own checks
 * @param features feature availability; empty until the background tool check has finished
 * @param workRoot where job work folders are created
 * @param background short background work: reading media, scanning sizes, preparing a burn
 * @param jobs the single thread that runs jobs, so only one burn runs at a time
 * @param coverContent the text of the last project shown, for the cover designer's auto-fill
 * @param appearance the theme, sounds and disc animation
 */
public record UiServices(
        Messages messages,
        PlatformServices platform,
        SettingsStore settings,
        PipelineRunner pipelineRunner,
        ProcessRunner processes,
        Supplier<Map<Feature, FeatureAvailability>> features,
        Supplier<Path> workRoot,
        ExecutorService background,
        ExecutorService jobs,
        AtomicReference<CoverContent> coverContent,
        Appearance appearance) {

    public UiServices {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(pipelineRunner, "pipelineRunner");
        Objects.requireNonNull(processes, "processes");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(workRoot, "workRoot");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(jobs, "jobs");
        Objects.requireNonNull(coverContent, "coverContent");
        Objects.requireNonNull(appearance, "appearance");
    }
}
