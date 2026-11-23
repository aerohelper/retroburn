// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import io.aerohelper.retroburn.engine.DiscTree;
import io.aerohelper.retroburn.engine.DriveId;
import java.nio.file.Path;
import java.util.Objects;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * A request for the Windows helper: one JSON object, sent as the first line of its standard input.
 * Every value travels as JSON data, never as script text. See {@code
 * docs/design/LLD-windows-platform.md} section 4.
 *
 * @param operation the operation name, for logs
 * @param json the request as a single line of JSON
 */
record HelperRequest(String operation, String json) {

    static final int PROTOCOL_VERSION = 1;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    HelperRequest {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(json, "json");
    }

    static HelperRequest probe() {
        return of(start("probe"));
    }

    static HelperRequest listDrives() {
        return of(start("list-drives"));
    }

    static HelperRequest readMedia(DriveId drive) {
        return of(start("read-media").put("drive", drive.value()));
    }

    static HelperRequest buildImage(DiscTree tree, String volumeLabel, Path target) {
        ObjectNode request = start("build-image")
                .put("target", target.toAbsolutePath().toString())
                .put("volumeLabel", volumeLabel);
        ArrayNode entries = request.putArray("entries");
        tree.entries()
                .forEach(entry -> entries.addObject()
                        .put("discPath", entry.discPath())
                        .put("source", entry.source().toAbsolutePath().toString()));
        return of(request);
    }

    static HelperRequest burnImage(DriveId drive, Path image, boolean closeMedia) {
        return of(start("burn-image")
                .put("drive", drive.value())
                .put("image", image.toAbsolutePath().toString())
                .put("closeMedia", closeMedia));
    }

    static HelperRequest blank(DriveId drive, boolean full) {
        return of(start("blank").put("drive", drive.value()).put("full", full));
    }

    static HelperRequest verifyImage(DriveId drive, Path image) {
        return of(start("verify-image")
                .put("drive", drive.value())
                .put("image", image.toAbsolutePath().toString()));
    }

    static HelperRequest eject(DriveId drive) {
        return of(start("eject").put("drive", drive.value()));
    }

    static HelperRequest watchDrives() {
        return of(start("watch-drives"));
    }

    static HelperRequest keepAwake(String reason) {
        return of(start("keep-awake").put("reason", reason));
    }

    static HelperRequest power() {
        return of(start("power"));
    }

    private static ObjectNode start(String operation) {
        return MAPPER.createObjectNode().put("protocol", PROTOCOL_VERSION).put("operation", operation);
    }

    private static HelperRequest of(ObjectNode request) {
        // Jackson escapes line breaks inside strings, so the request is always a single line.
        return new HelperRequest(request.get("operation").stringValue(), MAPPER.writeValueAsString(request));
    }
}
