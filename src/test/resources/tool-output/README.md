<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Captured tool output

Real output of the external tools, used as parser test fixtures. Never edit a
capture by hand except to remove personal data; add a new file when a tool
version changes its output.

Each file starts with the command line (`$ ...`) and, where captured by the
script, ends with the exit code (`[exit N]`). stdout and stderr are
interleaved as they arrived.

| Folder | Captured on | Versions |
|---|---|---|
| `xorriso/` | Ubuntu 24.04.5 (Docker `ubuntu:24.04`), 2026-10-31 | xorriso 1.5.6 |
| `cdrskin/`, `genisoimage/`, `ffmpeg/`, `dvdauthor/` | same | cdrskin 1.5.6, genisoimage 1.1.11, FFmpeg 6.1.1-3ubuntu5, dvdauthor/spumux 0.7.2 |
| `systemd-inhibit/` | Ubuntu 22.04.3 under WSL 2, normal user (not a local desktop session), 2026-10-31 | systemd 249 |
| `windows-helper/` | Windows 11 Pro 10.0.26200, standard user, USB drive "Slimtype eBAU108", 2026-11-01 | Windows PowerShell 5.1.26100, .NET Framework 4.8 (CLR 4.0.30319) |

Notes:

- `xorriso/create-iso-small.txt` used a pathspec list with names containing
  spaces, `=` and `\`; the extracted image matched the source tree byte for
  byte.
- `xorriso/create-iso-3gb.txt` wrote a 3 GB file of zeros to get many progress
  lines, including speed figures.
- `xorriso/devices-none-found.txt` and the image captures ran without any
  optical drive. Output with real drives will be added from the owner's
  hardware.
- `windows-helper/` files hold the helper's stdout only (JSON Lines), without
  a command line: the request is named by the file name and the operation
  tables in `docs/design/LLD-windows-platform.md`. The drive's serial number
  inside its id is replaced with zeros and the Windows user name in paths with
  `user`. `read-media-no-media.txt` was captured with the tray open.
