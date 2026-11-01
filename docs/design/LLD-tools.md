<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# LLD — External tools (`tools`)

| | |
|---|---|
| Status | Implemented in the core milestone (M2) |
| Package | `io.aerohelper.retroburn.tools` |
| Related | [ADR-0004](../adr/0004-external-tools-as-separate-processes.md), [HLD §9](../architecture/HLD.md) |

## 1. Purpose

Run external programs safely, find them on disk, learn their versions and
capabilities, and decide which RetroBurn features are available. This is the
**only** package that starts processes (enforced by `ArchitectureTest`).

## 2. Classes

| Class | Responsibility |
|---|---|
| `ExternalTool` (enum) | Every program RetroBurn depends on: executable name, display name, arguments that print the version, and the pattern that extracts it. |
| `ProcessRequest` (record + builder) | What to run: executable, argument list, working folder, timeouts, line consumers or a raw stdout sink, output charset. Never a shell string. |
| `ProcessRunner` | `run(request, cancelToken)` blocks until the process ends and returns a `ProcessResult`; `startLongRunning(request)` returns a `RunningProcess` for long-lived helpers whose stdin stays open. |
| `ProcessResult` (record) | Exit code, the command line as text, the last 200 output lines (for "Show details"), whether it timed out. |
| `RunningProcess` | Handle on a long-lived process: close its stdin, check if alive, wait, destroy the process tree. `AutoCloseable`. |
| `ToolLocator` (interface) | `Optional<Path> locate(ExternalTool)`. |
| `PathSearchToolLocator` | Looks for the executable in a list of folders with a list of file extensions, both supplied by the platform (PATH folders and no extension on Linux; the bundled folder and `.exe` on Windows). `locateExecutable(name)` also finds OS helpers such as `systemd-inhibit`. |
| `OverridingToolLocator` | User-configured paths from settings first (read on every lookup, so changes apply at once; a path that no longer exists is ignored), then a fallback locator. |
| `ToolStatus` (record) | Tool, state (`FOUND`, `MISSING`, `NOT_WORKING`), path, version. |
| `ToolProbe` | Locates a tool and runs its version command (10 s timeout); lists FFmpeg's encoders by collecting every output line, because the list is longer than a result's output tail. |
| `FfmpegEncoderListParser` | Parses `ffmpeg -hide_banner -encoders` into encoder names. |
| `ToolReport` (record) | Statuses of all tools plus the FFmpeg encoder names. |
| `Feature` (enum) | User-visible capabilities that depend on tools: burning, ISO creation, verify, audio CD, MP3 transcoding, video DVD, video DVD image, burn reel. |
| `Requirement` (sealed) | `ToolPresent(tool)` or `FfmpegEncoderPresent(name)`. |
| `FeatureGate` | Given a `ToolReport` and the platform's requirement table, returns a `FeatureAvailability` per feature with the reasons it is unavailable. |

## 3. Process rules

```mermaid
sequenceDiagram
    participant Caller
    participant Runner as ProcessRunner
    participant P as Child process
    Caller->>Runner: run(request, cancel)
    Runner->>P: ProcessBuilder(argument list).start()
    par stdout
        P-->>Runner: lines -> consumer + log + tail
    and stderr
        P-->>Runner: lines -> consumer + log + tail
    end
    loop every 100 ms
        Runner->>Runner: cancelled? idle timeout? total timeout?
    end
    alt cancelled or timed out
        Runner->>P: destroy descendants and process; force after 3 s
    end
    Runner-->>Caller: ProcessResult (or OperationCancelledException)
```

- **Argument lists only.** `ProcessBuilder` gets the executable path and a
  `List<String>`. No `sh -c`, `cmd /c` or `powershell -Command`.
- **Both streams are drained** on their own virtual threads, so a chatty tool
  can never block on a full pipe. A line consumer that throws does not stop the
  draining; its first exception is rethrown after the process ends.
- **Lines** end at `\n` or `\r`; tools such as FFmpeg redraw progress with
  `\r`.
- **Raw stdout:** `stdoutTo(outputStream)` copies stdout unchanged into the
  stream instead of splitting it into lines, for tools that write data there
  (xorriso reading a disc back for verification). Every chunk counts as
  output for the idle timeout; the data is not part of the output tail. An
  `IOException` from the stream is kept and rethrown like a consumer's
  exception after the process ends; the rest of stdout is drained and
  discarded meanwhile, so the tool never blocks.
- **Timeouts:** `totalTimeout` bounds the whole run; `idleTimeout` fails the
  run when no output arrives for that long (catches a vanished device). Either
  may be absent.
- **Cancellation:** the `CancelToken` is checked every 100 ms; cancel destroys
  descendants then the process, and forcibly after 3 seconds. The caller gets
  `OperationCancelledException` — within the 5-second cancel target.
- **Logging:** the full command line, every output line (at `FINE`) and the
  exit code are logged through `java.util.logging`.
- **Charset:** UTF-8 by default; the request can override it.

## 4. Version detection (verified 2026-10-31, Ubuntu 24.04)

Captured output is in `src/test/resources/tool-output/<tool>/`.

| Tool | Version command | First line seen | Pattern |
|---|---|---|---|
| xorriso 1.5.6 | `xorriso -version` | `xorriso 1.5.6 : RockRidge filesystem manipulator, libburnia project.` | `^xorriso (\S+) :` |
| cdrskin 1.5.6 | `cdrskin --version` | `cdrskin 1.5.6 : limited cdrecord compatibility wrapper for libburn` | `^cdrskin (\S+) :` |
| genisoimage 1.1.11 | `genisoimage --version` | `genisoimage 1.1.11 (Linux)` | `^genisoimage (\S+)` |
| FFmpeg 6.1.1 | `ffmpeg -version` | `ffmpeg version 6.1.1-3ubuntu5 Copyright (c) ...` | `^ffmpeg version (\S+)` |
| ffprobe 6.1.1 | `ffprobe -version` | `ffprobe version 6.1.1-3ubuntu5 Copyright (c) ...` | `^ffprobe version (\S+)` |
| dvdauthor 0.7.2 | `dvdauthor --help` | `DVDAuthor::dvdauthor, version 0.7.2.` | `^DVDAuthor::dvdauthor, version (\d[\d.]*\d)` |
| spumux 0.7.2 | `spumux --help` | `DVDAuthor::spumux, version 0.7.2.` | `^DVDAuthor::spumux, version (\d[\d.]*\d)` |

The dvdauthor tools have no dedicated version option; `--help` prints the
version on its first line. The exit code of a version command is ignored: a
tool counts as working when the pattern matches.

FFmpeg encoders: `ffmpeg -hide_banner -encoders` prints a legend, a
` ------` separator, then one line per encoder such as
` A....D libmp3lame           libmp3lame MP3 (MPEG audio layer 3) (codec mp3)`.
The second column is the encoder name.

## 5. Feature gating

The requirement table is supplied by the platform, because the burning
back-end differs per OS. The Linux table:

| Feature | Requirements |
|---|---|
| Burning, ISO creation, verify | xorriso |
| Audio CD | cdrskin, ffmpeg, ffprobe |
| MP3 transcoding | ffmpeg with the `libmp3lame` encoder |
| Video DVD | ffmpeg, ffprobe, dvdauthor, spumux |
| Video DVD image | everything for video DVD, plus genisoimage |
| Burn reel | ffmpeg |

Each unmet requirement becomes a `UserMessage` such as "Needs xorriso, which
was not found." The UI uses these to explain disabled features.

## 6. Error cases

| Case | Result |
|---|---|
| Executable not found | `ToolStatus` `MISSING`; running it anyway throws `ToolMissingException`. |
| Version pattern does not match | `NOT_WORKING`, with the first output line as technical detail. |
| Process cannot start (permissions, broken binary) | `ToolFailedException` with the OS error. |
| Idle or total timeout | `ProcessResult.timedOut()` is true; callers turn it into a typed error. |
| Cancel | `OperationCancelledException`. |

## 7. How to change this safely

- Add a tool: add the `ExternalTool` constant with a verified version command
  and pattern, capture its real output into `tool-output/<tool>/` and add a
  `ToolProbe` test using it. Register it in the credits when it ships.
- Never add a method that takes a single command string.
- Keep `ProcessRunner` free of tool-specific knowledge; parsing belongs in the
  callers' parsers.
- Run `ProcessRunnerTest`: it covers timeouts, cancellation, both streams and
  `\r` line endings with a small Java child process, so it runs on every OS.
