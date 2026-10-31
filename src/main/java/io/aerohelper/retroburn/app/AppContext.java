// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import io.aerohelper.retroburn.common.AppInfo;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.credits.ComponentRegistry;
import io.aerohelper.retroburn.platform.PlatformDetector;
import io.aerohelper.retroburn.platform.PlatformServices;
import io.aerohelper.retroburn.platform.simulated.SimulatedPlatform;
import java.util.Locale;
import java.util.Objects;

/**
 * The composition root: creates the application's long-lived objects and hands them out.
 *
 * <p>Objects are wired by hand through constructors, with no dependency-injection framework (see
 * {@code docs/adr/0008}). This is the only class that chooses a {@link PlatformServices}
 * implementation.
 */
public final class AppContext {

    private final PlatformServices platform;
    private final AppInfo appInfo;
    private final Messages messages;
    private final ComponentRegistry componentRegistry;

    public AppContext(
            PlatformServices platform, AppInfo appInfo, Messages messages, ComponentRegistry componentRegistry) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.appInfo = Objects.requireNonNull(appInfo, "appInfo");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry");
    }

    /**
     * Builds the context for the machine the app is running on.
     *
     * <p>The Linux and Windows platforms do not exist yet, so the simulated platform is used on
     * every OS; once they exist, this method picks the matching one.
     */
    public static AppContext createDefault() {
        OperatingSystem operatingSystem = PlatformDetector.currentOperatingSystem();
        PlatformServices platform = new SimulatedPlatform(operatingSystem);
        return new AppContext(
                platform, AppInfo.loadBundled(), Messages.load(Locale.getDefault()), ComponentRegistry.loadBundled());
    }

    public PlatformServices platform() {
        return platform;
    }

    public AppInfo appInfo() {
        return appInfo;
    }

    public Messages messages() {
        return messages;
    }

    public ComponentRegistry componentRegistry() {
        return componentRegistry;
    }
}
