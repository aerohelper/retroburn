// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.app;

import io.aerohelper.retroburn.common.AppInfo;
import io.aerohelper.retroburn.common.Messages;
import io.aerohelper.retroburn.common.OperatingSystem;
import io.aerohelper.retroburn.credits.ComponentRegistry;
import io.aerohelper.retroburn.design.CoverContent;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.pipeline.WorkFolders;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.PlatformDetector;
import io.aerohelper.retroburn.platform.PlatformServices;
import io.aerohelper.retroburn.platform.linux.LinuxAppPaths;
import io.aerohelper.retroburn.platform.linux.LinuxPlatform;
import io.aerohelper.retroburn.platform.simulated.SimulatedPlatform;
import io.aerohelper.retroburn.platform.windows.WindowsAppPaths;
import io.aerohelper.retroburn.platform.windows.WindowsPlatform;
import io.aerohelper.retroburn.settings.Settings;
import io.aerohelper.retroburn.settings.SettingsStore;
import io.aerohelper.retroburn.theme.Appearance;
import io.aerohelper.retroburn.theme.ThemeRegistry;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import io.aerohelper.retroburn.tools.FeatureGate;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.tools.ToolProbe;
import io.aerohelper.retroburn.ui.UiServices;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The composition root: creates the application's long-lived objects and hands them out.
 *
 * <p>Objects are wired by hand through constructors, with no dependency-injection framework (see
 * {@code docs/adr/0008}). This is the only class that chooses a {@link PlatformServices}
 * implementation.
 */
public final class AppContext implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    private final AtomicReference<CoverContent> coverContent = new AtomicReference<>(CoverContent.EMPTY);
    private final PlatformServices platform;
    private final SettingsStore settingsStore;
    private final Appearance appearance;
    private final AppInfo appInfo;
    private final Messages messages;
    private final ComponentRegistry componentRegistry;
    private final ToolProbe toolProbe;
    private final WorkFolders workFolders;
    private final PipelineRunner pipelineRunner;
    private final Path workRoot;
    private final AtomicReference<Map<Feature, FeatureAvailability>> features = new AtomicReference<>(Map.of());
    /** Short background work for the screens: reading discs, scanning sizes, preparing jobs. */
    private final ProcessRunner processRunner;

    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();
    /** One job at a time, on its own thread (HLD section 7: "pipeline-runner"). */
    private final ExecutorService jobs = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pipeline-runner");
        thread.setDaemon(true);
        return thread;
    });

    public AppContext(
            PlatformServices platform,
            SettingsStore settingsStore,
            ProcessRunner processRunner,
            AppInfo appInfo,
            Messages messages,
            ComponentRegistry componentRegistry) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.appInfo = Objects.requireNonNull(appInfo, "appInfo");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.componentRegistry = Objects.requireNonNull(componentRegistry, "componentRegistry");
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner");
        this.toolProbe = new ToolProbe(platform.toolLocator(), processRunner);
        Path workRoot = settingsStore
                .current()
                .workDirectory()
                .orElse(platform.appPaths().work());
        this.workRoot = workRoot;
        this.workFolders = new WorkFolders(workRoot);
        this.pipelineRunner = new PipelineRunner(workFolders, platform.sleepInhibitor());
        Settings.Appearance look = settingsStore.current().appearance();
        this.appearance = new Appearance(ThemeRegistry.builtIn(), look.theme(), look.soundChoice(), look.animateDisc());
    }

    /**
     * Builds the context for the machine the app is running on: the Linux or Windows platform, or the
     * simulated platform on any other OS.
     */
    public static AppContext createDefault() {
        OperatingSystem operatingSystem = PlatformDetector.currentOperatingSystem();
        Path home = Path.of(System.getProperty("user.home"));
        ProcessRunner processRunner = new ProcessRunner();

        AppPaths paths = switch (operatingSystem) {
            case LINUX -> LinuxAppPaths.fromEnvironment(System.getenv(), home);
            case WINDOWS -> WindowsAppPaths.fromEnvironment(System.getenv(), home);
            default -> simulatedPaths(home);
        };
        paths.ensureExists();
        LoggingSetup.writeLogsTo(paths.logs());
        SettingsStore settingsStore = new SettingsStore(paths.settings());

        PlatformServices platform = switch (operatingSystem) {
            case LINUX ->
                new LinuxPlatform(
                        paths,
                        processRunner,
                        System.getenv("PATH"),
                        () -> settingsStore.current().toolPaths());
            case WINDOWS ->
                new WindowsPlatform(
                        paths,
                        processRunner,
                        System.getenv(),
                        () -> settingsStore.current().toolPaths());
            default -> new SimulatedPlatform(operatingSystem, paths);
        };

        AppContext context = new AppContext(
                platform,
                settingsStore,
                processRunner,
                AppInfo.loadBundled(),
                Messages.load(Locale.getDefault()),
                ComponentRegistry.loadBundled());
        // No job can be running yet, so anything left in the work folder is from an earlier run.
        context.workFolders.deleteLeftovers();
        return context;
    }

    private static AppPaths simulatedPaths(Path home) {
        Path root = home.resolve(".retroburn-simulated");
        return new AppPaths(
                root.resolve("settings"),
                root.resolve("data"),
                root.resolve("logs"),
                root.resolve("work"),
                root.resolve("reels"));
    }

    public PlatformServices platform() {
        return platform;
    }

    public SettingsStore settingsStore() {
        return settingsStore;
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

    public ToolProbe toolProbe() {
        return toolProbe;
    }

    public PipelineRunner pipelineRunner() {
        return pipelineRunner;
    }

    /**
     * Finds the external tools and works out which features are available, in the background so
     * the window opens at once. Until it finishes, no feature is blocked; a job whose tool turns out
     * to be missing fails with a "not installed" message instead.
     */
    public void checkToolsInBackground() {
        background.execute(() -> {
            try {
                features.set(FeatureGate.evaluate(
                        toolProbe.probeAll(List.of(ExternalTool.values())), platform.featureRequirements()));
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Could not check the external tools", e);
            }
        });
    }

    /** What the screens need, as one object. */
    public UiServices uiServices() {
        return new UiServices(
                messages,
                platform,
                settingsStore,
                pipelineRunner,
                processRunner,
                features::get,
                () -> workRoot,
                background,
                jobs,
                coverContent,
                appearance);
    }

    /** Stops background threads; a running job's processes are stopped with the JVM. */
    @Override
    public void close() {
        appearance.close();
        background.shutdownNow();
        jobs.shutdownNow();
    }
}
