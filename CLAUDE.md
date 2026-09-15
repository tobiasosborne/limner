# CLAUDE.md — guidance for AI agents working on limner

If you are an agent (Claude Code, an SDK harness, or a downstream tool)
landing in this repo, **read this top to bottom every session**. After a
context compression, re-read. The rules drift out of working memory faster
than you think; that's why they're numbered.

The discipline here is inherited from the owner's other repos
(`scientist-workbench`, `PadeTaylor.jl`, `Bennett.jl`), specialised for a
Clojure/babashka terminal-UI library.

## What limner is, honestly

A Clojure + babashka library for terminal output: styled text, Unicode
display width, borders, layout, and a set of components (panel, list,
input, progress, statusbar, markdown, syntax highlighting, streaming) plus
an experimental render loop and event system.

It was written in late 2025 by an earlier generation of coding agents and
**over-claims badly** (the README calls it "production-ready v1.0"). It
has since attracted real outside attention (GitHub issues #1 and #2 from an
experienced Clojure developer). The job now is to turn it into a codebase
that deserves that attention. The full audit is
`reviews/2026-09-15/REVIEW.md`; the working plan is
`reviews/2026-09-15/PLAN.md` (to be converted into beads issues).
Treat every claim in `README.md` and active `docs/` as unverified until you
have checked it against the code. Documents under `docs/archive/2025/` are
historical and explicitly do not describe the current system.

## The Laws

Two laws. Read first, applied always.

**Law 1 — Ground truth before code.** Before writing any code: open the
affected files (verify their current shape — never trust memory, a prior
conversation summary, or a doc), and open the canonical reference. For
limner the canonical references are *data and specifications*, not
intuition:

- Unicode Character Database files, **at a pinned version**, vendored
  under `reference/unicode/<version>/` (`EastAsianWidth.txt`,
  `emoji/emoji-data.txt`, `auxiliary/GraphemeBreakProperty.txt`,
  `auxiliary/GraphemeBreakTest.txt`, `DerivedCoreProperties.txt`,
  `emoji/emoji-test.txt`), plus UAX #11, UAX #29 and UTS #51;
- ECMA-48 for escape-sequence grammar (CSI, OSC, string terminators) and
  the xterm control-sequences document for key, mouse and SGR sequences;
- the CommonMark spec and its example set, if and only if markdown stays
  in scope.

Cite the local source by path in commits and ADRs
(`reference/unicode/17.0.0/EastAsianWidth.txt`, not "Unicode says"). If
the reference you need is not vendored yet, vendor it (with its SHA-256)
before writing the code.

**Law 2 — Docs in lockstep with code.** Every code change ships its doc
updates *in the same edit session*:

- public function added or changed: docstring, and the README / `docs/`
  entry if one mentions it;
- a design decision (width semantics, error policy, runtime support,
  API break): an ADR under `docs/adr/NNNN-title.md`;
- behaviour visible to users: a line in `CHANGELOG.md`;
- a doc example: it must run. Code in docs that calls functions which do
  not exist is a bug (the 2026-09-15 audit found 24 such references).

Code shipped with stale docs is incomplete work, full stop.

## The Rules

Numbered, non-negotiable. Re-read after compaction.

0. **Laws 1 & 2 apply.** Ground truth before code. Docs in lockstep.

1. **Fail fast, fail loud.** Throw `ex-info` with a message and a data map
   that says what was wrong and what to do about it. Do **not** catch
   exceptions to print a warning to stderr and return a fabricated value
   (a fake `"+-+"` box, an empty layout, width `0`, an uncoloured string).
   That pattern is all over the legacy code and is being removed; never add
   more of it. A library that prints to stderr in the middle of someone's
   TUI is broken twice. The single allowed error boundary is an explicit,
   opt-in one at the top of a render loop, and it reports the error, it
   does not hide it.

2. **All bugs are deep.** No band-aids. A misaligned box is almost never
   "off by one": it is width measured in UTF-16 code units, or a style reset
   that clobbers an outer style, or an escape sequence counted as text.
   Find the class of bug, fix the class, and add the test that would have
   caught the whole class.

3. **Skepticism.** Verify subagent output, previous-session claims, doc
   claims, and your own memory against the repo. `git log`, `Read`, and a
   REPL are authoritative. "The tests pass" is only evidence if the tests
   assert exact output (see Rule 5).

4. **Red-green TDD, two valid shapes.**
   - **Spec-from-scratch:** write the failing test, watch it fail for the
     right reason, write the minimum code, watch it pass, refactor.
   - **Port-and-verify** (for algorithms defined by a spec or data file,
     such as UAX #29 segmentation, width tables, and ECMA-48 parsing):
     implement against the spec, drive it with the official conformance
     data (`GraphemeBreakTest.txt`, `emoji-test.txt`), **mutation-prove**
     the tests catch regressions (perturb the implementation, confirm RED,
     restore), and cross-validate against an independent oracle when one
     exists (glibc `wcwidth`, Python `wcwidth`, ICU4J).
   The discipline is "the tests have caught a real regression", not "the
   test was committed first". Bug fixes always start with a failing test
   that reproduces the bug.

5. **"Runs without errors" is not a passing test.** Assert exact values.
   Rendered output is compared as an exact string (escape sequences
   included, made visible in failure messages), not with
   `clojure.string/includes?`. The legacy `test-color-nesting` passes while
   the behaviour it names is broken; that is the failure mode to avoid.
   Invariants that hold for all inputs (every line of a box has the same
   display width; clipping never exceeds the requested width) belong in
   `test.check` property tests.

6. **Two runtimes, one behaviour.** limner supports the JVM (Clojure CLI)
   and babashka. Every change passes the suite on **both** before it is
   done. No hard dependency on a Java library babashka does not ship. Any
   optional JVM-only capability (for example ICU4J) is loaded explicitly
   via `requiring-resolve`, is selected by documented, testable
   configuration, and is tested in both states. That is capability
   selection, not a silent fallback, and it must never change results
   without the user being able to see why.

7. **Pure core, effects at the edge.** Styling, measuring, layout and
   component rendering are pure functions from data to data/strings. Only
   the terminal I/O layer and the render loop write to the terminal, and
   their output writer, clock and terminal size are injectable so tests
   never touch the real terminal, never shell out, and never sleep. Never
   perform I/O inside a `swap!` function (it can be retried); never use
   blocking core.async operations (`<!!`, `>!!`, `alts!!`) inside a `go`
   block.

8. **Text is grapheme clusters, measured in terminal columns.** Never
   measure or cut user-visible text with `count`, `subs`, `nth`, or
   `format "%Ns"`: those work on UTF-16 code units and split surrogate
   pairs, emoji sequences and wide characters. Use the text layer's
   width/slice/pad/truncate functions, which understand escape sequences,
   grapheme clusters and display width.

9. **Senior-engineer-grade only.** No "good enough for now". If a corner
   is deliberately deferred, file a bead that states the exact condition
   that would force the work, and say so in the docstring.

10. **Literate programming.** Each namespace opens with a docstring that
    reads like a short chapter: what the namespace is for, what ground truth
    it embodies, which pitfalls motivated its defensive checks, and which
    references it derives from. Comments explain *why*. Delete comments that
    restate the code, and never write marketing prose ("100% bulletproof",
    "production-ready") in source or docs.

11. **Small namespaces.** Aim for ≤ 200 lines of code per file, excluding
    blank lines and docstrings. Several legacy files are 400–750 lines;
    split them when you touch them, along real seams.

12. **Beads is the only persistent tracker.** `bd ready` at session start,
    `bd update <id> --claim` before work, `bd close <id>` when done. Never add
    planning documents; the old ones under `docs/archive/2025/` are historical.
    Anything that should outlive the conversation is a bead. Never run `bd
    edit` (it opens `$EDITOR` and blocks). `bd prime` prints the full workflow.

    The local Dolt database is git-ignored. Cross-device state travels only
    through the tracked `.beads/issues.jsonl`: `.githooks/pre-commit` exports
    and stages it, while `.githooks/post-merge` imports it. On a fresh clone,
    run `sh scripts/setup-device.sh` once to set `core.hooksPath` and run the
    non-destructive `bd bootstrap --yes`. The setup script also removes any
    Dolt `origin` that beads inferred automatically, so tracker sync cannot
    push behind the owner's back. Never run `bd init --force`; it can discard
    issue state.

13. **No GitHub CI.** Quality gates run locally. Do not create
    `.github/workflows/` and do not file "add CI" beads; the owner rejects
    automated CI across all repos.

14. **The public face is the owner's voice.** People outside the project
    now read this repo and its issues. Never post, edit or close anything on
    GitHub (issues, comments, PRs, on this repo or on anyone else's, for
    example `pmonks/clj-wcwidth`) without the owner approving the exact
    text. Draft it and hand it over. When a fix comes from someone's report,
    credit them in the commit message (`Reported-by:`) and in `CHANGELOG.md`.
    Replies carry evidence (data, test output, the reference consulted), not
    reassurance.

15. **Repeat rules.** Re-read this file at session start, after `/clear`,
    and after any compaction.

## Hallucination-risk callouts

Mistakes that look right and are wrong. Stop and check when you are about
to make one.

- **Unicode width is versioned, and there is no single "true" answer.**
  Unicode 16 changed the Yijing trigrams and digrams (U+2630–2637,
  U+268A–268F) from narrow to wide. glibc on a typical Linux box still
  reports 1 for them (see pmonks's `wcwidth` table on issue #2), while UCD
  17.0 says W. Always state which Unicode version a width claim is against,
  and never "correct" a table from memory.
- **U+2600–U+27BF is a heterogeneous mix.** In UCD 17.0 the block has 308
  N, 80 A and 60 W code points. ✓ ✗ ⚠ ❤ ☀ are 1; ☔ ☕ ⚡ ✅ ❌ ✨ are 2.
  Neither "the block is wide" nor "the block is narrow" is right; generate
  from `EastAsianWidth.txt`.
- **East Asian Ambiguous (A) is not 2.** In non-CJK contexts terminals
  render A as 1. If CJK-legacy width is ever supported, it is an explicit
  option.
- **`java.text.BreakIterator` is not a conforming UAX #29 implementation.**
  On JDK 21 it passes 749 of 766 `GraphemeBreakTest-17.0.0` cases. All
  emoji cases pass; 16 of the 17 failures are Indic conjunct rule GB9c
  (Unicode 15.1) and one is a ZWJ case. Behaviour also varies with the JDK
  version, which differs between a user's JVM and their babashka build. Do
  not describe it as "following UAX #29".
- **A JVM `char` is a UTF-16 code unit, not a character.** `(count "😀")` is
  2. `(subs s 0 n)` can cut an emoji in half and emit a lone surrogate.
- **`ESC[0m` resets everything.** Wrapping text in `style + text + ESC[0m`
  destroys any enclosing style: `(color :bold (str (color :red "R") " x"))`
  leaves " x" unbolded. Correct composition needs a style model (spans), not
  string concatenation.
- **Not every escape sequence ends in `m`.** CSI sequences such as
  `ESC[2K`, and OSC sequences such as OSC 8 hyperlinks (terminated by BEL or
  `ESC \`), are zero-width too. A regex for `ESC\[[0-9;]*m` is not an ANSI
  parser. There must be one tokenizer, used everywhere (the audit found
  that regex seven times across four namespaces).
- **Default-ignorable and format characters are width 0** (U+00AD soft
  hyphen, U+2060 word joiner, U+FEFF). Emoji outside the pictograph blocks
  (U+1F170 🅰 is A, U+1F321 🌡 is N) are **not** automatically 2 without
  VS16.
- **Shift-Tab is `ESC [ Z`.** Many keys arrive as multi-byte sequences, and
  a bare `ESC` is ambiguous with the start of a sequence. Key parsing needs
  a timeout-aware reader, not a lookup on one string.
- **Historical docs describe functions that do not exist.** The archived
  `docs/archive/2025/tutorial.md` calls 20 missing functions. Check a function
  exists (`ns-publics`) before citing or using it in active documentation.
- **`limner` (single-segment namespace) is legacy.** It duplicates
  `limner.syntax`, `limner.borders` and adds a `table`. Do not build on it.

## Practical guidance

- **Runtimes:** JDK 21 and babashka 1.12.x are what this machine has.
  Declared minimums are an open decision (PLAN.md, D1); do not assume JDK 20+
  semantics anywhere without checking.
- **Tests (JVM):** `clojure -M:test`, or `clojure -M:test -n limner.core-test`
  for one namespace.
- **Tests (babashka):** `bb test`, or `bb test -n limner.core-test` for one
  namespace. Both runtime commands discover every `*_test.clj` namespace under
  `test/`; the benchmarks namespace is not part of either suite.
- **All local gates:** `bb check` runs the JVM suite, the babashka suite, then
  `clj-kondo --lint src test` when `clj-kondo` is available, serially and
  fail-fast. A missing optional linter is reported explicitly.
- **Changelog:** every user-visible change adds a line under `[Unreleased]`.
  Credit reporters as `(#N, reported by @handle)`.
- **README examples:** every `clojure` or `clj` fence is executed in isolation.
  A block that cannot safely run in a test needs
  `<!-- not-executed: reason -->` immediately before its opening fence. The
  reason must name the concrete side effect or blocking behaviour.
- **Babashka tasks:** `bb tasks` lists them; the benchmarks are
  `bb benchmark[:suite]`.
- **Reflection:** loading every namespace with `*warn-on-reflection*` on
  printed 18 warnings on 2026-09-15. Do not add new ones.
- **Evidence scripts** for audit claims live under `reviews/<date>/evidence/`.
  Re-run them rather than quoting their numbers.
- **Machine limits:** this is WSL and has frozen from runaway processes.
  Wrap long runs in `timeout`, and never run several JVM test suites in
  parallel.

## Session close

1. `bd close <id1> <id2> ...` for finished work; file beads for anything
   left over.
2. Run the quality gates on both runtimes for any code change.
3. `bd export -o .beads/issues.jsonl` and commit it **with** the code. The
   tracked pre-commit hook repeats that export and stages the JSONL as a
   safety net. The local Dolt database is git-ignored; the exported JSONL is
   the only way tracker state reaches another checkout. The post-merge hook
   imports incoming state, and fresh clones run `sh scripts/setup-device.sh`.
4. Commit with messages that cite the reference or ADR involved.
5. Pushing is the owner's call: say the work is committed and ready, and
   let them push unless they have told you otherwise.

## Tool of last resort

If the Laws conflict with a fast path, choose the Laws.
