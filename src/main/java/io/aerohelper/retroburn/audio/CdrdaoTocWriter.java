// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import io.aerohelper.retroburn.engine.AudioDisc;
import io.aerohelper.retroburn.engine.CdText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Writes a cdrdao TOC file for an audio CD, with CD-Text when given.
 *
 * <p>Syntax from the cdrdao 1.2.6 manual ("TOC FILES", "CD-TEXT Blocks") and {@code
 * trackdb/TocParser.g}: strings are UTF-8 with {@code \"} and {@code \\} escapes; once TITLE or
 * PERFORMER is used, it must be present for the disc and every track. Every track gets {@code
 * PREGAP 0:2:0}. See {@code docs/design/LLD-audio-cd.md} section 6.2.
 */
public final class CdrdaoTocWriter {

    private CdrdaoTocWriter() {}

    /** The TOC's text; WAV paths are written as given, so pass names relative to the TOC's folder. */
    public static String render(AudioDisc disc) {
        StringBuilder toc = new StringBuilder("CD_DA\n");
        Optional<CdText> text = disc.cdText();
        text.ifPresent(cdText -> {
            toc.append("CD_TEXT {\n");
            toc.append("  LANGUAGE_MAP {\n    0 : EN\n  }\n");
            language(toc, "  ", cdText.albumTitle(), cdText.albumPerformer());
            toc.append("}\n");
        });
        for (int index = 0; index < disc.tracks().size(); index++) {
            toc.append("\nTRACK AUDIO\n");
            if (text.isPresent()) {
                CdText.Track track = text.get().tracks().get(index);
                toc.append("CD_TEXT {\n");
                language(toc, "  ", track.title(), track.performer());
                toc.append("}\n");
            }
            toc.append("PREGAP 0:2:0\n");
            toc.append("FILE ")
                    .append(quoted(disc.tracks().get(index).wav().toString().replace('\\', '/')));
            toc.append(" 0\n");
        }
        return toc.toString();
    }

    /** Writes the TOC to {@code target} in UTF-8. */
    public static void write(AudioDisc disc, Path target) throws IOException {
        Files.writeString(target, render(disc), StandardCharsets.UTF_8);
    }

    private static void language(StringBuilder toc, String indent, String title, String performer) {
        toc.append(indent).append("LANGUAGE 0 {\n");
        toc.append(indent).append("  TITLE ").append(quoted(title)).append('\n');
        toc.append(indent).append("  PERFORMER ").append(quoted(performer)).append('\n');
        toc.append(indent).append("}\n");
    }

    static String quoted(String text) {
        if (text.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\t')) {
            throw new IllegalArgumentException("TOC strings cannot hold line breaks or tabs");
        }
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}
