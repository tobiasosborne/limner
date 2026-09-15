## T02 — PLAN 0.4: local quality gates

Find the beads issue for plan item 0.4 (`bd list`, `bd ready`) and claim it.
This task is **only** the gates. Do not fix the README, the docs, or code
defects they reveal: other tasks do that.

A gate is allowed to start out red, as long as it is red for a real reason.
When it is, record the current failures as a documented, explicit baseline
and file a bead for them. Never weaken a gate to make it green.

### Gates to build

1. **`bb test`.** Add a babashka task that runs every test namespace under
   `test/` (except the benchmarks namespace) and exits non-zero on any
   failure or error.
   - Make it discover the namespaces rather than hard-code them, if that
     works reliably in babashka.
   - The JVM equivalent, `clojure -M:test`, already exists; make sure both
     run the same set of namespaces.
   - Decide whether `test/` should stay on `bb.edn`'s `:paths`: the
     benchmarks namespace needs it for the `benchmark` tasks today. Keep
     the benchmark tasks working.

2. **Doc-reference gate.** A test namespace, e.g.
   `limner.docs-reference-test`. Start from
   `reviews/2026-09-15/evidence/docrefs.clj` but make it robust:
   - Scan `README.md`, `docs/**/*.md` except `docs/archive/**` (which the
     0.3 task will create), and `examples/*.clj`.
   - Resolve aliases properly. In `.clj` examples, read the `ns` form's
     `:require` aliases. In markdown code blocks, use the `require` forms
     inside the block where present, falling back to the conventional
     alias table only for blocks without requires.
   - Fail with a precise list of `file:line alias/sym` entries that do not
     resolve.
   - Today it will fail (README 4 missing, tutorial 20). Do not suppress
     that. Record the current failure list in a clearly named baseline data
     file that the test compares against. The gate then fails on any *new*
     missing reference, and also fails when a baseline entry has been fixed
     but not removed from the baseline, so the baseline only ever shrinks.
   - File a bead pointing at 0.2/0.3 to empty the baseline.

3. **README examples gate.** Extract the Clojure code blocks from
   `README.md` and evaluate them in a test, each block in a fresh namespace
   with its own requires.
   - Blocks that are not meant to be runnable, such as ones that start a
     render loop or call `System/exit`, need an explicit opt-out marker in
     the markdown, e.g. an HTML comment `<!-- not-executed: reason -->`
     right before the fence. Document that convention in CLAUDE.md
     (Practical guidance).
   - As with gate 2, today's broken examples go in a shrinking baseline
     and get a bead, and are not silently skipped.
   - Output that would touch the real terminal must be captured.

4. **Reflection gate.** A test that loads every `src/` namespace with
   `*warn-on-reflection*` bound true, captures the warnings, and compares
   them against a checked-in baseline (currently about 18 warnings; record
   the exact set). It fails on any new warning and on a fixed but
   un-removed baseline entry. Babashka has no reflection warnings, so on bb
   this test must report that it doesn't apply and pass, not silently do
   nothing. Put that in the assertion message.

5. **Lint.** Check whether `clj-kondo` is installed. If it isn't, don't
   install anything system-wide; write in your report how the owner could
   add it and skip this gate.

6. **`bb check`.** One task that runs, in order and serially: the JVM suite
   (`clojure -M:test`), then `bb test`, then any lint. It stops at the
   first failure and prints a one-line summary per gate. Document it in
   CLAUDE.md "Practical guidance", replacing the note there that says
   `bb test` does not exist yet.

### Notes

- Baselines are data files with a comment header saying what they are and
  that they may only shrink. Put them under `test/limner/baselines/` or
  `test-resources/`, whichever fits the classpath cleanly on both runtimes.
- Gates 2–4 are ordinary `deftest`s, so both `clojure -M:test` and
  `bb test` run them.
- Keep new test namespaces within CLAUDE.md Rule 11 (about 200 lines of
  code) and give each a literate docstring.
- In your report: how long `bb check` takes, and the pass/fail status of
  each gate.
