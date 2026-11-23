<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# RetroBurn user guide

This guide shows how to use RetroBurn: installing it, burning each kind of
disc, making covers and burn reels, and fixing common problems. For building
RetroBurn from source, see the [developer guide](DEVELOPER_GUIDE.md).

## 1. Install

| System | What to download | How |
|---|---|---|
| Windows 10 or 11 | `RetroBurn-<version>.msi` | Run it and follow the installer. RetroBurn appears in the Start menu. |
| Windows, no install | `RetroBurn-<version>-windows-portable.zip` | Unzip it anywhere and run `RetroBurn.exe`. |
| Ubuntu or Debian | `retroburn_<version>_amd64.deb` | `sudo apt install ./retroburn_<version>_amd64.deb` |

Downloads are on the repository's **Releases** page. Each release has a
`SHA256SUMS` file to check your download against.

- **Windows:** FFmpeg and dvdauthor come with RetroBurn; nothing else is
  needed. The installer is not code-signed yet, so SmartScreen may say the
  publisher is unknown. If the file came from this repository's releases and
  its checksum matches, choose **More info → Run anyway**.
- **Linux:** the package installs xorriso, cdrskin, genisoimage, FFmpeg and
  dvdauthor from your distribution. To burn, your user must be in the
  `cdrom` group:

  ```sh
  sudo usermod -aG cdrom "$USER"
  ```

  Then log out and back in.

RetroBurn never asks for administrator or root rights and never connects to
the internet.

## 2. The home screen

RetroBurn opens on **What do you want to make?** with one card per kind of
project:

| Card | Makes |
|---|---|
| **Data disc** | Files and folders on a CD, DVD or Blu-ray |
| **Burn ISO** | Writes an existing `.iso` image to a disc |
| **Create ISO** | An `.iso` image file; no drive needed |
| **Audio CD** | Music that plays in any CD player, with track names |
| **MP3 disc** | Music files for car stereos and MP3 players |
| **Video DVD** | Videos with a menu that play in DVD players |
| **Cover & label** | A case cover or disc label to print |

A card that is greyed out says why — usually a tool that is missing (see
[Troubleshooting](#9-troubleshooting)).

### The drive bar

Every burning screen has the drive bar at the top:

- **Disc drive** — pick the drive. It shows the drive's name and where it is
  (`D:` on Windows, `/dev/sr0` on Linux). USB drives get a **USB** badge.
  Drives that are plugged in or removed appear and disappear within a few
  seconds.
- The inserted disc, for example `DVD+RW · has data · 4.7 GB usable`.
- **Refresh** reads the disc again.
- **Erase disc…** appears only for a rewritable disc (CD-RW, DVD±RW, BD-RE)
  that has data on it.

Below the heading, the **capacity bar** shows how full the disc will be and
says how much too big a project is.

## 3. Burn a data disc

1. Choose **Data disc**.
2. Add what you want on the disc: **Add files…**, **Add folder…**, or drag
   files and folders from your file manager onto the list.
3. Type a **Disc name**. It is shortened to what both Windows and Linux can
   show (16 letters, digits, spaces, `_` and `-`); the hint under the field
   shows exactly what will be written.
4. Insert a blank or rewritable disc and press **Burn disc**.
5. Read the confirmation: the drive, the size to write and any warnings.
   **Check the disc after burning** is on by default — leave it on unless you
   are in a hurry. Press **Burn**.

RetroBurn first builds an image of your files, then writes it, then reads the
disc back and compares it byte for byte. **Done — The disc is ready and
matches your files** means every byte was checked.

> A write-once disc (CD-R, DVD±R, BD-R) that already has data cannot be
> used again; RetroBurn says so before burning. A rewritable disc with data
> is erased or overwritten — the confirmation tells you first.

### Create an ISO image

On the Data disc screen press **Create ISO…**, or use the **Create ISO** card,
and choose where to save the `.iso` file. No drive is needed.

### Burn an ISO image

Choose **Burn ISO**, then **Choose image…**, then **Burn disc**. Any ISO image
can be burned, not just RetroBurn's.

### Erase a rewritable disc

Use **Erase disc…** in the drive bar and choose:

- **Quick erase** — seconds to a minute; enough to reuse the disc.
- **Full erase** — can take as long as writing the whole disc (about 8
  minutes for a CD-RW).

Erasing shows no percentage, because drives do not report it reliably, and
on Windows an erase cannot be stopped once it has started.

## 4. Make an audio CD

1. Choose **Audio CD** and add your music files (or drop them on the list).
   Any format FFmpeg can read works: MP3, FLAC, WAV, AAC, Ogg and more.
   Copy-protected files are refused.
2. Titles and performers are filled in from the files' tags. Edit them in
   the table; **Move up** and **Move down** change the order.
3. The minutes bar shows the total, including the 2-second gap before each
   track, against the disc (80 minutes without a disc inserted).
4. Options:
   - **Write track names (CD-Text)** — shown by players with a display.
     CD-Text uses Latin letters only: accents outside Western European
     languages are simplified, and titles in other scripts (for example
     Devanagari or Cyrillic) need a Latin version before burning. On Windows,
     CD-Text cannot be written yet; the screen says so and the disc plays
     without track names.
   - **Even out loudness** — levels tracks that were mastered at different
     volumes.
5. Insert a **CD-R** (best for car and hi-fi players) or CD-RW and press
   **Burn disc**.

After burning, RetroBurn reads the disc's track list back and checks it
against what it wrote.

Limits: up to 99 tracks, each at least 4 seconds long.

## 5. Make an MP3 disc

An MP3 disc holds many more songs than an audio CD and plays in most car
stereos made since the mid-2000s.

1. Choose **MP3 disc** and use **Add files…** or **Add folder…** (every music
   file inside the folder is added).
2. Pick a **Layout**: **Keep folders**, **Artist / Album** (from the tags) or
   **All in one folder**.
3. Leave **Car stereo mode** on for car players. It gives files short, plain
   names with a track number in front (`01 - Title.mp3`), so they play in the
   right order and display correctly, and keeps folders at most two deep.
4. **Convert other formats to MP3** converts FLAC, WAV, AAC and so on, at the
   **Bitrate** you choose (192 kbps is a good default). Files that are already
   MP3 are copied unchanged.
5. **Playlist per folder** adds an `.m3u` playlist to each folder.
6. Check the list — the **How** column says **Copied** or **Converted** — then
   **Burn disc** or **Create ISO…**.

RetroBurn warns when a folder has more than 255 files or the disc more than 99
folders, which many car stereos cannot show.

## 6. Make a video DVD

1. Choose **Video DVD** and press **Add videos…**. Each video becomes a title
   on the disc.
2. Set the **Disc title**, the **TV standard** (PAL or NTSC — chosen from
   your country; use NTSC for the Americas and Japan), the **Shape**,
   **Chapters** (for skipping through long videos) and the **Menu** style:
   Classic, Y2K or Minimal.
3. The preview shows the menu. Each page holds six titles, with **Play all**
   and page buttons when needed. After a title, the player returns to the
   menu; tick **Play the next video after each one** to play straight on.
4. Insert a DVD±R or DVD±RW and press **Burn disc**, or:
   - **Create ISO…** for an image to burn later, or
   - **Save DVD folder…** for a `VIDEO_TS` folder you can play in VLC.

RetroBurn picks the best picture quality that fits the disc. If the videos
are very long, it warns that quality will be low — use fewer videos or a
dual-layer disc.

Converting video takes time — often longer than the videos themselves. Keep
the computer plugged in.

## 7. Covers and labels

Choose **Cover & label**.

1. Pick a template: **CD case front**, **CD case back with spines**, **CD
   slim case insert**, **DVD case wrap** or **Disc label**, and the paper
   (**A4** or **US Letter**).
2. **Re-sync from project** fills the track list and title from the last
   project you opened (an audio CD's tracks, a DVD's titles, a data disc's
   name).
3. Use **Add** to place text, pictures, shapes, the track list or the disc
   title. Drag to move, use the corner handle to resize, and the arrow keys
   to nudge (hold Shift for bigger steps). Elements snap to edges and centre
   lines; hold Alt while dragging to turn snapping off. **Style** applies a
   ready-made look.
4. **Undo** and **Redo** work as usual (Ctrl+Z, Ctrl+Y).
5. **Save…** keeps the design as a `.rbdesign` file to open later.
6. Finish with **Export PDF…** (best for print shops), **Export PNG…**, or
   **Print…**.

**Printing at the right size:** in the print dialog, choose **100 %** or
**Actual size** and turn off **Fit to page**. Print the **Print test page…**
once: each ruler should measure exactly 100 mm. Only use pictures you are
allowed to print.

## 8. Themes, sounds and burn reels

- **View → Theme** switches between **Classic** and **Y2K** (bevelled buttons,
  an LED progress bar and a pixel font). Everything restyles at once.
- **View → Sounds** turns the spin-up, burning and finished sounds on or off.
- **View → Animate the disc** stops the spinning disc. It also stays still
  when your system asks for reduced motion.

### Burn reels

After a successful burn, the result screen offers **Make my burn reel**: a
15-second vertical video of your burn, ready for a phone. **Hide item names in
reel** is on by default, so file and track names are replaced by "Item 1",
"Item 2" and so on. The reel is saved in your **Videos** folder
(`Videos/RetroBurn`); **Open folder** shows it.

Try it without burning: **Help → Make a demo burn reel**.

Reels are made on your computer and never uploaded.

## 9. Troubleshooting

| Problem | What to do |
|---|---|
| **"No disc drive found"** | Plug the drive in; it appears within a few seconds. On Linux, if the drive is listed with a permission message, add yourself to the `cdrom` group (section 1). |
| **A card or option is greyed out** | The reason is shown. On Linux, install the missing tool (for example `sudo apt install dvdauthor`). |
| **"Audio CDs need a CD-R or CD-RW"** | Audio CDs cannot be burned on DVDs. |
| **The burn is blocked because a title needs Latin text** | CD-Text cannot hold that script. Type a Latin version, or turn off **Write track names (CD-Text)**. |
| **"The drive was disconnected"** | A USB drive lost power or its cable came loose. Use the drive's own power supply or a powered USB port, avoid hubs, and keep a laptop plugged in. A rewritable disc can be erased and used again. |
| **"The disc doesn't match"** | The disc was written but cannot be trusted. Try another disc or a slower, better-quality brand. |
| **Cancelling takes a few seconds** | The drive must finish the track it is writing before it can stop. A cancelled write-once disc is usually unusable. |
| **"RetroBurn couldn't stop the computer from sleeping"** | Keep the computer awake yourself until the burn finishes. |
| **Windows: "RetroBurn could not start its Windows disc helper"** | A security policy or antivirus program is blocking PowerShell. Ask your administrator, or allow RetroBurn in your antivirus. |
| **Windows: SmartScreen warning** | See section 1. |
| **A printed cover is the wrong size** | Print at 100 % / Actual size, with Fit to page off; check with **Print test page…**. |

**Show details** on the result screen shows exactly what RetroBurn ran and
what the burning tools said. Please include it when you report a problem.

### Where RetroBurn keeps its files

| | Linux | Windows |
|---|---|---|
| Settings | `~/.config/retroburn/settings.properties` | `%APPDATA%\RetroBurn\settings.properties` |
| Logs | `~/.local/state/retroburn/logs/` | `%LOCALAPPDATA%\RetroBurn\logs\` |
| Burn sessions | `~/.local/share/retroburn/sessions/` | `%LOCALAPPDATA%\RetroBurn\data\sessions\` |
| Temporary work | `~/.cache/retroburn/work/` | `%LOCALAPPDATA%\RetroBurn\work\` |
| Burn reels | `~/Videos/RetroBurn/` | `Videos\RetroBurn\` in your user folder |

The logs contain command lines and file paths but no file contents; they never
leave your computer unless you attach them to a report. Temporary work folders
are deleted after each burn, and leftovers are cleaned up at the next start.

## 10. Reporting a problem

Open an issue on the repository with the **Bug report** form: your system,
the drive and disc, what you did, what happened, and the text from **Show
details**. Security problems go through the repository's **Security** tab
instead — see [SECURITY.md](../SECURITY.md).
