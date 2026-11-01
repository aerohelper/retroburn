<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Manual test checklist

| | |
|---|---|
| Status | Draft: data disc, Burn ISO, Create ISO and erase. Extended as each project type is built. |

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
  table (section 6).

Features the drive cannot test (for example Blu-ray on a DVD writer) are
written down as "not tested — no drive", not skipped silently.

## 2. Drives

| # | Steps | Expected |
|---|---|---|
| D1 | Start with no drive plugged in. Open Data disc. | Drive bar says "No disc drive found. Plug one in — it will show up here automatically." |
| D2 | Plug the drive in. | It appears in the drive bar within 3 s, with a USB badge and its drive letter (Windows) or device (Linux). |
| D3 | Insert the DVD+RW, then press Refresh. | Media text, for example "DVD+RW · has data · 4.7 GB usable"; capacity bar shows the right total. |
| D4 | Eject, then insert a blank CD-R. | Media text changes to the CD-R without restarting; capacity becomes about 700 MB. |
| D5 | Unplug the drive while no job runs. | Drive disappears; the view shows the empty state; no error dialog. |
| D6 | Linux only: run as a user outside the `cdrom` group. | Permission message naming the group, not a generic error. |

## 3. Data disc

| # | Steps | Expected |
|---|---|---|
| A1 | Add files, add the test folder, and drop a file from the file manager. | Entries listed with name on the disc and source path; capacity bar shows "Calculating…" then the size. |
| A2 | Add a second file with the same name. | It is listed as "name (2)". |
| A3 | Select entries and press Delete. | They are removed; size updates. |
| A4 | Type a disc name longer than 16 characters, and one with `/` or `.` in it. | Hint explains the limit and the replacement; burned disc shows the cleaned name. |
| A5 | Add more than fits on the disc. | Capacity bar says how much too big; Burn shows the blocker in the confirm dialog and cannot start. |
| A6 | Burn the test folder to a CD-R with verify on. | Steps tick through build, burn, verify; speed and elapsed time shown; result "Done" with "The disc is ready and matches your files." Open the disc in the file manager: names (including `Grüße.txt`), folders and contents match. |
| A7 | Burn to the DVD+RW that already has data. | Confirm dialog says the disc will be overwritten; burn succeeds; old content is gone. |
| A8 | Burn with verify off. | Result adds "It was not checked after burning." |
| A9 | Start a burn on the DVD+RW and press Cancel, then confirm. | "Stopping…", then the "Stopped" result within about 8 s (Windows track close, risk R18). The disc can be erased and reused. |
| A10 | Create ISO from the test folder, saved to a folder on another drive. | Image created; mounts and shows the same files and the disc name. |

## 4. Burn ISO and erase

| # | Steps | Expected |
|---|---|---|
| B1 | Choose the image from A10 and burn it to the DVD+RW. | Fits check shown; burned and verified. |
| B2 | Choose an image larger than the disc. | Over-capacity text; Burn blocked. |
| E1 | Erase disc… → Quick on the DVD+RW. | Confirm says all data will be deleted; Back is the default button; the disc is reported empty afterwards (on Windows a DVD+RW may still read as "has data" — see the Windows platform LLD). |
| E2 | Erase disc… → Full on a CD-RW or DVD-RW, if available. | Runs without a percentage; ends with success. |
| E3 | Erase disc… with a CD-R inserted. | "Erase disc…" is not shown in the drive bar. |

## 5. USB and power

Run these on the DVD+RW only.

| # | Steps | Expected |
|---|---|---|
| U1 | First burn after install. | USB tip shown in the confirm dialog; "Don't show again" hides it on the next burn. |
| U2 | Unplug the charger, then start a burn. | Battery warning in the confirm dialog. |
| U3 | During a burn, leave the laptop idle past its sleep timeout. | The computer stays awake; the progress view shows the sleep note (Linux: a warning if sleep could not be blocked). |
| U4 | Unplug the drive during a burn. | Result "The drive was disconnected or lost power…" with details; the app stays usable; replugging shows the drive again. |

## 6. Results

Copy this table into the pull request or release checklist.

| OS and version | Drive | Media | Steps run | Failures (step, what happened) | Date |
|---|---|---|---|---|---|
| | | | | | |

## 7. Automated real-disc test

Separate from this checklist, `WindowsRealDiscTest` burns, verifies and
erases the inserted rewritable disc through the engine, without the screens.
It runs only when `RETROBURN_ERASE_TEST_DISC=yes` is set, never on CI,
because it destroys the disc's content. See [PORTING.md](PORTING.md).
