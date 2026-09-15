Implemented T05 and left all changes uncommitted.

Offender inventory:

- Nine `Thread/sleep` calls in `events_test.clj`.
- Three `System/nanoTime` uses in `render_test.clj`.
- Two `System/nanoTime` calls and the `<100 ms` assertion in `core_test.clj`.
- Streaming delay and cursor tests depended on the production clock.
- Render tests invoked `render-frame`, `force-render`, `setup-terminal`, and `restore-terminal` without capturing output.
- Two terminal-size tests reached the real `stty`/`tput` source.
- `test/limner/check.clj` read `PATH` directly.
- Baseline stderr contained two color warnings, an event-handler stack trace, and a timeout warning: 24 JVM lines and 31 babashka lines.
- `benchmarks.clj` retains one sleep and four `nanoTime` calls intentionally; benchmarks are excluded from test discovery.

Changes by file:

- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): exported the closed issue state.
- [src/limner/render.clj](/home/tobiasosborne/Projects/limner/src/limner/render.clj): added documented clock and terminal-size injection, preserving production defaults and existing clamping.
- [src/limner/streaming.clj](/home/tobiasosborne/Projects/limner/src/limner/streaming.clj): added the documented `:clock` option and routed timestamps, advancement, and cursor ticks through it.
- [docs/api/render.md](/home/tobiasosborne/Projects/limner/docs/api/render.md): documented clock and size-source contracts.
- [docs/api/streaming.md](/home/tobiasosborne/Projects/limner/docs/api/streaming.md): documented the streaming clock.
- [test/limner/events_test.clj](/home/tobiasosborne/Projects/limner/test/limner/events_test.clj): replaced all sleeps with promises and exact stderr assertions.
- [test/limner/render_test.clj](/home/tobiasosborne/Projects/limner/test/limner/render_test.clj): uses fake clocks/sizes and asserts exact captured terminal bytes.
- [test/limner/streaming_test.clj](/home/tobiasosborne/Projects/limner/test/limner/streaming_test.clj): uses deterministic clocks.
- [test/limner/core_test.clj](/home/tobiasosborne/Projects/limner/test/limner/core_test.clj): removed the performance assertion and captured expected warnings.
- [test/limner/check.clj](/home/tobiasosborne/Projects/limner/test/limner/check.clj) and [check_test.clj](/home/tobiasosborne/Projects/limner/test/limner/check_test.clj): route `PATH` through `terminal/get-env`.
- [test/limner/test_runner.clj](/home/tobiasosborne/Projects/limner/test/limner/test_runner.clj) and [test_runner_test.clj](/home/tobiasosborne/Projects/limner/test/limner/test_runner_test.clj): added a runner-level guard against uncaptured stderr and raw escape bytes.

Red tests observed first:

- Render tests: 11 errors because clock options and injectable terminal-size arity were absent.
- Streaming tests: seven failures because timestamps, advancement, and cursor state used the real clock.
- `executable-discovery-uses-the-environment-seam`: failed because `terminal/get-env` was never called.
- `rejects-uncaptured-terminal-and-error-output`: three failures because raw output was not intercepted.

Verification:

- JVM: 258 tests, 1,525 assertions, 0 failures, 0 errors.
- Babashka: 258 tests, 1,524 assertions, 0 failures, 0 errors.
- `bb check` passed three consecutive times. All three stdout files were byte-identical; all three stderr files were empty.
- `git diff --check` passed.
- clj-kondo was unavailable and reported as skipped.
- Remaining stderr noise: zero bytes.

Beads:

- Claimed and closed: `limner-0-5`.
- Created: none.

No scope deviations. The performance work remains with `limner-1-8`; silent terminal clamping remains documented for `limner-2-4`; unsafe event internals remain for `limner-2-5`. No flaky annotation was needed because the promise-based event tests are deterministic.