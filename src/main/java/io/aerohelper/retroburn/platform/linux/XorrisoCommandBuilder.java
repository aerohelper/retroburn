// SPDX-FileCopyrightText: 2026 Chetan and contributors
// SPDX-License-Identifier: GPL-3.0-or-later
package io.aerohelper.retroburn.platform.linux;

import io.aerohelper.retroburn.engine.DriveId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds every xorriso argument list RetroBurn uses. The only place xorriso options appear.
 *
 * <p>Options are from the xorriso 1.5.6 manual, https://www.gnu.org/software/xorriso/man_1_xorriso.html,
 * and were checked against real runs; see {@code docs/design/LLD-linux-platform.md} section 2.
 */
final class XorrisoCommandBuilder {

    private XorrisoCommandBuilder() {}

    /** Lists the drives the user can open: {@code -devices}. */
    static List<String> listDrives() {
        List<String> arguments = commonPrefix();
        // Prints one "N  -dev '/dev/srN' rwrw-- :  'vendor' 'product'" line per accessible drive.
        arguments.add("-devices");
        return arguments;
    }

    /** Describes the medium in a drive: {@code -outdev <drive> -toc}. */
    static List<String> readMedia(DriveId drive) {
        List<String> arguments = commonPrefix();
        // -outdev acquires the drive without loading an ISO image tree from it, which is all -toc needs
        // and avoids reading a possibly large directory tree.
        arguments.add("-outdev");
        arguments.add(drive.value());
        // Prints "Media current", "Media status" and "Media blocks" lines for the acquired drive.
        arguments.add("-toc");
        return arguments;
    }

    /**
     * Writes an ISO image file from a pathspec list.
     *
     * @param target ISO file to create; must not exist, or xorriso would append a session to it
     * @param volumeLabel volume ID
     * @param pathList file with one {@code disc_path=source_path} line per entry
     */
    static List<String> createImage(Path target, String volumeLabel, Path pathList) {
        List<String> arguments = commonPrefix();
        // The names in the path list are written as UTF-8 regardless of the user's locale.
        arguments.add("-local_charset");
        arguments.add("UTF-8");
        // "stdio:" makes xorriso write to a file instead of an optical drive.
        arguments.add("-outdev");
        arguments.add("stdio:" + target);
        arguments.add("-volid");
        arguments.add(volumeLabel);
        // Joliet gives Windows long Unicode names; Rock Ridge (for Linux) is on by default.
        arguments.add("-joliet");
        arguments.add("on");
        // "iso_path=disk_path" pairs: '=' is escaped in the disc path only, '\' is not escaped.
        arguments.add("-pathspecs");
        arguments.add("on");
        // Read the pairs from a file, so huge compilations do not hit command-line length limits.
        arguments.add("-path_list");
        arguments.add(pathList.toString());
        // Write the image, then exit.
        arguments.add("-commit");
        arguments.add("-end");
        return arguments;
    }

    /**
     * Writes an image file to a drive as one data track: the manual's "Burn an existing ISO image
     * file to medium" recipe, without its implicit blanking.
     *
     * @param closeMedia finalise the disc; otherwise leave it appendable where the medium allows
     */
    static List<String> burnImage(DriveId drive, Path image, boolean closeMedia) {
        List<String> arguments = commonPrefix();
        // cdrecord emulation: "a single data track per session to be written to blank, overwritable,
        // or appendable media"; a track source makes it commit automatically.
        arguments.add("-as");
        arguments.add("cdrecord");
        // Verbose, so "Show details" has the drive's own messages.
        arguments.add("-v");
        // "dev= must be given as xorriso device address": /dev/srN, or stdio:<file> in tests.
        arguments.add("dev=" + drive.value());
        if (!closeMedia) {
            // "The medium gets closed if closing is applicable and not option -multi is present."
            arguments.add("-multi");
        }
        // The track source, always absolute so it can never look like an option.
        arguments.add(image.toAbsolutePath().toString());
        // Ends the cdrecord option list.
        arguments.add("--");
        return arguments;
    }

    /**
     * Makes a rewritable disc ready for writing from scratch.
     *
     * @param full erase thoroughly ({@code -blank all}) rather than quickly ({@code -blank as_needed})
     */
    static List<String> blank(DriveId drive, boolean full) {
        List<String> arguments = commonPrefix();
        // -blank "affects only the -outdev", which also avoids loading the disc's ISO tree.
        arguments.add("-outdev");
        arguments.add(drive.value());
        // as_needed: blanks used CD-RW, DVD-RW and overwritable media, formats new DVD-RAM and BD-RE,
        // and leaves blank media alone. all: "might work more thoroughly and need more time".
        arguments.add("-blank");
        arguments.add(full ? "all" : "as_needed");
        return arguments;
    }

    /**
     * Copies the first {@code blocks} 2048-byte blocks of the disc to standard output: the manual's
     * {@code -check_media data_to=- use=outdev what=disc} recipe.
     */
    static List<String> readBack(DriveId drive, long blocks) {
        if (blocks < 1) {
            throw new IllegalArgumentException("Nothing to read back: " + blocks + " blocks");
        }
        List<String> arguments = commonPrefix();
        arguments.add("-outdev");
        arguments.add(drive.value());
        arguments.add("-check_media");
        // Read from the output drive, which "avoids loading the ISO image tree from media".
        arguments.add("use=outdev");
        // "scans the payload range of a medium without respecting track gaps".
        arguments.add("what=disc");
        // Exactly the blocks the image occupies.
        arguments.add("min_lba=0");
        arguments.add("max_lba=" + (blocks - 1));
        // Unreadable blocks raise a FAILURE at the end, so a bad disc gives a non-zero exit.
        arguments.add("event=FAILURE");
        // The blocks go to stdout; result messages are redirected to the info channel (stderr).
        arguments.add("data_to=-");
        // Ends the -check_media option list.
        arguments.add("--");
        return arguments;
    }

    /** Opens the tray: {@code -outdev <drive> -eject out}. Does nothing for {@code stdio:} files. */
    static List<String> eject(DriveId drive) {
        List<String> arguments = commonPrefix();
        arguments.add("-outdev");
        arguments.add(drive.value());
        arguments.add("-eject");
        arguments.add("out");
        return arguments;
    }

    /** Options that make every run fail clearly and report progress. */
    private static List<String> commonPrefix() {
        List<String> arguments = new ArrayList<>();
        // Stop at the first real failure instead of carrying on with a broken result.
        arguments.add("-abort_on");
        arguments.add("FAILURE");
        // Exit with 32 when a lesser problem (SORRY) occurred, so partial success is never mistaken
        // for success.
        arguments.add("-return_with");
        arguments.add("SORRY");
        arguments.add("32");
        // Emit the "UPDATE" progress messages the progress parser reads.
        arguments.add("-report_about");
        arguments.add("UPDATE");
        return arguments;
    }
}
