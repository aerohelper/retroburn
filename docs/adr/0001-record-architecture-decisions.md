<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0001 — Record architecture decisions

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

RetroBurn spans two operating systems, several external tools and strict
licence obligations. Future maintainers need to know *why* the code is shaped
the way it is, without asking the original authors.

## Decision drivers

- Maintainers must be able to change a module safely after reading its docs.
- Decisions must be reviewable in pull requests like code.

## Considered options

1. Architecture Decision Records in the repository (MADR format)
2. A wiki page
3. No formal record; rely on commit messages

## Decision outcome

Chosen option: "ADRs in `docs/adr/`", because they are versioned with the code,
reviewed in the same pull requests and readable without extra tools.

Rules:

- One decision per file, `NNNN-short-title.md`, following
  [template.md](template.md).
- Any deviation from a documented default, any choice made to resolve an
  ambiguity, and any breaking change to an extension point gets an ADR.
- Verified facts about external tools and APIs (options, output formats) are
  recorded in an ADR or the relevant module LLD, with links to the source.

### Consequences

- Good: rationale survives contributor turnover.
- Bad: small overhead per decision.
