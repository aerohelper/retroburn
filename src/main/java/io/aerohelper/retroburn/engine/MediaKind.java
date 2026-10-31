// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import java.util.Optional;

/** The kind of medium in a drive, with what can be done with it. */
public enum MediaKind {
    CD_ROM(MediaFamily.CD, false, false),
    CD_R(MediaFamily.CD, true, false),
    CD_RW(MediaFamily.CD, true, true),
    DVD_ROM(MediaFamily.DVD, false, false),
    DVD_R(MediaFamily.DVD, true, false),
    DVD_RW(MediaFamily.DVD, true, true),
    DVD_RAM(MediaFamily.DVD, true, true),
    DVD_R_DL(MediaFamily.DVD, true, false),
    DVD_PLUS_R(MediaFamily.DVD, true, false),
    DVD_PLUS_RW(MediaFamily.DVD, true, true),
    DVD_PLUS_R_DL(MediaFamily.DVD, true, false),
    BD_ROM(MediaFamily.BD, false, false),
    BD_R(MediaFamily.BD, true, false),
    BD_RE(MediaFamily.BD, true, true),
    /** No medium in the drive. */
    NONE(null, false, false),
    /** A medium the drive could not identify. */
    UNKNOWN(null, false, false);

    private final MediaFamily family;
    private final boolean writable;
    private final boolean rewritable;

    MediaKind(MediaFamily family, boolean writable, boolean rewritable) {
        this.family = family;
        this.writable = writable;
        this.rewritable = rewritable;
    }

    /** CD, DVD or BD; empty for {@link #NONE} and {@link #UNKNOWN}. */
    public Optional<MediaFamily> family() {
        return Optional.ofNullable(family);
    }

    /** Whether data can be written to this kind of medium at all. */
    public boolean isWritable() {
        return writable;
    }

    /** Whether the medium can be erased and written again. */
    public boolean isRewritable() {
        return rewritable;
    }
}
