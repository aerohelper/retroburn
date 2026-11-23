// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.reel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Saves burn sessions as JSON in {@code <data>/sessions/}, keeping the newest {@value #KEEP}. Local
 * only: nothing is sent anywhere.
 */
public final class SessionStore {

    public static final int KEEP = 50;

    private static final DateTimeFormatter NAME_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private final Path folder;

    public SessionStore(Path dataFolder) {
        this.folder = dataFolder.resolve("sessions");
    }

    public Path folder() {
        return folder;
    }

    /** Writes the session (temporary file first) and removes the oldest beyond {@value #KEEP}. */
    public Path save(BurnSession session) throws IOException {
        Files.createDirectories(folder);
        Path target = folder.resolve(NAME_TIME.format(session.started()) + "-" + session.id() + ".json");
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        Files.write(temporary, MAPPER.writeValueAsBytes(session));
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        prune();
        return target;
    }

    public BurnSession read(Path file) throws IOException {
        return MAPPER.readValue(Files.readAllBytes(file), BurnSession.class);
    }

    /** Saved sessions, newest first. */
    public List<Path> list() throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(
                                    (Path file) -> file.getFileName().toString())
                            .reversed())
                    .toList();
        }
    }

    private void prune() throws IOException {
        List<Path> files = list();
        for (Path old : files.subList(Math.min(KEEP, files.size()), files.size())) {
            Files.deleteIfExists(old);
        }
    }
}
