// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.aerohelper.retroburn.common.CancelToken;
import io.aerohelper.retroburn.pipeline.PipelineEvent;
import io.aerohelper.retroburn.pipeline.PipelineResult;
import io.aerohelper.retroburn.pipeline.PipelineRunner;
import io.aerohelper.retroburn.pipeline.WorkFolders;
import io.aerohelper.retroburn.platform.AppPaths;
import io.aerohelper.retroburn.platform.linux.LinuxPlatform;
import io.aerohelper.retroburn.testsupport.IsoImage;
import io.aerohelper.retroburn.tools.ExternalTool;
import io.aerohelper.retroburn.tools.ProcessRequest;
import io.aerohelper.retroburn.tools.ProcessRunner;
import io.aerohelper.retroburn.video.DvdBitrate;
import io.aerohelper.retroburn.video.DvdTools;
import io.aerohelper.retroburn.video.MenuTemplate;
import io.aerohelper.retroburn.video.TvStandard;
import io.aerohelper.retroburn.video.VideoTsOrder;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Two 20-second synthetic clips through the real FFmpeg, spumux, dvdauthor and genisoimage: a
 * VIDEO_TS folder with a menu and two titles, and a DVD-Video image with UDF. Runs on Linux where
 * the tools are installed (the CI runner installs them).
 */
@EnabledOnOs(OS.LINUX)
class DvdJobIntegrationTest {

    @TempDir
    Path folder;

    private final ProcessRunner processes = new ProcessRunner();

    @Test
    void twoClipsBecomeAVideoTsFolderAndAnImageWithUdf() throws IOException {
        LinuxPlatform platform = new LinuxPlatform(
                new AppPaths(
                        folder.resolve("settings"),
                        folder.resolve("data"),
                        folder.resolve("logs"),
                        folder.resolve("work"),
                        folder.resolve("reels")),
                processes,
                System.getenv("PATH"),
                Map::of);
        for (ExternalTool tool : List.of(
                ExternalTool.FFMPEG,
                ExternalTool.FFPROBE,
                ExternalTool.DVDAUTHOR,
                ExternalTool.SPUMUX,
                ExternalTool.GENISOIMAGE)) {
            assumeTrue(platform.toolLocator().locate(tool).isPresent(), tool + " is not installed");
        }
        Path ffmpeg = platform.toolLocator().locate(ExternalTool.FFMPEG).orElseThrow();
        DvdTools tools = new DvdTools(processes, platform.toolLocator());
        List<DvdProject.Title> titles = new ArrayList<>();
        for (String name : List.of("first", "second")) {
            Path clip = folder.resolve(name + ".mp4");
            assertTrue(processes
                    .run(
                            ProcessRequest.builder(
                                            ffmpeg,
                                            List.of(
                                                    "-nostdin",
                                                    "-hide_banner",
                                                    "-loglevel",
                                                    "error",
                                                    "-y",
                                                    "-f",
                                                    "lavfi",
                                                    "-i",
                                                    "testsrc=size=1280x720:rate=25:duration=20",
                                                    "-f",
                                                    "lavfi",
                                                    "-i",
                                                    "sine=frequency=440:duration=20",
                                                    "-c:v",
                                                    "mpeg4",
                                                    "-c:a",
                                                    "aac",
                                                    "-shortest",
                                                    clip.toString()))
                                    .totalTimeout(Duration.ofMinutes(2))
                                    .build(),
                            new CancelToken())
                    .succeeded());
            titles.add(DvdProject.Title.of(clip, tools.probe(clip, new CancelToken())));
        }
        DvdProject project = new DvdProject(
                        "Holiday 2026", List.of(), TvStandard.PAL, "4:3", 5, MenuTemplate.CLASSIC, false)
                .add(titles);
        assertEquals("16:9", project.aspect(), "detected from the 1280x720 clip");

        Path saved = folder.resolve("saved");
        DvdJobFactory factory = new DvdJobFactory(
                new BurnJobFactory(platform.burnEngine(), platform.discImageBuilder()),
                platform.discImageBuilder(),
                tools,
                new DvdJobFactory.MenuTexts("Play all", "Previous", "Next"));
        List<String> log = Collections.synchronizedList(new ArrayList<>());
        PipelineResult result = new PipelineRunner(new WorkFolders(folder.resolve("work")), platform.sleepInhibitor())
                .run(
                        factory.saveFolder(project, project.bitrate(DvdBitrate.SINGLE_LAYER_BYTES), saved),
                        new PipelineRunner.Options(false, false),
                        event -> {
                            if (event instanceof PipelineEvent.StepOutput output) {
                                log.add(output.event().toString());
                            }
                        },
                        new CancelToken());
        log.forEach(line -> System.out.println("[dvd] " + line));
        assertInstanceOf(PipelineResult.Succeeded.class, result, result.toString());

        Path videoTs = saved.resolve("VIDEO_TS");
        assertTrue(Files.isRegularFile(videoTs.resolve("VIDEO_TS.IFO")));
        assertTrue(Files.isDirectory(saved.resolve("AUDIO_TS")));
        List<int[]> table = titleTable(videoTs.resolve("VIDEO_TS.IFO"));
        assertEquals(2, table.size(), "titles in VIDEO_TS.IFO");
        table.forEach(entry -> assertEquals(1, entry[0], "one chapter in a 20-second title"));

        Path image = folder.resolve("dvd.iso");
        platform.discImageBuilder().buildDvdVideoImage(saved, "HOLIDAY_2026", image, event -> {}, new CancelToken());
        try (IsoImage iso = new IsoImage(image)) {
            List<String> descriptors = iso.descriptorIdentifiers();
            System.out.println("[dvd] descriptors " + descriptors);
            assertTrue(descriptors.contains("NSR02"), "UDF 1.02 descriptor missing: " + descriptors);
            List<IsoImage.Entry> files = iso.list("/VIDEO_TS").stream()
                    .sorted(Comparator.comparingLong(IsoImage.Entry::lba))
                    .toList();
            files.forEach(entry -> System.out.println("[dvd] " + entry));
            List<String> names = files.stream().map(IsoImage.Entry::name).toList();
            assertEquals(VideoTsOrder.sort(names), names);
        }
        try (Stream<Path> menus = Files.list(folder.resolve("work"))) {
            System.out.println("[dvd] work folders left: " + menus.toList());
        }
    }

    /**
     * Title table of VIDEO_TS.IFO: the TT_SRPT sector number is at byte 0xC4 (big-endian); TT_SRPT
     * starts with the number of titles (2 bytes), then 8 header bytes in all, then 12 bytes per title
     * with the number of chapters at offset 2. Layout from the public DVD-Video IFO descriptions.
     */
    private static List<int[]> titleTable(Path ifo) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(ifo.toFile(), "r")) {
            byte[] id = new byte[12];
            file.readFully(id);
            assertEquals("DVDVIDEO-VMG", new String(id, StandardCharsets.US_ASCII));
            file.seek(0xC4);
            long sector = file.readInt() & 0xFFFFFFFFL;
            file.seek(sector * 2048);
            int titles = file.readUnsignedShort();
            List<int[]> entries = new ArrayList<>();
            for (int index = 0; index < titles; index++) {
                file.seek(sector * 2048 + 8 + index * 12L + 2);
                entries.add(new int[] {file.readUnsignedShort()});
            }
            return entries;
        }
    }
}
