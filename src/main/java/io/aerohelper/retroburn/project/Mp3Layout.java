// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.project;

/** Where the files of an MP3 disc go. See {@code docs/design/LLD-mp3-disc.md} section 3. */
public enum Mp3Layout {
    /** The folders as added. */
    KEEP_FOLDERS,
    /** Artist/Album from the tags. */
    ARTIST_ALBUM,
    /** Everything in one folder. */
    FLAT
}
