// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.tools;

/** User-visible capabilities that depend on external tools or OS services. */
public enum Feature {
    BURNING("feature.burning"),
    ISO_CREATION("feature.isoCreation"),
    VERIFY("feature.verify"),
    AUDIO_CD("feature.audioCd"),
    MP3_TRANSCODING("feature.mp3Transcoding"),
    VIDEO_DVD("feature.videoDvd"),
    /** Turning an authored DVD folder into a disc image; folder export works without it. */
    VIDEO_DVD_IMAGE("feature.videoDvdImage"),
    BURN_REEL("feature.burnReel");

    private final String messageKey;

    Feature(String messageKey) {
        this.messageKey = messageKey;
    }

    /** Key of the feature's display name in {@code messages.properties}. */
    public String messageKey() {
        return messageKey;
    }
}
