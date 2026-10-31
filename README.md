<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# RetroBurn

RetroBurn is a free, open-source desktop app for Windows and Linux that burns
CDs, DVDs and Blu-ray discs. It is written in Java with a JavaFX interface and
wraps proven burning technology — open-source tools on Linux and the Windows
Image Mastering API (IMAPI2) on Windows — in one friendly UI.

Planned features:

- Data discs, ISO images, blanking and verify-after-burn
- Audio mix CDs with CD-Text from your own music files
- MP3 discs laid out for car stereos and older players
- Video DVDs with menus that play on living-room DVD players
- A cover and label designer with print-ready PDF export
- Retro themes with a spinning-disc animation and sounds
- A one-tap 15-second vertical "burn reel" video of your burn

> **Status:** early development. The application skeleton runs; burning
> features are being built milestone by milestone. See
> [docs/architecture/SAD.md](docs/architecture/SAD.md) for the plan.

RetroBurn makes no network connections and collects no data.

## Building and running

You need JDK 25. Everything else is fetched by the Maven wrapper.

```sh
./mvnw verify        # Linux: build, test and run all checks
./mvnw javafx:run    # Linux: start the app
mvnw.cmd verify      # Windows
mvnw.cmd javafx:run  # Windows
```

The [developer guide](docs/DEVELOPER_GUIDE.md) has the full set-up for both
operating systems.

## Documentation

Start at [docs/README.md](docs/README.md): architecture, design decisions,
UX specification, glossary and the security threat model.

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) and
our [Code of Conduct](CODE_OF_CONDUCT.md). Security issues: see
[SECURITY.md](SECURITY.md).

## Licence

RetroBurn is free software, licensed under the
[GNU General Public License v3.0 or later](LICENSE). The repository follows the
[REUSE](https://reuse.software/) specification; every file states its licence.

## Acknowledgements

RetroBurn stands on the shoulders of other open-source projects. Each one is
credited in the app under Help → Open-source licenses and in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

- **OpenJDK** and **OpenJFX** — the Java runtime and user interface toolkit
  (GPL-2.0 with the Classpath Exception).
- **Jackson** — reads and writes RetroBurn's JSON files (Apache-2.0).

As burning features arrive, RetroBurn will also use GNU xorriso, cdrskin and
genisoimage on Linux, the Windows Image Mastering API on Windows, and FFmpeg,
dvdauthor and Apache PDFBox on both. They will be listed here when they are
added.
