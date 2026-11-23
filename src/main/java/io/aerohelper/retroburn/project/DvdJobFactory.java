// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import io.aerohelper.retroburn.common.ByteSizes;
import io.aerohelper.retroburn.common.UserMessage;
import io.aerohelper.retroburn.engine.BurnEvent;
import io.aerohelper.retroburn.engine.DiscImageBuilder;
import io.aerohelper.retroburn.engine.MediaFamily;
import io.aerohelper.retroburn.engine.MediaState;
import io.aerohelper.retroburn.pipeline.Pipeline;
import io.aerohelper.retroburn.pipeline.PipelineStep;
import io.aerohelper.retroburn.pipeline.StepContext;
import io.aerohelper.retroburn.tools.Feature;
import io.aerohelper.retroburn.tools.FeatureAvailability;
import io.aerohelper.retroburn.video.DvdBitrate;
import io.aerohelper.retroburn.video.DvdChapters;
import io.aerohelper.retroburn.video.DvdTools;
import io.aerohelper.retroburn.video.DvdXml;
import io.aerohelper.retroburn.video.MenuLayout;
import io.aerohelper.retroburn.video.MenuRenderer;
import io.aerohelper.retroburn.video.MenuTemplate;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Builds the video DVD pipeline: convert titles, make the menus, author VIDEO_TS, then save the
 * folder, build an ISO, or burn and verify. See {@code docs/design/LLD-video-dvd.md} section 7.
 */
public final class DvdJobFactory {

    static final String IMAGE_NAME = "dvd.iso";
    /** The work folder needs room for the titles and the image. */
    static final int WORK_SPACE_FACTOR = 2;

    private final BurnJobFactory dataJobs;
    private final DiscImageBuilder imageBuilder;
    private final DvdTools tools;
    private final MenuTexts texts;

    /**
     * The menu's fixed button texts, in the user's language.
     *
     * @param playAll "Play all"
     * @param previous "Previous"
     * @param next "Next"
     */
    public record MenuTexts(String playAll, String previous, String next) {}

    public DvdJobFactory(BurnJobFactory dataJobs, DiscImageBuilder imageBuilder, DvdTools tools, MenuTexts texts) {
        this.dataJobs = dataJobs;
        this.imageBuilder = imageBuilder;
        this.tools = tools;
        this.texts = texts;
    }

    /** Author the DVD and burn it to the disc in the plan's drive. */
    public Pipeline burn(BurnPlan plan, DvdProject project, DvdBitrate bitrate, VolumeLabel label) {
        if (plan.kind() != JobKind.VIDEO_DVD) {
            throw new IllegalArgumentException("Expected a VIDEO_DVD plan, got " + plan.kind());
        }
        BurnJobFactory.drive(plan);
        BurnSteps.ImageFile image = context -> context.workFolder().resolve(IMAGE_NAME);
        List<PipelineStep> steps = new ArrayList<>(author(project, bitrate));
        steps.add(new BuildDvdImage(imageBuilder, label, image, weightOf(project)));
        steps.addAll(dataJobs.writeSteps(plan, image));
        return new Pipeline(steps);
    }

    /** Author the DVD and save it as an ISO file. */
    public Pipeline createIso(DvdProject project, DvdBitrate bitrate, VolumeLabel label, Path target) {
        List<PipelineStep> steps = new ArrayList<>(author(project, bitrate));
        steps.add(new BuildDvdImage(imageBuilder, label, context -> target, weightOf(project)));
        return new Pipeline(steps);
    }

    /** Author the DVD and copy its VIDEO_TS and AUDIO_TS folders into {@code target}. */
    public Pipeline saveFolder(DvdProject project, DvdBitrate bitrate, Path target) {
        List<PipelineStep> steps = new ArrayList<>(author(project, bitrate));
        steps.add(new CopyFolders(target, Math.max(1, weightOf(project) / 4)));
        return new Pipeline(steps);
    }

    /**
     * The rules a video DVD adds to the burn checks: a DVD, room in the work folder for titles and
     * image, and the DVD tools.
     */
    public static BurnPlan check(BurnPreCheck.Request request, DvdProject project) {
        BurnPlan base = BurnPreCheck.check(request);
        List<UserMessage> blockers = new ArrayList<>(base.blockers().stream()
                .filter(message -> !message.key().equals("burn.blocker.workFolderFull"))
                .toList());
        if (project.titles().isEmpty()) {
            blockers.add(UserMessage.of("burn.blocker.noTitles"));
        }
        if (project.titles().size() > DvdProject.MAX_TITLES) {
            blockers.add(UserMessage.of(
                    "burn.blocker.tooManyTitles", project.titles().size()));
        }
        request.media()
                .filter(media -> media.state() != MediaState.NO_MEDIA)
                .filter(media -> !media.kind().family().equals(Optional.of(MediaFamily.DVD)))
                .ifPresent(media -> blockers.add(UserMessage.of("burn.blocker.videoNeedsDvd")));
        long needed = request.bytesToWrite() * WORK_SPACE_FACTOR;
        request.workFolder()
                .filter(space -> space.freeBytes() < needed)
                .ifPresent(space -> blockers.add(UserMessage.of(
                        "burn.blocker.videoWorkFolderFull",
                        space.folder().toString(),
                        ByteSizes.format(needed),
                        ByteSizes.format(space.freeBytes()))));
        for (Feature feature : List.of(Feature.VIDEO_DVD, Feature.VIDEO_DVD_IMAGE)) {
            FeatureAvailability availability = request.features().get(feature);
            if (availability != null && !availability.isAvailable()) {
                availability.reasons().stream()
                        .filter(reason -> !blockers.contains(reason))
                        .forEach(blockers::add);
            }
        }
        return new BurnPlan(
                base.kind(),
                base.drive(),
                base.media(),
                base.bytesToWrite(),
                base.eraseFirst(),
                base.verify(),
                blockers,
                base.warnings(),
                base.showUsbTip());
    }

    private List<PipelineStep> author(DvdProject project, DvdBitrate bitrate) {
        double seconds = Math.max(1, project.totalDuration().toSeconds());
        return List.of(
                new ConvertTitles(tools, project, bitrate, seconds),
                new MakeMenus(tools, project, texts, Math.max(1, seconds * 0.05)),
                new Author(tools, project, texts, Math.max(1, seconds * 0.05)));
    }

    private static double weightOf(DvdProject project) {
        return Math.max(1, project.totalDuration().toSeconds() * 0.1);
    }

    static Path titleFile(StepContext context, int number) {
        return context.workFolder().resolve("titles").resolve("%02d.mpg".formatted(number));
    }

    static Path thumbnailFile(StepContext context, int number) {
        return context.workFolder().resolve("thumbs").resolve("%02d.png".formatted(number));
    }

    static Path menuFolder(StepContext context) {
        return context.workFolder().resolve("menus");
    }

    static Path menuFile(StepContext context, int page) {
        return menuFolder(context).resolve("p%d-buttons.mpg".formatted(page));
    }

    static Path dvdFolder(StepContext context) {
        return context.workFolder().resolve("dvd");
    }

    record ConvertTitles(DvdTools tools, DvdProject project, DvdBitrate bitrate, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.convertTitles");
        }

        @Override
        public void run(StepContext context) {
            createDirectories(context.workFolder().resolve("titles"));
            double total = Math.max(1, project.totalDuration().toMillis());
            double before = 0;
            for (int index = 0; index < project.titles().size(); index++) {
                DvdProject.Title title = project.titles().get(index);
                double offset = before;
                double share = title.video().duration().toMillis();
                tools.title(
                        title.source(),
                        titleFile(context, index + 1),
                        project.standard(),
                        project.aspect(),
                        bitrate.videoKbps(),
                        title.video().duration(),
                        fraction -> context.reportProgress((offset + fraction * share) / total),
                        context.cancelToken());
                before += share;
            }
        }
    }

    record MakeMenus(DvdTools tools, DvdProject project, MenuTexts texts, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.makeMenus");
        }

        @Override
        public void run(StepContext context) {
            createDirectories(context.workFolder().resolve("thumbs"));
            Path folder = menuFolder(context);
            createDirectories(folder);
            for (int index = 0; index < project.titles().size(); index++) {
                DvdProject.Title title = project.titles().get(index);
                tools.thumbnail(
                        title.source(),
                        title.video().duration(),
                        thumbnailFile(context, index + 1),
                        context.cancelToken());
            }
            List<MenuLayout.Page> pages = project.pages(texts.playAll(), texts.previous(), texts.next());
            MenuTemplate template = project.template();
            for (MenuLayout.Page page : pages) {
                Map<String, BufferedImage> thumbnails = new HashMap<>();
                for (MenuLayout.Button button : page.buttons()) {
                    if (button.title().isPresent()) {
                        thumbnails.put(
                                button.name(),
                                readImage(thumbnailFile(context, button.title().getAsInt())));
                    }
                }
                String stem = "p" + page.number();
                try {
                    MenuRenderer.PageFiles files = MenuRenderer.render(
                                    page, project.discTitle(), template, project.standard(), thumbnails)
                            .write(folder, stem);
                    Path plain = folder.resolve(stem + ".mpg");
                    tools.menuVideo(files.background(), plain, project.standard(), context.cancelToken());
                    Path xml = folder.resolve(stem + ".xml");
                    Files.writeString(
                            xml,
                            DvdXml.spumux(page, project.standard(), files.highlight(), files.select()),
                            StandardCharsets.UTF_8);
                    tools.spumux(
                            xml,
                            plain,
                            menuFile(context, page.number()),
                            line -> context.burnListener().onEvent(new BurnEvent.LogLine(line)),
                            context.cancelToken());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                context.reportProgress((double) page.number() / pages.size());
            }
        }

        private static BufferedImage readImage(Path file) {
            try {
                return ImageIO.read(file.toFile());
            } catch (IOException e) {
                return null;
            }
        }
    }

    record Author(DvdTools tools, DvdProject project, MenuTexts texts, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.author");
        }

        @Override
        public void run(StepContext context) {
            List<MenuLayout.Page> pages = project.pages(texts.playAll(), texts.previous(), texts.next());
            List<DvdXml.Title> titles = new ArrayList<>();
            for (int index = 0; index < project.titles().size(); index++) {
                DvdProject.Title title = project.titles().get(index);
                titles.add(new DvdXml.Title(
                        titleFile(context, index + 1),
                        DvdChapters.attribute(DvdChapters.every(
                                project.chapterMinutes(), title.video().duration())),
                        index / MenuLayout.TITLES_PER_PAGE + 1));
            }
            Path output = dvdFolder(context);
            try {
                Files.createDirectories(output.resolve("AUDIO_TS"));
                Path xml = context.workFolder().resolve("dvd.xml");
                Files.writeString(
                        xml,
                        DvdXml.dvdauthor(new DvdXml.Authoring(
                                output,
                                project.standard(),
                                project.aspect(),
                                pages.stream()
                                        .map(page -> menuFile(context, page.number()))
                                        .toList(),
                                pages,
                                titles,
                                project.playNext())),
                        StandardCharsets.UTF_8);
                tools.dvdauthor(
                        xml,
                        line -> context.burnListener().onEvent(new BurnEvent.LogLine(line)),
                        context.cancelToken());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    record BuildDvdImage(DiscImageBuilder builder, VolumeLabel label, BurnSteps.ImageFile image, double weight)
            implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.buildImage");
        }

        @Override
        public void run(StepContext context) {
            builder.buildDvdVideoImage(
                    dvdFolder(context),
                    label.text().toUpperCase(Locale.ROOT),
                    image.apply(context),
                    context.burnListener(),
                    context.cancelToken());
        }
    }

    record CopyFolders(Path target, double weight) implements PipelineStep {

        @Override
        public UserMessage name() {
            return UserMessage.of("step.saveFolder");
        }

        @Override
        public void run(StepContext context) {
            Path source = dvdFolder(context);
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.toList()) {
                    context.cancelToken().throwIfCancelled();
                    Path copy = target.resolve(source.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.copy(file, copy, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static void createDirectories(Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
