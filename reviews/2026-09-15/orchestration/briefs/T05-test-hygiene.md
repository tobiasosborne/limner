## T05 — PLAN 0.5: test hygiene

Claim the beads issue for plan item 0.5 (REVIEW G2 and G3, plus the terminal-touching parts of E5).

**Goal:** the test suite must be deterministic and hermetic. It must not use wall-clock sleeps, timing assertions, writes to the real terminal, shell-outs to `stty`/`tput`, or real environment reads.

Keep the changes small and surgical. This task is not the Phase 2 redesign of render, events or tty (those beads come later). Add only the minimum injection points needed to test deterministically, and make them the ones Phase 2 would keep: plain function arguments or options with production defaults. Avoid `with-redefs` of library internals where an injection point is cleaner. `with-redefs` of a single documented seam, such as the existing `terminal/get-env`, is acceptable.

### Find the offenders first, and list them in your report

- Every `Thread/sleep`, `System/currentTimeMillis`, `System/nanoTime` and wall-clock-dependent assertion in `test/`.
- The performance assertion in `core_test.clj` (under 100 ms for 10k calls).
- Tests that call functions which print escape sequences to `*out*`, such as `setup-terminal`, `restore-terminal`, `clear-screen`, `force-render`, `render-frame` and `apply-diff`.
- Tests that reach `get-terminal-size`, which shells out to `sh -c stty`.
- Anything that reads the real environment.
- Warnings the suite prints to stderr (for example "Warning: Event handler timeout", stack traces). Every stderr line during a test run is either asserted on (captured and checked) or a defect to remove.

### What to do

1. **Timing.**
   - Remove the wall-clock performance assertion. Performance belongs in `test/limner/benchmarks.clj` and the Phase 1.8 bead. If that assertion guards something real, say so in a comment on the bead instead.
   - For code that genuinely depends on time, add an injectable clock (a zero-argument function returning ms or ns), defaulting to the system clock, and test with a fake clock. This covers `render/should-render?`, `render-frame`, the render-state timestamps and `streaming` `advance` / `tick-cursor`.

2. **Async event tests.**
   - Replace the sleeps with deterministic synchronisation: promises or latches that the handler delivers, dereferenced with a generous timeout (e.g. 5 s) that is only a hang guard and never the expected path.
   - The handler-timeout test currently relies on `Thread/sleep` inside a handler with a 50 ms timeout. Restructure it so the handler blocks on a promise that the test releases after observing the timeout callback.
   - Do **not** fix the known E1 defect (blocking ops in go blocks). If a test cannot be made deterministic without fixing E1, mark it with an explicit, documented `^:flaky-until-limner-2-5` metadata or skip note tied to the Phase 2 events bead (find its ID). Explain it in your report. Do not silently delete it.

3. **Terminal writes.**
   - Tests of functions that print must capture output with `with-out-str` and assert on the exact string, escapes made visible in failure messages.
   - Nothing may reach the real stdout during `bb test` / `clojure -M:test`.
   - Add a guard test or runner-level check that fails if a test namespace writes raw escape sequences to the real `*out*`, if it can be done cheaply and portably. If it cannot, explain why in the report.

4. **Terminal size.**
   - Give `get-terminal-size` an injectable size source (an option or a dynamic var with a documented production default).
   - Tests use a fake source.
   - Test the silent clamping (under 20 becomes 80) as the current behaviour, with a comment linking the Phase 2 tty bead. Do not change the behaviour here.

5. **Environment.** Anything reading env outside `terminal/get-env` gets routed through it or stubbed.

### Verify

- Run `bb check` three times in a row. All three must pass, and the output of the runs must be identical apart from timing lines.
- Report how much of the suite's stderr noise remains, and why each remaining line is expected.
- Report JVM and bb counts.
- Every injection point added gets a docstring and follows CLAUDE.md Rule 7 wording.
