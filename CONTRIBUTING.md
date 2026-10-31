<!--
SPDX-FileCopyrightText: 2026 Chetan and contributors
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Contributing to RetroBurn

Thanks for helping. This guide covers how to set up, how we write code, commits
and pull requests, and what reviewers check.

## Getting started

1. Follow [docs/DEVELOPER_GUIDE.md](docs/DEVELOPER_GUIDE.md) to build and run
   the app on Linux or Windows.
2. Read [docs/architecture/HLD.md](docs/architecture/HLD.md) and the LLD of the
   module you want to change (`docs/design/`).
3. Install the repository's git hooks once:
   `git config core.hooksPath tools/git-hooks`.

## Ground rules

- **Docs change with the code.** A pull request that changes behaviour updates
  the relevant LLD/HLD/ADR in the same pull request.
- **Decisions get an ADR** in `docs/adr/` (see the template there).
- **New dependencies, bundled programs, fonts, images or sounds** must be
  GPL-3.0-compatible and registered *before* use: a row in
  `THIRD_PARTY_NOTICES.md`, an entry in `src/main/resources/credits/components.json`
  and the licence text under `src/main/resources/credits/licenses/`. The build
  fails if a runtime dependency is not registered.
- **No third-party media or logos** in the repository — not even in tests or
  screenshots. Test media is generated synthetically.
- **External tools:** never guess command-line options or output formats.
  Check the tool's documentation, capture real output as a test fixture, and
  note what you verified in the module's LLD.
- **No circumvention of DRM or copy protection**, in any form.
- Only `platform.<os>` packages may contain OS-specific code. The architecture
  tests enforce this.

## Code style

- Formatting is automatic: run `./mvnw spotless:apply` before committing.
- Every source file starts with the two-line SPDX header:
  ```java
  // SPDX-FileCopyrightText: 2026 Chetan and contributors
  // SPDX-License-Identifier: GPL-3.0-or-later
  ```
  Use the comment syntax of the file type. Files that cannot hold comments are
  listed in `REUSE.toml`.
- Names say what things are, in full words. Domain terms (SAO, UDF, CD-Text)
  are defined in [docs/GLOSSARY.md](docs/GLOSSARY.md).
- Keep units small: classes around 300 lines, methods around 40 lines at most.
  If you need more, explain why in a comment.
- Boring, explicit code: constructor injection wired in `AppContext`, no
  dependency-injection framework, no reflection tricks, no clever one-liners.
- Every package has a `package-info.java`; every public class and method has
  Javadoc covering purpose, parameters, errors and threading where relevant.
- Errors are typed and carry a user-facing message plus technical detail.
- Test names read as sentences: `rejectsAudioCdWhenTotalExceedsEightyMinutes()`.

### Comments

- Explain **why**, not what the next line does.
- Every external-tool argument and every workaround (drive quirks, player
  compatibility) gets a "why" comment with a link to its source.
- No comments about the editing process ("added this", "fixed bug"). History
  belongs in git.
- No commented-out code, no decorative banners, no emoji.
- `TODO` and `FIXME` only with an issue: `// TODO(#42): support gapless audio`.
- Update or remove comments when the code they describe changes.

## Commits

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
type(scope): summary in the imperative, at most 72 characters

Body wrapped at 72 columns: what changed and why, trade-offs, follow-ups,
and how it was tested.

Fixes #12
Signed-off-by: Your Name <you@example.org>
```

- Types: `feat`, `fix`, `docs`, `test`, `refactor`, `build`, `ci`, `chore`.
- Scope is the area touched: `audio`, `linux`, `windows`, `credits`, `ui`, …
- Subject in the imperative mood ("add", not "added"), no trailing period.
- **One logical change per commit.** Code, its tests and its docs go together;
  unrelated changes go in separate commits.
- **Sign off every commit** (`git commit -s`) to certify the
  [Developer Certificate of Origin](https://developercertificate.org/).
- Avoid vague messages ("update files", "WIP"), lists of touched files, emoji
  and promotional language.

## Pull requests

- Title in the same style as a commit subject.
- Description: a short summary, why, and how you tested it (OS, drive and
  media where relevant). Add screenshots for UI changes.
- Complete the checklist in the pull request template honestly.
- CI must pass on both Linux and Windows before merge.

## Review checklist

Reviewers check:

- [ ] Behaviour matches the LLD; docs, ADRs and CHANGELOG updated where needed.
- [ ] Names are clear; classes and methods small; one responsibility each.
- [ ] Comments explain why; no narration, commented-out code or bare TODOs.
- [ ] Public API has accurate Javadoc; new packages have `package-info.java`.
- [ ] No work on the JavaFX Application Thread that could block (I/O,
      processes, rendering).
- [ ] External processes use argument lists, bounded timeouts and the tools
      package; their options are verified and documented.
- [ ] Generated files (XML, sheets, TOC, playlists) escape user data.
- [ ] Paths use `java.nio.file.Path`; names with spaces, Unicode and Windows
      reserved words are handled.
- [ ] User-visible text is in `messages.properties`.
- [ ] Tests cover the change, including error cases; names read as sentences.
- [ ] New dependencies or assets are licence-compatible and registered.
- [ ] Works on both Linux and Windows (CI green on both).
- [ ] Commits follow the commit rules and are signed off.
