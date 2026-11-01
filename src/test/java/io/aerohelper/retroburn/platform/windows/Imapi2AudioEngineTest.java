// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.aerohelper.retroburn.engine.DriveId;
import io.aerohelper.retroburn.engine.TrackLayout;
import io.aerohelper.retroburn.tools.ProcessRunner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class Imapi2AudioEngineTest {

    private static final DriveId DRIVE = new DriveId("\\\\?\\usbstor#cdrom");

    @Test
    void tocOfTheOwnersDvdPlusRwIsOneTrackAndALeadOut() {
        TrackLayout layout = engine("windows-helper/read-toc-dvdplusrw.txt").readTrackLayout(DRIVE);
        assertEquals(new TrackLayout(List.of(0L), 1_151_849), layout);
    }

    @Test
    void writesNoCdText() {
        assertFalse(engine("windows-helper/read-toc-dvdplusrw.txt").writesCdText());
    }

    @Test
    void requestListsTheWavFilesInOrder() {
        HelperRequest request = HelperRequest.burnAudio(DRIVE, List.of(Path.of("C:/w/01.wav"), Path.of("C:/w/02.wav")));
        assertEquals("burn-audio", request.operation());
        assertEquals(true, request.json().matches(".*\"tracks\":\\[\"[^\"]*01\\.wav\",\"[^\"]*02\\.wav\"\\].*"));
    }

    private static Imapi2AudioEngine engine(String capture) {
        return new Imapi2AudioEngine(new HelperClient(new ProcessRunner(), () -> FakeHelper.launch("replay", capture)));
    }
}
