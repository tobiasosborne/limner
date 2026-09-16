# limner plan — from the 2026-09-15 review to a codebase worth the attention

This document is temporary. Once the decisions below are made, turn every
phase item into a beads issue (epic per phase) and delete this file's
"Phases" section in favour of `bd ready`. Findings IDs (A1, B3, …) refer to
`REVIEW.md`.

## Guiding idea

Most defects share a few root causes (REVIEW §Summary). So the work is not
"fix 40 bugs". It has three parts:

1. Be honest about the current state now.
2. Build one correct foundation for text: escape sequences, grapheme
   clusters, display width, and styled spans, all against pinned Unicode
   data.
3. Move each component onto that foundation, test-first, using the probe
   list as the first red tests.

Issue #2 gets its real fix in step 2. Nothing is released until it is
verified on both runtimes.

## Decisions needed from the owner

**Status (2026-09-15):** the owner accepted all recommendations below
("sounds all good"). D1–D7 are decided as recommended. Outreach (D8) still
needs the owner to approve the exact text immediately before anything is
posted (CLAUDE.md Rule 14).

**Delegation (owner directive, 2026-09-15):** the owner has no strong
preference on design decisions and delegates them to the orchestrator:
"make the respectful, experienced senior engineer call". New decisions are
made by the orchestrator and recorded in an ADR or the relevant bead. The
one exception is public text: the owner sees the exact text of every GitHub
comment, issue or PR before it is posted.

**Execution policy (owner directive, 2026-09-15):** work is orchestrated
serially, one task at a time. Each task is implemented by a Codex
`gpt-5.6-sol` subagent at `xhigh` reasoning effort; no parallel subagents.
The orchestrator verifies each task (both test suites, the diff, CLAUDE.md
compliance) before committing it.
Amended 2026-09-15 (owner): related beads are batched into one Codex run.
Within a run the worker commits locally once per bead after `bb check` passes
(never pushes); the orchestrator reviews the run's commit range afterwards.

**D1. Runtimes.** Keep babashka as a first-class runtime next to the JVM?
- **Recommend: yes.** It is the library's distinctive niche. It rules out
  hard Java dependencies (such as ICU4J) and a Java markdown parser, and it
  means Unicode segmentation should be implemented in Clojure rather than
  borrowed from the JDK (D2).
- Declared minimums: JDK 17+ and babashka ≥ 1.12. Pick the exact versions
  after checking what the test matrix can actually run.

**D2. How limner gets Unicode right.** Options:
- **(a)** Generate tables from a pinned UCD version and implement UAX #29
  grapheme segmentation in portable Clojure, verified against the official
  test file.
- **(b)** Keep `BreakIterator` and fix only the width tables.
- **(c)** Depend on clj-wcwidth.
- **(d)** Use ICU4J when present, as pmonks suggests.

**Recommend (a)**, with ICU4J used only in the test suite as a
cross-check oracle.
- Why not (b): the JDK fails GB9c and differs between JDKs (REVIEW C3).
- Why not (c): today clj-wcwidth has the U+2600–27BF bug, and without
  ICU4J on the classpath it has the same JDK segmentation dependence.
  Offer the generated data upstream instead (see D8).
- Why not (d) as the main path: it cannot run in babashka, so the two
  runtimes would disagree.
- What (a) gives: deterministic results in every runtime, and a data
  generator that is reusable and could be shared with clj-wcwidth.

**D3. Unicode version and ambiguous width.**
- **Recommend:** pin UCD 17.0.0. Treat East Asian Ambiguous as width 1 by
  default, with an explicit option for 2.
- Record in an ADR that terminals lag the standard (glibc still reports
  1 for the Yijing symbols), and expose the pinned version as data
  (`limner.unicode/version`).

**D4. Scope.** 16 namespaces are too many to make excellent at once.
- **Recommend a core:** `text` (new), `ansi` (new), `unicode` (new),
  `core` (colour), `borders`, `layout`, `table` (promoted out of the
  `limner` namespace), `terminal`, and the components `panel`, `list`,
  `input`, `progress` and `statusbar`.
- **Label experimental** until reworked: `render`, `events`, `state`,
  `streaming`, `syntax`, `markdown`. Say so in their namespace docstrings
  and in the README.
- **Deprecate** the `limner` top-level namespace.
- Alternative: cut the experimental namespaces entirely. That is cleaner,
  but it deletes working-ish code before there is a replacement.

**D5. Versioning and API stability.**
- **Recommend:** relabel as `0.x`, pre-release, with the API allowed to
  change until 1.0.
- Start `CHANGELOG.md`, and tag `v0.1.0` at the current commit as the
  "legacy" baseline so git-dep users have a stable coordinate.
- Tag `v0.2.0` after Phase 1.
- Consider Clojars publishing, as pmonks suggested on #1, once Phase 3
  lands.

**D6. Styled text model.**
- **Recommend:** a small data model, a vector of spans
  `{:text "…" :style {:fg … :bg … :bold true …}}`.
- Existing ANSI strings get parsed into it.
- Width, slicing, padding and truncation work on it.
- Rendering to a string emits minimal SGR transitions, never a blanket
  reset inside nested styles.
- Keep `core/color` returning a string for compatibility, implemented on
  top of spans.
- This fixes REVIEW B1–B4 as a class.

**D7. Terminal input.** Provide a real terminal I/O namespace (`limner.tty`)
or declare input out of scope?
- **Recommend:** provide a small POSIX-only `limner.tty` covering:
  - raw mode via `stty` with guaranteed restore;
  - a reader with an ESC timeout;
  - the xterm key table (including Shift-Tab);
  - SGR mouse and bracketed paste.
- Windows is explicitly unsupported until someone needs it.
- Without this, the event and input components cannot be used as
  documented.

**D8. Outreach (owner approves every word, CLAUDE.md Rule 14).**
- **Recommend:** reply on #2 now with the evidence (draft below).
- File the clj-wcwidth issue pmonks asked for (draft below).
- Later, offer the UCD table generator as a PR to clj-wcwidth.

## Work tracking

The former phase plan is tracked in beads: run `bd list` for all work and
`bd ready` for the next unblocked item.

## Outreach status

- The reply on limner#2 was approved by the owner and posted on 2026-09-16:
  https://github.com/tobiasosborne/limner/issues/2#issuecomment-5694586613
- The clj-wcwidth issue below is drafted and **not posted**; it needs the
  owner's approval of the exact text (CLAUDE.md Rule 14).

### Issue on pmonks/clj-wcwidth

**Title:** `wide?` treats all of U+2600–U+27BF as wide; per UAX #11 most of the block is narrow

In `src/wcwidth/api.clj` (at d0d892d, line 211) `wide?` includes `(and (>= cp 0x2600) (<= cp 0x27BF))`, so every code point in Miscellaneous Symbols and Dingbats gets width 2.

In UCD 17.0.0 `EastAsianWidth.txt` those 448 code points are 308 N, 80 A and 60 W. Only the 60 W are wide: for example ☔ U+2614, ☕ U+2615, ⚡ U+26A1, ✅ U+2705, ❌ U+274C, ✨ U+2728. Common symbols such as ✓ U+2713, ✗ U+2717, ⚠ U+26A0, ❤ U+2764 and ☀ U+2600 are N, and glibc `wcwidth` returns 1 for them (table in tobiasosborne/limner#2). Note that ⚠ and ❤ become width 2 only as emoji presentation sequences with VS16 (U+FE0F).

The 60 W code points in the block, from that file, are: U+2614–2615, 2630–2637, 2648–2653, 267F, 268A–268F, 2693, 26A1, 26AA–26AB, 26BD–26BE, 26C4–26C5, 26CE, 26D4, 26EA, 26F2–26F3, 26F5, 26FA, 26FD, 2705, 270A–270B, 2728, 274C, 274E, 2753–2755, 2757, 2795–2797, 27B0, 27BF. Of these, U+2630–2637 and U+268A–268F became W in Unicode 16, so glibc (15.1 data) still reports 1 for them.

If it is useful, the table generator limner uses to derive width from a pinned UCD version is in `scripts/gen-unicode-tables.clj`; I'd be happy to adapt it into a PR here.
