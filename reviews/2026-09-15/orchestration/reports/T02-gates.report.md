Implemented T02 and left all changes uncommitted.

1. File changes

- `.beads/issues.jsonl`: exported tracker updates.
- `bb.edn`: added discovered `bb test` and serial `bb check`; retained `test/` for benchmarks.
- `deps.edn`: switched JVM tests to the same portable runner.
- `CLAUDE.md`: documented both test commands, `bb check`, lint behavior, and README opt-out markers.
- `README.md`: added five explicit `not-executed` markers; no examples were corrected.
- `test/limner/test_runner.clj`: shared namespace discovery, focused `-n` runs, and reliable process exit.
- `test/limner/check.clj`: fail-fast JVM, Babashka, then optional lint orchestration.
- `test/limner/quality_gate_support.clj`: safe form/alias and Markdown-fence parsing.
- `test/limner/docs_reference_test.clj`: scans README, non-archive docs, and examples with scoped aliases and precise diagnostics.
- `test/limner/readme_examples_test.clj`: evaluates runnable README blocks in isolated namespaces with captured output.
- `test/limner/reflection_test.clj`: reloads all source namespaces and compares JVM warnings; explicitly reports N/A on Babashka.
- Added corresponding runner, orchestration, and parser tests.
- Added shrinking baselines:
  - `doc_references.edn`: 24 entries—4 README, 20 tutorial.
  - `readme_examples.edn`: 3 runnable-block failures.
  - `reflection_warnings.edn`: 18 warnings.

2. Red tests observed first

- Alias and Markdown parser tests failed on empty stub results.
- Fragment-prefix parsing failed by discarding a valid preceding `require`.
- Runner discovery and `-n` selection failed on empty discovery.
- Explicit successful-process exit test failed because the runner returned without exiting.
- Documentation gate failed with the expected 24 unresolved references.
- README gate failed with the expected 3 runnable-block failures.
- Reflection gate failed with all 18 current JVM warnings.
- `bb check` orchestration tests failed because no commands ran and no summaries were produced.
- Stable baseline-key tests failed because moving line numbers were initially retained.

3. Verification

- JVM: 256 tests, 1,494 assertions, 0 failures, 0 errors.
- Babashka: 256 tests, 1,493 assertions, 0 failures, 0 errors.
- The one-assertion difference is the explicit Babashka reflection N/A assertion.
- Final `bb check`: passed in 11.56 seconds.
  - JVM tests: PASS
  - Babashka tests: PASS
  - `clj-kondo`: SKIP—executable not installed
- `bb benchmark:color`: PASS.
- `git diff --check`: PASS.

To enable lint, install a `clj-kondo` executable through the owner’s preferred package manager or standalone release and place it on `PATH`; `bb check` will detect and run it automatically.

4. Beads

- Claimed and closed: `limner-0-4`.
- Created: `limner-p0.1`, dependent on `limner-0-2` and `limner-0-3`, to empty both documentation baselines.
- No other issues created or closed.

5. Decisions and open items

- Baseline identities omit volatile line numbers, while failures report current exact locations. This lets baselines only shrink.
- Reflection baselines preserve duplicate-warning counts while ignoring source-line movement.
- Existing suite-generated warning/error and terminal output remains unchanged and out of scope.
- No deviations from the brief; no README, documentation, or library defects were fixed.