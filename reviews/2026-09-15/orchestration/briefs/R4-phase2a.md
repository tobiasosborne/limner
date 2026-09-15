## Run R4 — Phase 2.1, 2.2, 2.6 (three beads, in this order)

Phase 1 is done. The foundation is `limner.unicode*`, `limner.ansi*` and
`limner.text`; its ADRs are in `docs/adr/`. Read those ADRs before starting,
and use the foundation everywhere in this run.

---

## Part 1 — PLAN 2.1: fail-loud error policy (REVIEW D1–D3; CLAUDE.md Rule 1)

Claim the 2.1 bead. Write the next-numbered ADR, `error-policy`, before any
code.

### ADR content

- **Library code never swallows exceptions.** It never prints to
  `*err*`/`*out*` as a way of reporting errors, and it never returns
  fabricated fallback values.
- **Invalid input throws `ex-info`.** The message is human-readable. The
  data map contains:
  - `:limner/error`, a namespaced keyword naming the error class, e.g.
    `:limner.layout/invalid-constraint`;
  - the offending value(s);
  - where useful, a `:limner/hint` string saying how to fix it.
  Document the full set of error classes in the ADR and in each namespace's
  docstring.
- **`:pre`/`assert` are not used for argument validation in public
  functions.** `AssertionError` is an `Error` (it escapes `catch Exception`),
  and assertions can be disabled with `*assert*`. Use explicit validation
  that throws `ex-info`. Internal invariant checks may keep `assert`; say so.
- **The one error boundary** is the render loop's opt-in `:on-error`. The
  ADR defines exactly what it does: the loop keeps its last good frame, the
  boundary reports to the callback, and the loop does not print. Part 2 of
  run R5 implements it; this ADR only specifies it.
- **Capability fallbacks are not errors.** Choosing `:ascii` borders on a
  terminal without Unicode is a documented capability selection, not a
  silent fallback, and it is fine.
- **Unknown options and keywords throw.** Examples: an invalid colour spec,
  an unknown border style, an unknown spinner style, an unknown
  `:title-pos`. Decide whether unknown option keys in keyword-argument maps
  throw too. The recommendation is to throw only for enumerated values, not
  for unknown keys, to keep the Clojure convention of open maps. Justify
  the choice.

### Code

- Apply the policy across all of `src/`. That means all 41 `catch Exception`
  sites and every `binding [*out* *err*]` (see REVIEW D1; use `grep` to find
  the current count).
- **Each site, in order:**
  1. Write a red test asserting the new throw: exact `:limner/error` class
     and data keys.
  2. Remove the catch-and-fabricate.
  3. Update any legacy test that asserted the fabricated fallback. Explain
     why in the commit body.
- **Experimental namespaces** (`render`, `events`, `state`, `streaming`,
  `syntax`, `markdown`) get the same treatment, except the render-loop and
  async event-processor boundaries. Those belong to the 2.3 and 2.5 beads.
  For now, leave them structurally as they are but stop them printing:
  - route the error to the existing callbacks, or keep it in the stats;
  - add a `;; TODO(limner-2-3)` or `;; TODO(limner-2-5)` comment linking the
    bead ID.
  The only allowed TODO comments are ones naming a bead.
- **After this bead, a grep gate holds:** no `binding [*out* *err*]` and no
  `println` in `src/` outside the documented terminal-output functions and
  the deprecated `limner` demo `-main`.
  - Add the gate as a test: a scan of `src/` with an explicit allow-list
    carrying reasons.
  - Add a gate that `catch Exception` / `catch Throwable` appear only at
    allow-listed boundaries, again with reasons.
- **CHANGELOG `[Unreleased]`:** add a "Changed (breaking)" section listing
  every function that now throws instead of returning a fallback.

---

## Part 2 — PLAN 2.2: a render buffer on grapheme cells with style state (REVIEW B3, B4)

Claim the 2.2 bead. Write an ADR (`render-buffer`) covering the model before
the code.

### Model

- **A cell** is one grapheme cluster plus its style map (from
  `limner.text`).
- **A wide cluster** occupies two cells: the lead cell, and a continuation
  marker cell that renders nothing.
- **Zero-width content** attaches to the preceding cluster; segmentation
  guarantees this.
- **Writing a line** goes through `limner.text` for parsing, segmentation
  and width.
- **Writes at the edges:** a write that starts in the middle of a wide cell
  replaces it with spaces, and so does a write that would leave half a wide
  cluster at the right edge. The screen never shows half a glyph.
- **SGR accumulation is gone.** A cell's style is a value, and cells compare
  by value (REVIEW B4 fixed).
- **Diffs** produce runs of changed cells per row. Rendering emits
  `move-cursor` plus minimal SGR transitions (reuse `limner.text` rendering)
  and the graphemes, and never emits a half wide cluster.
- **The existing public functions** (`create-buffer`, `get-cell`,
  `set-cell`, `write-string-to-buffer`, `write-lines-to-buffer`,
  `find-dirty-cells`, `find-dirty-regions`, `apply-diff`, `render-frame`,
  `force-render`, `buffer-stats`) keep their names where the semantics still
  make sense. Where a signature must change, record it in CHANGELOG as a
  breaking change; `limner.render` is experimental.
- **Performance:** `find-dirty-regions` is currently O(n²) (REVIEW: `.indexOf`
  inside `partition-by`). Make it linear.

### Testing via a virtual terminal

- Add a test-only minimal VT emulator, `test/limner/vt.clj`. It consumes the
  emitted byte stream: CUP cursor positioning, SGR through `limner.ansi.sgr`,
  graphemes with widths from `limner.text`, and ED/EL clears. It produces a
  screen grid of `{:cluster :style}`.
- **Golden tests** render frames, feed the bytes through the VT, and compare
  the resulting grid with the expected grid. That is robust to byte-level
  formatting choices.
- **Direct byte tests** additionally pin a few exact outputs.
- **Probes.** The REVIEW probes for render (6, 6c) and all B3 render cases
  become red tests first.
- **Property tests:**
  - For any sequence of frames, applying the diffs through the VT gives the
    same grid as a full redraw of the last frame.
  - No cell ever holds half a wide cluster.

---

## Part 3 — PLAN 2.6: zero reflection warnings

Claim the 2.6 bead.

- Add type hints until loading every `src/` namespace with
  `*warn-on-reflection*` produces no warnings.
- Shrink `test/limner/baselines/reflection_warnings.edn` to empty.
- Change the reflection gate so that any warning fails. With the baseline
  empty, keep the baseline-file mechanism but assert that it is empty, or
  remove it. Decide which, and justify it in the commit body.
- **Babashka must not break.** Type hints are fine in bb, but some interop
  forms behave differently there. Run `bb test`.
- **Hot paths.** Measure hot paths in `limner.text`/`limner.unicode*`
  before and after with the `bb bench-check` task. Report any change.

All three beads: `bb check` passes on both runtimes. Report the counts, and
the grep-gate allow-lists with their reasons.
