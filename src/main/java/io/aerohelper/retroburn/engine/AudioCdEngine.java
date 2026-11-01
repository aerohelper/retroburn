// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.engine;

import io.aerohelper.retroburn.common.CancelToken;
import java.util.List;

/**
 * Burns audio CDs. Implementations live in the platform packages (cdrskin on Linux, IMAPI2 on
 * Windows); see {@code docs/design/LLD-audio-cd.md} section 6. Failures are thrown as {@link
 * io.aerohelper.retroburn.common.RetroBurnException} subclasses, as for {@link BurnEngine}.
 */
public interface AudioCdEngine {

    /** True if {@link #burnAudioCd} writes the disc's CD-Text; false if it would be left out. */
    boolean writesCdText();

    /**
     * Writes the disc in one session and closes it. Progress runs from 0 to 1; CD-Text is written
     * only if {@link #writesCdText()}.
     */
    void burnAudioCd(DriveId drive, AudioDisc disc, BurnListener listener, CancelToken cancel);

    /** The length in sectors of each audio track on the disc in the drive, for the check after burning. */
    List<Long> readTrackSectors(DriveId drive);
}
