<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Changelog

All notable changes to RetroBurn are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Architecture and design documentation: solution architecture, high-level
  design, decision records, threat model, risk register, glossary, UX
  specification and extensibility guide.
- Application skeleton: JavaFX window with the home screen (project types not
  available yet), Help → About and Help → Open-source licenses.
- Third-party component register with bundled licence texts, and a build check
  that every runtime dependency is credited.
- Build checks for formatting, licence headers, architecture rules and test
  coverage; CI on Linux and Windows; REUSE compliance.
- Core services: safe external-process runner with timeouts and cancellation,
  tool detection and feature gating, job pipeline with weighted progress and
  work folders, settings file, rotating log files.
- Linux: drive and media detection and ISO image creation with GNU xorriso,
  drive hot-plug and USB detection, battery detection, and keeping the
  computer awake during burns with systemd-inhibit.
