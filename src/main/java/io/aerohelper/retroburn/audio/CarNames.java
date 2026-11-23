// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.audio;

import java.text.Normalizer;

/**
 * File and folder names for MP3 discs: safe and short for car stereos, or only free of characters no
 * disc file system allows. See {@code docs/design/LLD-mp3-disc.md} section 2.
 */
public final class CarNames {

    public static final int FILE_LIMIT = 64;
    public static final int FOLDER_LIMIT = 32;
    public static final int PLAIN_LIMIT = 100;

    private static final String CAR_ALLOWED = " -_.,()&'!";
    private static final String NEVER_ALLOWED = "/\\:*?\"<>|";

    private CarNames() {}

    /** A file name for a car stereo, extension kept, at most {@link #FILE_LIMIT} characters. */
    public static String carFile(String baseName, String extension) {
        return limit(carText(baseName, "Track"), "." + extension, FILE_LIMIT);
    }

    /** A folder name for a car stereo, at most {@link #FOLDER_LIMIT} characters. */
    public static String carFolder(String name) {
        return limit(carText(name, "Folder"), "", FOLDER_LIMIT);
    }

    /** A file or folder name with only the characters no disc file system allows replaced. */
    public static String plain(String baseName, String extension, String fallback) {
        StringBuilder text = new StringBuilder();
        baseName.codePoints()
                .forEach(c ->
                        text.appendCodePoint(Character.isISOControl(c) || NEVER_ALLOWED.indexOf(c) >= 0 ? '_' : c));
        String cleaned = trimEnds(text.toString());
        return limit(cleaned.isEmpty() ? fallback : cleaned, extension.isEmpty() ? "" : "." + extension, PLAIN_LIMIT);
    }

    /** "01 - name": the position prefix car stereos sort by. */
    public static String prefixed(int position, String name) {
        return "%02d - %s".formatted(position, name);
    }

    /** Text with car-safe characters only: accents dropped, other characters become '_'. */
    public static String carText(String input, String fallback) {
        String decomposed = Normalizer.normalize(transliterate(input), Normalizer.Form.NFD);
        StringBuilder text = new StringBuilder();
        decomposed
                .codePoints()
                .filter(c -> Character.getType(c) != Character.NON_SPACING_MARK)
                .forEach(c -> {
                    boolean letterOrDigit = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
                    text.append(letterOrDigit || CAR_ALLOWED.indexOf(c) >= 0 ? (char) c : '_');
                });
        // A run of spaces and '_' with a space in it becomes one space; then runs of '_' become one.
        String collapsed = text.toString().replaceAll("[ _]* [ _]*", " ").replaceAll("_+", "_");
        String cleaned = trimEnds(collapsed);
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    /** Latin letters that are not a base letter plus a mark, so decomposing does not reach them. */
    private static String transliterate(String input) {
        StringBuilder out = new StringBuilder();
        input.codePoints()
                .forEach(c -> out.append(
                        switch (c) {
                            case 'ß' -> "ss";
                            case 'æ' -> "ae";
                            case 'Æ' -> "AE";
                            case 'œ' -> "oe";
                            case 'Œ' -> "OE";
                            case 'ø' -> "o";
                            case 'Ø' -> "O";
                            case 'ł' -> "l";
                            case 'Ł' -> "L";
                            case 'đ' -> "d";
                            case 'Đ' -> "D";
                            case 'þ' -> "th";
                            case 'Þ' -> "Th";
                            default -> new String(Character.toChars(c));
                        }));
        return out.toString();
    }

    private static String trimEnds(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && " ._".indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && " ._".indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }

    private static String limit(String base, String extension, int limit) {
        int room = Math.max(1, limit - extension.length());
        String cut = base.length() > room ? trimEnds(base.substring(0, room)) : base;
        return (cut.isEmpty() ? base.substring(0, 1) : cut) + extension;
    }
}
