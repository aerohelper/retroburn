<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Manual test checklist

| | |
|---|---|
| Status | Draft: data disc, Burn ISO, Create ISO, erase, audio CD, MP3 disc, video DVD and covers. Extended as each project type is built. |

Automated tests cover the models, parsers, pipelines and engines (see
[TEST_STRATEGY.md](TEST_STRATEGY.md)). This checklist covers what needs a
person, a window and a real drive: the screens, real discs and USB
behaviour. Run it on Linux and on Windows before a release, and after a
change to the screens or an engine.

## 1. Before you start

- A USB DVD writer, plugged straight into the laptop, and the laptop on its
  charger unless a step says otherwise.
- Blank media: one CD-R and one DVD-R for the write-once steps, and one
  DVD+RW (or CD-RW, DVD-RW) whose content may be destroyed.
- A test folder with a few files and subfolders, one file with a non-ASCII
  name (for example `Grüße.txt`), and one empty folder.
- Start the app with `./mvnw javafx:run`, or from an installed build.
- Write down the OS version, drive model and media brand in the results
  table (section 10).

Features the drive cannot test (for example Blu-ray on a DVD writer) are
written down as "not tested — no drive", not skipped silently.

## 2. Drives

| # | Steps | Expected |
|---|---|---|
| DRV-1 | Start with no drive plugged in. Open Data disc. | Drive bar says "No disc drive found. Plug one in — it will show up here automatically." |
| DRV-2 | Plug the drive in. | It appears in the drive bar within 3 s, with a USB badge and its drive letter (Windows) or device (Linux). |
| DRV-3 | Insert the DVD+RW, then press Refresh. | Media text, for example "DVD+RW · has data · 4.7 GB usable"; capacity bar shows the right total. |
| DRV-4 | Eject, then insert a blank CD-R. | Media text changes to the CD-R without restarting; capacity becomes about 700 MB. **Not tested: no CD-R at hand** (a blank CD-RW checks the same media change). |
| DRV-5 | Unplug the drive while no job runs. | Drive disappears; the view shows the empty state; no error dialog. |
| DRV-6 | Linux only: run as a user outside the `cdrom` group. | Permission message naming the group, not a generic error. |

## 3. Data disc

| # | Steps | Expected |
|---|---|---|
| DATA-1 | Add files, add the test folder, and drop a file from the file manager. | Entries listed with name on the disc and source path; capacity bar shows "Calculating…" then the size. |
| DATA-2 | Add a second file with the same name. | It is listed as "name (2)". |
| DATA-3 | Select entries and press Delete. | They are removed; size updates. |
| DATA-4 | Type a disc name longer than 16 characters, and one with `/` or `.` in it. | Hint explains the limit and the replacement; burned disc shows the cleaned name. |
| DATA-5 | Add more than fits on the disc. | Capacity bar says how much too big; Burn shows the blocker in the confirm dialog and cannot start. |
| DATA-6 | Burn the test folder to a CD-R with verify on. | Steps tick through build, burn, verify; speed and elapsed time shown; result "Done" with "The disc is ready and matches your files." Open the disc in the file manager: names (including `Grüße.txt`), folders and contents match. **Not tested: no CD-R at hand**; run it on a CD-RW instead and note that in the results. |
| DATA-7 | Burn to the DVD+RW that already has data. | Confirm dialog says the disc will be overwritten; burn succeeds; old content is gone. |
| DATA-8 | Burn with verify off. | Result adds "It was not checked after burning." |
| DATA-9 | Start a burn on the DVD+RW and press Cancel, then confirm. | "Stopping…", then the "Stopped" result within about 8 s (Windows track close, risk R18). The disc can be erased and reused. |
| DATA-10 | Create ISO from the test folder, saved to a folder on another drive. | Image created; mounts and shows the same files and the disc name. |

## 4. Burn ISO and erase

| # | Steps | Expected |
|---|---|---|
| ISO-1 | Choose the image from DATA-10 and burn it to the DVD+RW. | Fits check shown; burned and verified. |
| ISO-2 | Choose an image larger than the disc. | Over-capacity text; Burn blocked. |
| ERASE-1 | Erase disc… → Quick on the DVD+RW. | Confirm says all data will be deleted; Back is the default button; the disc is reported empty afterwards (on Windows a DVD+RW may still read as "has data" — see the Windows platform LLD). |
| ERASE-2 | Erase disc… → Full on a CD-RW or DVD-RW, if available. | Runs without a percentage; ends with success. |
| ERASE-3 | Erase disc… with a CD-R inserted. | "Erase disc…" is not shown in the drive bar. **Not tested: no CD-R at hand** (covered by unit tests of the drive bar's media rules). |

## 5. Audio CD

Needs a blank CD-R (or a CD-RW) and a few music files in different formats
(MP3, FLAC, M4A), one with a non-Latin title and one shorter than
4 seconds. **Not run yet: a CD-RW is on order.** Steps that say CD-R can
be run on the CD-RW; many older CD players and some car stereos cannot
read CD-RW, so a playback failure there points at the disc type first.

| # | Steps | Expected |
|---|---|---|
| AUDIO-1 | Add the files. | Titles and performers come from the tags (or the file name); the album fills in when all tracks share it; the minutes bar adds up. |
| AUDIO-2 | Rename, reorder and remove tracks. | The table and the minutes follow. |
| AUDIO-3 | With CD-Text on, keep the non-Latin title (Linux). | The problem is listed under the table and Burn is blocked; entering a Latin title clears it. |
| AUDIO-4 | Keep the track shorter than 4 seconds. | Burn is blocked naming that track. |
| AUDIO-5 | Insert the DVD+RW and press Burn. | "Audio CDs need a CD-R or CD-RW." |
| AUDIO-6 | Burn to a CD-R on Linux with CD-Text on. | Converting, writing and checking the track list tick through; the CD plays in a CD player with 2-second gaps; a CD-Text player (or `cdrskin dev=… cdtext_to_v07t=out.v07t`) shows the names. |
| AUDIO-7 | Burn to a CD-R on Windows. | Same, without CD-Text; the confirm dialog says CD-Text can't be written. |
| AUDIO-8 | Cancel during writing (CD-RW). | "Stopped"; the disc can be erased and reused. |
| AUDIO-9 | Burn with "Even out loudness". | Tracks play at similar loudness. |

## 6. MP3 disc

| # | Steps | Expected |
|---|---|---|
| MP3-1 | Add a folder of albums (MP3 and FLAC, some names with accents and symbols). | Names on the disc follow car stereo mode: "01 - Title.mp3", no accents, at most two folder levels; FLAC files show "Converted". |
| MP3-2 | Switch layouts and car stereo mode off and on. | The table follows; turning conversion off lists the FLAC files as left out. |
| MP3-3 | Burn to the DVD+RW with playlists on. | Converting, building, writing and checking tick through; the disc opens in the file manager with the names shown, and the MP3 files are identical to the originals. |
| MP3-4 | Play the disc in a car stereo. | Folders and tracks play in order; titles show. |

## 7. Video DVD

Needs two or three short video files (one 16:9, one 4:3, one in a format
such as MKV or MOV) and, for one step, enough files for two menu pages
(seven or more).

| # | Steps | Expected |
|---|---|---|
| VIDEO-1 | Add the files. | Titles listed with duration; the aspect ratio follows the first file; the minutes bar shows the estimated size and the quality note when the bitrate is low. |
| VIDEO-2 | Change TV standard, aspect, chapter interval, template and disc title. | The menu preview redraws with the chosen template and title. |
| VIDEO-3 | Save VIDEO_TS folder, then open it in VLC as a DVD (Media → Open Disc, with "No disc menus" unchecked). | The menu shows thumbnails and names; the remote/arrow keys move between buttons in grid order; each button plays its title; "Play all" plays every title in turn; chapters skip every few minutes; after a title the menu comes back. |
| VIDEO-4 | With seven or more titles, check page buttons in VLC. | "Next page" and "Previous page" move between pages. |
| VIDEO-5 | Choose "play next" after a title. | Titles play one after another; after the last, the menu returns. |
| VIDEO-6 | Create ISO, then open the image in VLC. | Same as VIDEO-3. |
| VIDEO-7 | Burn to the DVD+RW. | Converting, menus, authoring, building, writing and verifying tick through; the disc plays in VLC as in VIDEO-3. |
| VIDEO-8 | Play the disc from VIDEO-7 in a hardware DVD player, once for a disc made on Linux and once for one made on Windows. | Same as VIDEO-3 on a TV. A Windows disc that plays in VLC but not in the player points at the IMAPI2FS image layout (ADR-0015). |
| VIDEO-9 | Cancel during converting. | "Stopped" quickly; no files left in the work folder unless "keep temporary files" is on. |

## 8. Covers and labels

Needs a printer, plain paper, a ruler, and a CD jewel case, a DVD case and
a disc to try the cut-outs against. Record measurements in
[PRINT_TEMPLATES.md](PRINT_TEMPLATES.md).

| # | Steps | Expected |
|---|---|---|
| COVER-1 | Print test page… with "Actual size" in the print dialog. | The rulers measure 100 mm and the square 100 × 100 mm (±0.5 mm); the circle is 118 mm across. |
| COVER-2 | Open an audio CD project with tracks, then Cover & label → CD case back with spines → Re-sync. | The track list and spine titles show the project's tracks and title. |
| COVER-3 | Add a picture. | The image-rights note appears the first time only; a low-resolution picture shows the "may look soft" note. |
| COVER-4 | Drag, resize and nudge elements; undo and redo several steps. | Elements snap to the edges, centre and safe zone (not while Alt is held); undo and redo walk back and forth; one drag is one undo step. |
| COVER-5 | Save, close, open the design again. | It looks the same, pictures included. |
| COVER-6 | Export PDF for each item on A4 (and Letter if available); open it in a PDF viewer. | Page size matches the paper; text is selectable; crop marks or cut circles are there. |
| COVER-7 | Print the CD front, the back inlay, the DVD wrap and two disc labels at 100 %; cut them out. | Each fits its case; spines fold on the fold marks; the label fits the disc with the 41 mm hole. |
| COVER-8 | Choose a font marked as not allowing PDF embedding (if the computer has one) and export PDF. | The export stops and names the font; printing still works. |

## 9. USB and power

Run these on the DVD+RW only.

| # | Steps | Expected |
|---|---|---|
| USB-1 | First burn after install. | USB tip shown in the confirm dialog; "Don't show again" hides it on the next burn. |
| USB-2 | Unplug the charger, then start a burn. | Battery warning in the confirm dialog. |
| USB-3 | During a burn, leave the laptop idle past its sleep timeout. | The computer stays awake; the progress view shows the sleep note (Linux: a warning if sleep could not be blocked). |
| USB-4 | Unplug the drive during a burn. | Result "The drive was disconnected or lost power…" with details; the app stays usable; replugging shows the drive again. |

## 10. Results

Copy this table into the pull request or release checklist.

| OS and version | Drive | Media | Steps run | Failures (step, what happened) | Date |
|---|---|---|---|---|---|
| | | | | | |

## 11. Automated real-disc test

Separate from this checklist, `WindowsRealDiscTest` burns, verifies and
erases the inserted rewritable disc through the engine, without the screens.
It runs only when `RETROBURN_ERASE_TEST_DISC=yes` is set, never on CI,
because it destroys the disc's content. See [PORTING.md](PORTING.md).
