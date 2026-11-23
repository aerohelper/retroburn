// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.windows;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One line of the Windows helper's output. See {@code docs/design/LLD-windows-platform.md} section 4
 * for the fields and which operation sends which event.
 */
sealed interface HelperEvent {

    /** An optical drive, from {@code list-drives}. */
    record Drive(
            String id, String vendor, String product, String revision, String volume, String bus, boolean accessible)
            implements HelperEvent {

        public Drive {
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * The medium in a drive, from {@code read-media}. The numbers are IMAPI2 values; when {@code present}
     * is false they are 0.
     */
    record Media(boolean present, int physicalType, int status, long freeSectors, long totalSectors)
            implements HelperEvent {}

    /**
     * Progress of the current stage.
     *
     * @param stage {@code image}, {@code erasing}, {@code verifying}, or a write stage such as {@code
     *     writing}
     * @param done units completed; the unit depends on the stage (bytes, sectors or seconds)
     * @param total units in the stage; 0 if unknown
     */
    record Progress(String stage, long done, long total) implements HelperEvent {

        public Progress {
            Objects.requireNonNull(stage, "stage");
        }
    }

    /** The unique ids of all drives, from {@code watch-drives}. */
    record Drives(List<String> ids) implements HelperEvent {

        public Drives {
            ids = List.copyOf(ids);
        }
    }

    /** Where power comes from: {@code mains}, {@code battery} or {@code unknown}. */
    record Power(String source) implements HelperEvent {

        public Power {
            Objects.requireNonNull(source, "source");
        }
    }

    /** The helper is running; for {@code keep-awake}, the request is held. */
    record Ready(String powerShellVersion, String clrVersion, String languageMode) implements HelperEvent {}

    /** Text for "Show details", including lines that are not helper events at all. */
    record Log(String text) implements HelperEvent {

        public Log {
            Objects.requireNonNull(text, "text");
        }
    }

    /** The operation succeeded; always the last event. */
    record Result() implements HelperEvent {}

    /** A disc's table of contents: track start LBAs, their control nibbles, and the lead-out LBA. */
    record Toc(List<Long> starts, List<Integer> controls, long leadOut) implements HelperEvent {

        public Toc {
            starts = List.copyOf(starts);
            controls = List.copyOf(controls);
        }
    }

    /**
     * The operation failed; always the last event.
     *
     * @param code one of the protocol's error codes, for example {@code NO_MEDIA}
     * @param message the helper's or IMAPI2's own explanation, in English
     * @param hresult the COM error code as {@code 0x…}, if the failure came from COM
     */
    record Error(String code, String message, Optional<String> hresult) implements HelperEvent {

        public Error {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(hresult, "hresult");
        }
    }
}
