# limner codebase review — 2026-09-15

Frozen snapshot. Do not edit; supersede with a later review. Line numbers
refer to commit `01592b2` (main, 2026-09-14).

**Scope:** every file in `src/` (16 namespaces, about 5,800 lines), the test
suite (15 namespaces plus benchmarks), `README.md`, `docs/`, `plans/`,
`bb.edn`, `deps.edn`, and the examples (skimmed).

**Method:** full read, then every suspected defect was reproduced by
running the code. Findings marked **[verified]** come from
`evidence/probe.clj`; run it from the repo root with
`clojure -M reviews/2026-09-15/evidence/probe.clj`. Findings marked
**[read]** come from reading the code and have not been run. Nothing here
is from memory.

## Summary

limner has a sensible surface: pure-data components, a colour API covering
16, 256 and truecolour, box borders, a constraint layout, and a large test
suite (1,469 assertions, green on the JVM and babashka). The foundations
beneath that surface are unsound, in three ways that repeat across the
codebase:

1. **Text is measured and cut in UTF-16 code units, and styled by string
   concatenation with a hard reset.** Almost every component misaligns on
   CJK text, emoji, or text that is already coloured, and nested styles lose
   their outer attributes. `visible-width` exists, but most code does not use
   it. Where code does use it, the escape-sequence handling underneath only
   understands SGR.
2. **Errors are swallowed.** 41 `catch Exception` blocks print to stderr and
   return fabricated values. Bugs become silent visual corruption, and
   warnings get printed into the user's TUI.
3. **The documentation describes a different, finished library.** The
   README claims "production-ready v1.0", ">95% coverage", performance
   figures and a terminal compatibility matrix, none of which is backed by
   anything in the repo. It contains four broken examples, and the tutorial
   calls 20 functions that do not exist.

Unicode width (issue #2) is one instance of problem 1, and the fix for it
should be the foundation that fixes the others.

## What is worth keeping

- **Components as plain maps** with pure `render` functions (`panel`,
  `list`, `input`, `progress`, `statusbar`). This is the right shape for a
  Clojure TUI library and makes golden testing easy.
- **The colour constructors** (`rgb`, `color-256`, `bg-rgb`, `bg-256`) with
  input validation.
- **The layout constraint vocabulary** (`fixed`, `percent`, `flex`, `auto`).
  The idea is fine; the arithmetic needs fixing.
- **Terminal capability detection through a stubbable `get-env`** (fixed on
  2026-09-14), and the `with-simulated-capabilities` test helper.
- **The grapheme-cluster approach in `visible-width`** (commit `51e3621`).
  The direction is right; the data under it is not.
- **Test volume.** There are many tests; the problem is how weak the
  assertions are, not how few tests there are.

## A. Public face and honesty

| # | Finding | Evidence |
|---|---|---|
| A1 | The README claims "production-ready (v1.0)", ">95% test coverage", "371 assertions", "60 FPS with 1000+ cells", "<2% CPU", "memoization for unchanged subtrees", a compatibility matrix marked "✅ Full", and flex layout that "guarantees total space utilization". The repo contains no measurement, coverage report or terminal test behind any of these, and the last claim is false (A/F6). | README.md:9, 49, 366, 421-427, 494-504, 478-492 |
| A2 | The citation block is a placeholder (`author = {Your Name}`, `url = {https://github.com/yourusername/limner}`, year 2024). | README.md:579-585 |
| A3 | README Examples 3, 4 and 5 call `layout/resolve-layout`, `events/on-key`, `events/start-event-loop!` and `stream/complete?`, none of which exist. Example 3's `(layout/box :height (layout/fixed 5))` throws, because `box` only accepts integers. **[verified]** | `evidence/docrefs.clj`; probe 5b |
| A4 | `docs/tutorial.md` (1,410 lines) calls 20 functions that do not exist, including `core/enable-raw-mode!`, `core/read-char`, `input/input-field` and `statusbar/status-bar`. **[verified]** | `evidence/docrefs.clj` |
| A5 | Two things carry marketing voice into the repo: the header of `src/limner.clj` ("100% bulletproof, no more broken borders"), and `plans/code_review.md` declaring "production-ready!" with most of its own list still open. | limner.clj:1; plans/code_review.md:350, 381-383 |
| A6 | Stale artefacts are committed: `benchmark_results.txt`, and three planning documents (2,041 lines) that read as a finished-project log. | repo root, `plans/` |

## B. Text model: the root-cause class

| # | Finding | Evidence |
|---|---|---|
| B1 | `color` wraps text as `code + s + ESC[0m`. A full reset inside an enclosing style clears the outer style. `(color :bold (str (color :red "R") " after"))` leaves " after" unbolded. The same happens in markdown (`# a **b** c` loses the header colour after `b`) and in the input cursor, which is wrapped in `color :reset`. **[verified]** | core.clj:163; input.clj:234; probes 1, 12b |
| B2 | Escape handling recognises only SGR (`ESC\[[0-9;]*m`). Other CSI sequences and OSC 8 hyperlinks are counted as visible text: `ESC[2K` + "hi" measures 5, and an OSC 8 link with the text "link" measures 22. The regex appears seven times across four namespaces. **[verified]** | core.clj:343; syntax.clj:304; render.clj:52; statusbar.clj:64, 122-124; probes 2, 2b |
| B3 | Most code measures and cuts with `count`, `subs`, `nth` or `format "%Ns"` (UTF-16 code units) instead of display columns. The consequences, all **[verified]**:<br>• a titled box with a CJK title has a top line 18 wide over content lines 11 wide;<br>• a coloured title gives widths 9 versus 18;<br>• a list configured with width 20 renders lines 39 wide;<br>• a statusbar of width 20 renders 23 columns;<br>• `layout/clip` asked for width 3 returns 6 columns;<br>• the render buffer stores one emoji as two cells (surrogate halves);<br>• streaming "a😀b" at position 2 emits a lone surrogate;<br>• `limner/table` misaligns CJK. | borders.clj:136-137; list.clj:292-301; statusbar.clj:136-167; layout.clj:439, 463; render.clj:71-89; streaming.clj:146; limner.clj:28-33; probes 4, 4b, 5d, 6, 9, 11, 14, 15 |
| B4 | `parse-line-with-ansi` accumulates escape strings and never resets them: after `ESC[0m` the cell's `:ansi` is `ESC[31mESC[0m`. Style state grows without bound, and cells that look identical on screen compare unequal in the diff. **[verified]** | render.clj:83; probe 6c |

**Consequence:** fixing these one by one would touch every namespace and
leave the class of bug in place. The fix is one text layer that everything
uses (PLAN.md Phase 1).

## C. Unicode width and segmentation

| # | Finding | Evidence |
|---|---|---|
| C1 | Width comes from hand-written ranges, not from UCD data. Wrong results against UCD 17.0, all **[verified]**:<br>• U+3000, U+3001, U+3131, U+3220 and U+338F are Wide but measure 1;<br>• U+1F170 🅰 (Ambiguous) and U+1F321 🌡 (Neutral) measure 2;<br>• default-ignorable U+00AD, U+2060 and U+FEFF measure 1 instead of 0;<br>• a lone regional indicator measures 2. | core.clj:212-307; probes 3–3f |
| C2 | No Unicode version is pinned. In U+2600–27BF limner agrees with pmonks's glibc `wcwidth` table on all 448 code points, and differs from UCD 17.0 on the same 14 that glibc does: the Yijing trigrams and digrams, which became W in Unicode 16. The block breaks down as 308 N, 80 A and 60 W. **[verified]** | `evidence/width-compare.clj`, `evidence/glibc-wcwidth-U2600-U27BF.tsv` |
| C3 | `java.text.BreakIterator` on JDK 21 passes 749 of 766 `GraphemeBreakTest-17.0.0` cases. Every emoji case passes; 16 failures are Indic conjuncts (GB9c, Unicode 15.1) and one is a ZWJ case. The docstring claims it "follows UAX #29". Behaviour depends on the JDK, which can differ between a user's JVM and their babashka build. This confirms pmonks's point on #2. **[verified]** | core.clj:309-322; `evidence/grapheme-break-conformance.clj` |
| C4 | Performance: a mixed CJK, Latin and emoji string runs at about 149k calls/s on a warm JVM (probe 17). The earlier `bb benchmark:unicode` run reported about 4k ops/s for CJK under babashka. Each call allocates a `BreakIterator` and lazy sequences. There is no budget and no regression check. **[verified JVM]** | core.clj:315, 368-371 |

## D. Error handling

| # | Finding | Evidence |
|---|---|---|
| D1 | 41 `catch Exception` blocks (render 16, events 11, borders 7, layout 7) print to `*err*` and return invented values. Examples: a `"+-+"` box (borders.clj:109-111), a titled fallback box with the wrong width (borders.clj:166-168), `[]` layouts, and a constraint width of 0 (layout.clj:104-107). | grep; files cited |
| D2 | Handling is inconsistent. `:pre` failures throw `AssertionError`, which `catch Exception` does not catch, so a stack with spacing larger than its height escapes as an `AssertionError`, while other invalid layouts silently give `[]`. **[verified]** | layout.clj:96, 117; probe 5c |
| D3 | Invalid colour specs print a warning and return the uncoloured string. Unknown border styles fall back to `:single`. Unknown spinner styles produce `nil` frames and a later divide-by-zero. **[verified]** | core.clj:164-168; borders.clj:63; progress.clj:32, 42; probe 13b |

## E. Runtime, concurrency and terminal I/O

| # | Finding | Evidence |
|---|---|---|
| E1 | Blocking core.async operations (`alts!!`) run inside a `go-loop`: `batch-events` at events.clj:553 and `call-handler-with-timeout` at events.clj:533 are both called from the `go-loop` at events.clj:599. Handlers run inside `go` (events.clj:517), so a blocking handler occupies a dispatch thread, and a timeout does not cancel the handler. **[read]** | events.clj:507-563, 599-661 |
| E2 | The async processor reads the state, calls the handler, then `reset!`s the atom (events.clj:618-627), so an update from any other writer is lost. The README's "race-condition-free" claim does not hold. **[read]** | events.clj:618-627; README.md:467 |
| E3 | The render loop does terminal output inside `swap!` functions, which can be retried (render.clj:542, 555, 559, 611). `:force-render!`, which `state/bind-to-render` calls from whichever thread changed the state, writes to the terminal at the same time as the loop thread. `stop!` rethrows the last loop error even if the loop recovered from it. **[read]** | render.clj:540-611; state.clj:178-190 |
| E4 | There is no terminal input layer. Nothing enables raw mode or reads keys; the examples shell out to `stty` themselves. The key parser does not handle Shift-Tab (`ESC[Z`, which `handle-tab-navigation` depends on), non-BMP characters or a bare ESC. **[verified]** | events_interactive.clj:13-27; events.clj:20-55, 429; probes 7, 7b, 7c |
| E5 | Terminal size is read by running `sh -c 'stty size </dev/tty'` every 500 ms. A width under 20 or a height under 10 is silently replaced with 80×24. Capability detection ignores `NO_COLOR`, `FORCE_COLOR`, whether output is a TTY, and Windows Terminal. **[read]** | render.clj:255-290, 373-379; terminal.clj |

## F. Component defects

All of these are **[verified]** unless marked otherwise.

| # | Finding | Evidence |
|---|---|---|
| F1 | **input:**<br>• `history-prev` returns `nil`, not the state, when history is empty;<br>• a placeholder with the cursor at 0 puts a raw ESC byte inside the inverse-video cursor (garbage output);<br>• a multiline value ending in `\n` draws the cursor on the wrong line and adds a stray empty line. | input.clj:153, 201-234, 263-270; probes 10, 10b, 10c |
| F2 | **list:**<br>• width 5 throws `StringIndexOutOfBoundsException`;<br>• CJK items overflow the width (see B3);<br>• the `set-filter` branches look inverted (both reset `:selected`; only the non-empty branch resets scroll) **[read]**;<br>• `select-next` and `select-prev` do not keep the selection scrolled into view **[read]**. | list.clj:115-121, 147-183, 295-297; probe 11b |
| F3 | **markdown:**<br>• `my_var_name` renders "var" in italics;<br>• the `:list-item` renderer takes 4 arguments but the registry calls it with 2 (`ArityException`);<br>• nested lists are modelled but never parsed **[read]**;<br>• code blocks ignore `limner.syntax` **[read]**;<br>• `with-custom-renderers` mutates the global registry atom, so it is not thread-safe **[read]**. | markdown.clj:138-157, 342-351, 437-442, 483, 550-562; probes 12, 12c |
| F4 | **progress:**<br>• `complete?` is false after `(set-value bar 100.0)`, because `(= 100 100.0)` is false;<br>• an unknown spinner style divides by zero on `tick`;<br>• `:box` and `:square` have identical frames **[read]**. | progress.clj:259-265, 11-17, 36-43; probes 13, 13b |
| F5 | **syntax:**<br>• keywords are highlighted inside identifiers (`my-def` becomes `my-` + keyword `def`);<br>• the tokenizer calls `subs` and `re-find` on the rest of the input at every position, so 11 KB of Clojure takes about 400 ms;<br>• several configured keys (`:symbol-pattern`, `:fstring-pattern`, `:arrow-function`, `:template-literal`) and theme keys (`:function`, `:operator`, `:special`) are never used **[read]**. | syntax.clj:150-227, 68-89; probes 8b, 8c |
| F6 | **layout:**<br>• three `flex 1` items in height 10 get `(3 3 3)`, so the leftover row is dropped;<br>• README Example 3 throws (A3);<br>• spacing larger than the height escapes as an `AssertionError` (D2). | layout.clj:109-132; probes 5, 5b, 5c |
| F7 | **borders:**<br>• `colorize-border` detects border lines by sniffing five corner glyphs, so on `:rounded` and `:thick` boxes only the corners are coloured;<br>• tabs count as width 0. | borders.clj:319-336; probes 4c, 4d |
| F8 | **`limner` (top-level namespace):**<br>• duplicates syntax highlighting and panels;<br>• `table` right-aligns with `format`, misaligns CJK, and silently draws a malformed table for rows of unequal length;<br>• `live-display` hides the cursor without a `finally` **[read]**. | limner.clj:8-54; probes 15, 15b |
| F9 | **statusbar:** discards the caller's styling (strips ANSI and never restores it), duplicates core's background colours, and overflows with CJK. **[read]** | statusbar.clj:7-24, 118-181 |

## G. Tests

| # | Finding | Evidence |
|---|---|---|
| G1 | Output is checked with `includes?` rather than exact strings. `test-color-nesting` passes while B1 is broken. No test compares a whole rendered component. | core_test.clj:44-97 |
| G2 | Tests depend on wall-clock time: at least 9 `Thread/sleep`s in the async event tests, a `< 100 ms` performance assertion in `core_test`, and `System/nanoTime` in the render tests. These are flaky under load. | events_test.clj:472-592; core_test.clj:368-376 |
| G3 | Tests touch the real terminal: `setup-terminal` and `restore-terminal` write hide-cursor and clear-screen sequences to stdout, and `get-terminal-size` shells out to `stty`. | render_test.clj:338-347, 392-396 |
| G4 | There is no babashka test task, although babashka is a supported runtime. `bb.edn` puts `test/` on `:paths` so the benchmarks can load. | bb.edn |
| G5 | There are no property-based tests, no Unicode conformance tests, no check that documented examples run, and no mutation checks. Loading all namespaces prints 18 reflection warnings. | grep; reflection run |

## H. Repository hygiene

- There is no `CHANGELOG.md`, no version tags, no ADRs, no issue tracker, and
  (until this review) no `CLAUDE.md`.
- `.gitignore` repeats several entries.
- `limner` is a single-segment namespace (legal, but conventionally avoided
  on the JVM).
- core.async is a dependency only for the experimental async event layer.

## Replies owed

Issue #2 is open. pmonks has posted two follow-ups, including a
glibc `wcwidth` table, and the owner replied on 2026-09-15 promising a
deeper look. C2 and C3 above are the evidence for that reply. pmonks also
asked for a bug report against clj-wcwidth. That bug is real: its `wide?`
function treats all of U+2600–27BF as wide (`src/wcwidth/api.clj:211` at
`2681e5a`), which is wrong for 388 of the 448 code points in the block
under UCD 17.0.
