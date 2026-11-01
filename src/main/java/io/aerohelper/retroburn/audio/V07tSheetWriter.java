// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import io.aerohelper.retroburn.engine.CdText;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes CD-Text as a Sony Input Sheet version 0.7T for cdrskin's {@code input_sheet_v07t=}.
 *
 * <p>Format from libburn's {@code doc/cdtext.txt} ("Sony Text File Format"): lines of {@code
 * purpose specifier = content text}; "Text Code = 8859" selects ISO-8859-1, which is also the file's
 * encoding. See {@code docs/design/LLD-audio-cd.md} section 6.1.
 */
public final class V07tSheetWriter {

    private V07tSheetWriter() {}

    /** The sheet's text. Every text must already be cleaned by {@link CdTextRules}. */
    public static String render(CdText text) {
        StringBuilder sheet = new StringBuilder();
        line(sheet, "Input Sheet Version", "0.7T");
        line(sheet, "Text Code", "8859");
        line(sheet, "Language Code", "English");
        line(sheet, "Album Title", text.albumTitle());
        line(sheet, "Artist Name", text.albumPerformer());
        line(sheet, "First Track Number", "1");
        line(sheet, "Last Track Number", Integer.toString(text.tracks().size()));
        for (int index = 0; index < text.tracks().size(); index++) {
            CdText.Track track = text.tracks().get(index);
            String number = "%02d".formatted(index + 1);
            line(sheet, "Track " + number + " Title", track.title());
            line(sheet, "Track " + number + " Artist", track.performer());
        }
        return sheet.toString();
    }

    /** Writes the sheet to {@code target} in ISO-8859-1. */
    public static void write(CdText text, Path target) throws IOException {
        String sheet = render(text);
        if (!StandardCharsets.ISO_8859_1.newEncoder().canEncode(sheet)) {
            throw new IllegalArgumentException("CD-Text has characters outside ISO-8859-1");
        }
        Files.writeString(target, sheet, StandardCharsets.ISO_8859_1);
    }

    private static void line(StringBuilder sheet, String specifier, String content) {
        if (content.indexOf('\n') >= 0 || content.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("CD-Text must be one line: " + specifier);
        }
        sheet.append("%-19s = %s\n".formatted(specifier, content));
    }
}
