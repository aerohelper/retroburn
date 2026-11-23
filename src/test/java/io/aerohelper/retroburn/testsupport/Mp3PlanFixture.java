// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.testsupport;

import io.aerohelper.retroburn.audio.Mp3Encoding;
import io.aerohelper.retroburn.audio.ProbedAudio;
import io.aerohelper.retroburn.engine.DiscEntry;
import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.project.Mp3Compilation;
import io.aerohelper.retroburn.project.Mp3DiscPlan;
import io.aerohelper.retroburn.project.Mp3Layout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;

/**
 * Small ".mp3" files with awkward names and tags, planned in car stereo mode, for the image tests
 * on each platform. Their bytes are random: an image only copies them, never decodes them.
 *
 * @param tree what the image builder gets
 * @param expected disc path to the source file whose bytes must be there unchanged
 */
public record Mp3PlanFixture(DiscTree tree, Map<String, Path> expected) {

    public static Mp3PlanFixture create(Path folder) throws IOException {
        Path albumFolder = Files.createDirectories(folder.resolve("album"));
        Random random = new Random(7);
        Path first = write(albumFolder.resolve("b — track.mp3"), random, 300_000);
        Path second = write(albumFolder.resolve("a.mp3"), random, 200_000);
        Path loose = write(folder.resolve("Straße & ünë.mp3"), random, 100_000);
        Mp3Compilation compilation = Mp3Compilation.empty()
                .add(List.of(
                        item(first, "Zweite: Straße", "2", List.of("Ærø: Live?")),
                        item(second, "Café “Intro”", "1", List.of("Ærø: Live?")),
                        item(loose, "", "", List.of())));
        Mp3DiscPlan plan = Mp3DiscPlan.of(
                compilation,
                new Mp3DiscPlan.Options(Mp3Layout.KEEP_FOLDERS, true, false, Mp3Encoding.Bitrate.CBR_192, false));
        Map<String, Path> expected = new TreeMap<>();
        plan.entries()
                .forEach(entry -> expected.put(entry.discPath(), entry.item().source()));
        return new Mp3PlanFixture(
                DiscTree.of(plan.entries().stream()
                        .map(entry ->
                                new DiscEntry(entry.discPath(), entry.item().source()))
                        .toList()),
                expected);
    }

    /**
     * The disc paths the plan must produce, worked out by hand from the naming rules: "Ærø: Live?"
     * spells out Æ and ø and loses ':' and '?'; titles sort by track number; the loose file has no
     * title, so its name comes from the file. The folder name is only in the plan: Windows file names
     * cannot hold ':' or '?'.
     */
    public static List<String> expectedPaths() {
        return List.of(
                "/01 - Strasse & une.mp3", "/AEro Live/01 - Cafe Intro.mp3", "/AEro Live/02 - Zweite Strasse.mp3");
    }

    private static Path write(Path file, Random random, int size) throws IOException {
        byte[] bytes = new byte[size];
        random.nextBytes(bytes);
        return Files.write(file, bytes);
    }

    private static Mp3Compilation.Item item(Path file, String title, String track, List<String> folders) {
        return new Mp3Compilation.Item(
                file,
                new ProbedAudio(
                        Duration.ofMinutes(3),
                        "mp3",
                        "[0][0][0][0]",
                        44_100,
                        2,
                        new ProbedAudio.AudioTags(
                                Optional.of(title).filter(text -> !text.isEmpty()),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                Optional.of(track),
                                Optional.empty())),
                folders);
    }
}
