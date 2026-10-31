<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — Linux platform (`platform.linux`)

| | |
|---|---|
| Status | Implemented in the core milestone (M2): drive and media info, ISO creation, hot-plug, USB detection, sleep inhibition, power status |
| Package | `io.aerohelper.retroburn.platform.linux` |
| Related | [LLD-engine-and-platform.md](LLD-engine-and-platform.md), [LLD-tools.md](LLD-tools.md) |

## 1. Classes

| Class | Responsibility |
|---|---|
| `LinuxPlatform` | Wires everything below; supplies the Linux feature requirement table. |
| `LinuxAppPaths` | XDG folders: `$XDG_CONFIG_HOME/retroburn`, `$XDG_DATA_HOME/retroburn`, `$XDG_STATE_HOME/retroburn/logs`, `$XDG_CACHE_HOME/retroburn/work`, `~/Videos/RetroBurn`, with the XDG defaults (`~/.config`, `~/.local/share`, `~/.local/state`, `~/.cache`) when the variables are unset, empty or relative. |
| `XorrisoCommandBuilder` | The only place xorriso command lines are built; one comment per argument. |
| `XorrisoDeviceListParser` | Parses `-devices` output into `DriveInfo`s. |
| `XorrisoMediaInfoParser` | Parses `-toc` output into `MediaInfo`. |
| `XorrisoProgressParser` | Parses `Writing:` progress lines into `BurnEvent.Progress`. |
| `XorrisoErrorParser` | Picks the `FAILURE`/`SORRY` lines that explain a failed run. |
| `PathspecListWriter` | Streams a `DiscTree` into a `-path_list` file with xorriso's escaping. |
| `XorrisoEngine` | `BurnEngine`: lists drives and reads media. |
| `XorrisoImageBuilder` | `DiscImageBuilder`: writes ISO images to a file. |
| `SysfsOpticalDrives` | Lists `/sys/block/sr*` (the cheap hot-plug signature) and determines the connection type. |
| `SysfsPowerStatus` | Battery or mains from `/sys/class/power_supply`. |
| `SystemdSleepInhibitor` | Holds a `systemd-inhibit` lock for the burn's duration. |

## 2. xorriso usage (verified against xorriso 1.5.6)

Sources: the xorriso manual (https://www.gnu.org/software/xorriso/man_1_xorriso.html),
the libisoburn source (`xorriso/drive_mgt.c`, `xorriso/write_run.c`) and the
libburn profile table (`libburn/mmc.c`), plus output captured on Ubuntu 24.04
in `src/test/resources/tool-output/xorriso/`.

Common prefix for every run:

| Argument | Why |
|---|---|
| `-abort_on FAILURE` | Stop at the first real failure instead of carrying on. Recognised from the start when given as a program argument. |
| `-return_with SORRY 32` | Exit with 32 when a problem of severity SORRY happened without aborting, so "partial success" is never mistaken for success. |
| `-report_about UPDATE` | Emit the `UPDATE` progress messages we parse. |

### List drives

`xorriso <prefix> -devices`

Output format, from `Xorriso_show_devices()`:
`"%d  -dev '%s' "` + padding + `"%s :  '%-8.8s' '%s' \n"` — index, device
path, permission flags, vendor (padded to 8), product. When no drive is
accessible it prints `xorriso : SORRY : No drives found` and exits 32 (with
our `-return_with`); that is an empty list, not an error.

Drives listed in `/sys/block` but missing from `-devices` are drives the user
lacks read/write permission for (the manual: "a device has to offer
rw-permissions"). The engine reports them with a permission hint (`cdrom`
group).

### Read media

`xorriso <prefix> -outdev <device> -toc`

| Line | Meaning |
|---|---|
| `Media current: <profile>[, <role>]` | Profile name from libburn's table: `CD-ROM`, `CD-R`, `CD-RW`, `DVD-ROM`, `DVD-R sequential recording`, `DVD-RAM`, `DVD-RW restricted overwrite`, `DVD-RW sequential recording`, `DVD-R/DL sequential recording`, `DVD-R/DL layer jump recording`, `DVD+RW`, `DVD+R`, `DVD+R/DL`, `BD-ROM`, `BD-R sequential recording`, `BD-R random recording`, `BD-RE`; or `is not recognizable`. |
| `Media status : …` | `is blank`, `is written , is appendable`, `is written , is closed`, `is not present`, `is not recognizable`, possibly followed by `, but …`. |
| `Media blocks : N readable , M writable , L overall` | Block counts of 2048 bytes; free bytes = M × 2048. (`occupied`/`unused` replace `readable`/`writable` for some drive roles.) |

### Create an ISO image

`xorriso <prefix> -local_charset UTF-8 -outdev stdio:<target> -volid <label> -joliet on -pathspecs on -path_list <file> -commit -end`

| Argument | Why |
|---|---|
| `-local_charset UTF-8` | The path list is written in UTF-8; this stops xorriso from assuming the locale's character set (verified with accented and Devanagari names). |
| `-outdev stdio:<target>` | Write to a file instead of a drive. The target must not exist yet; we delete a stale one first. |
| `-volid <label>` | Volume label (xorriso accepts up to 32 characters and warns about non-ECMA-119 characters). |
| `-joliet on` | Joliet names so Windows shows long names. Rock Ridge is on by default and the manual strongly advises keeping it. |
| `-pathspecs on` | Read `iso_path=disk_path` pairs. In this mode `=` is escaped as `\=` in the disc path only, and `\` is **not** escaped. |
| `-path_list <file>` | Read the pairs from a file, one per line — no command-line length limit for big compilations. |
| `-commit -end` | Write the image and exit. |

Verified behaviour: names with spaces, `=` and `\` round-trip exactly, and
the extracted tree matches the source byte for byte. Because the list is
line-based, disc paths and source paths containing newlines are rejected
before xorriso runs.

Progress lines (from `write_run.c`, examples captured from a 3 GB image):
`xorriso : UPDATE : Writing:      17888s    1.2%   fifo  93%  buf  50%   65.5xD`
— sectors written, percent, fifo and drive buffer fill, and speed as a
multiple of 1x CD (`C`), DVD (`D`) or BD (`B`). The speed part appears only
after the first interval.

Success ends with `Writing to '<target>' completed successfully.` and exit 0.
Failures print `xorriso : FAILURE : <reason>` (or `libburn : SORRY : …`) and
exit 5 when aborted.

## 3. Hot-plug and USB detection

- Signature: the sorted names in `/sys/block` starting with `sr`. Reading a
  directory does not touch the drives, so polling every 2 seconds is harmless.
  When the signature changes, `xorriso -devices` is run for details. udev
  events were considered but need native bindings; polling meets the
  "within about 3 seconds" target with less code.
- Connection type: `/sys/block/srN` is a symbolic link into `/sys/devices/…`,
  which mirrors the bus topology. If the resolved path contains a segment
  `usb<number>` the drive is on USB; if it resolves without one the drive is
  `OTHER`; if it cannot be resolved it is `UNKNOWN`. *To be confirmed with the
  owner's USB writer and an internal drive; fixtures will be added then.*

## 4. Sleep inhibition

`systemd-inhibit --what=sleep:idle --who=RetroBurn --why=<reason> --mode=block cat`

`systemd-inhibit` holds the lock while its command runs. The command is `cat`
reading the pipe RetroBurn keeps open; closing the pipe ends `cat` and
releases the lock. If RetroBurn crashes, the pipe closes too, so a lock can
never leak. Verified in Ubuntu 22.04 (WSL): the lock appears in
`systemd-inhibit --list` while held and disappears after the pipe closes.

If the process ends within one second (logind unavailable, or refused — e.g.
`Failed to inhibit: Access denied` outside a local desktop session),
`SleepInhibitionUnavailableException` is thrown and the UI warns the user.

## 5. Power status

From the kernel's `sysfs-class-power` ABI: each entry in
`/sys/class/power_supply` has `type` (`Battery`, `UPS`, `Mains`, `USB`,
`Wireless`); mains supplies have `online` (`0` offline, `1`/`2` online);
batteries have `status` (`Unknown`, `Charging`, `Discharging`,
`Not charging`, `Full`) and `scope` (`System`, `Device`, `Unknown`).

Rule: any online `Mains` supply → `MAINS`; otherwise any system battery (scope
not `Device`, which marks peripherals such as a wireless mouse) that is
`Discharging` → `BATTERY`; otherwise `UNKNOWN`.

## 6. How to change this safely

- Every new xorriso argument goes through `XorrisoCommandBuilder` with a
  comment and a link, after checking the manual for the installed version.
- Parsers are tested against files in `tool-output/xorriso/`; add new captures
  rather than editing old ones.
- `XorrisoIntegrationTest` builds real images (a 100 MB tree with awkward
  names, compared file by file after extraction), and checks replacing an
  existing image, failure messages and cancellation. It needs xorriso and runs
  only on Linux; CI installs it.
