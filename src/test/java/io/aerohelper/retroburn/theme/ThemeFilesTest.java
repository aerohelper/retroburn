// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.Test;

/** Both built-in themes have their stylesheet, fonts and four sounds in the expected format. */
class ThemeFilesTest {

    private final ThemeRegistry registry = ThemeRegistry.builtIn();

    @Test
    void classicIsTheDefaultAndOnlyY2kPlaysSoundsByDefault() {
        assertEquals(
                List.of("classic", "y2k"),
                registry.all().stream().map(Theme::id).toList());
        assertEquals("classic", registry.get("no-such-theme").id());
        assertFalse(registry.get("classic").soundsDefault());
        assertTrue(registry.get("y2k").soundsDefault());
        assertEquals(
                List.of("/fonts/vt323/VT323-Regular.ttf"), registry.get("y2k").fonts());
        assertEquals("Y2K", registry.get("y2k").name());
    }

    @Test
    void stylesheetsAndFontsExist() {
        for (Theme theme : registry.all()) {
            assertNotNull(ThemeFilesTest.class.getResource(theme.stylesheet()), theme.stylesheet());
            for (String font : theme.fonts()) {
                assertNotNull(ThemeFilesTest.class.getResource(font), font);
            }
        }
    }

    @Test
    void soundsAre22kHzMono16BitPcmOfSensibleLength() throws IOException, UnsupportedAudioFileException {
        for (Theme theme : registry.all()) {
            for (Sound sound : Sound.values()) {
                String resource = theme.sound(sound);
                try (InputStream raw = ThemeFilesTest.class.getResourceAsStream(resource)) {
                    assertNotNull(raw, resource);
                    try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                        AudioFormat format = audio.getFormat();
                        assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(), resource);
                        assertEquals(22_050, format.getSampleRate(), resource);
                        assertEquals(1, format.getChannels(), resource);
                        assertEquals(16, format.getSampleSizeInBits(), resource);
                        double seconds = audio.getFrameLength() / format.getFrameRate();
                        assertTrue(seconds >= 0.4 && seconds <= 2.0, resource + " lasts " + seconds + " s");
                    }
                }
            }
        }
    }
}
