<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# 0011 — Jackson 3 for JSON

- Status: Accepted
- Date: 2026-10-31
- Deciders: project owner

## Context and problem statement

Jackson is the chosen JSON library. Two major lines are maintained: 2.x
(`com.fasterxml.jackson.*`) and 3.x (`tools.jackson.*`, released October 2025).
Which one?

## Considered options

1. Jackson 3.x
2. Jackson 2.x

## Decision outcome

Chosen option: "Jackson 3.x", because the project starts after its release and
would otherwise face a migration later. Its API is close to 2.x, it supports
records directly, it throws unchecked exceptions, and its jars are proper JPMS
modules. `jackson-annotations` keeps the `com.fasterxml.jackson.annotation`
package in 3.x.

### Consequences

- Good: no future major-version migration; simpler exception handling.
- Bad: many online examples still show 2.x package names; the developer guide
  notes the difference.
- Licence: Apache-2.0, registered in `THIRD_PARTY_NOTICES.md` and
  `components.json`.
