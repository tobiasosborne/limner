1. Changed files:

- [docs/archive/2025](/home/tobiasosborne/Projects/limner/docs/archive/2025): moved the three former `plans/` files, tutorial, and mostly-fictional architecture document here with the required historical banner.
- [docs/archive/README.md](/home/tobiasosborne/Projects/limner/docs/archive/README.md): described the archive and directed new planning to beads.
- [docs/api/core.md](/home/tobiasosborne/Projects/limner/docs/api/core.md): corrected style nesting and Unicode-width claims.
- [docs/api/borders.md](/home/tobiasosborne/Projects/limner/docs/api/borders.md): corrected return type and title/colorization limitations.
- [docs/api/components.md](/home/tobiasosborne/Projects/limner/docs/api/components.md): removed the nonexistent aggregate namespace claim and corrected panel, input, list, Markdown, spinner, and status-bar descriptions.
- [docs/api/events.md](/home/tobiasosborne/Projects/limner/docs/api/events.md): documented parser gaps and unsafe async behavior.
- [docs/api/layout.md](/home/tobiasosborne/Projects/limner/docs/api/layout.md): corrected auto/flex allocation and UTF-16 clipping/padding behavior.
- [docs/api/render.md](/home/tobiasosborne/Projects/limner/docs/api/render.md): removed flicker/performance claims and corrected shutdown, terminal-size, buffer, and statistics descriptions.
- [docs/api/state.md](/home/tobiasosborne/Projects/limner/docs/api/state.md): described the actual atom metadata and synchronous watcher behavior.
- [docs/api/streaming.md](/home/tobiasosborne/Projects/limner/docs/api/streaming.md): documented caller-driven ticking and UTF-16 positions.
- [docs/api/syntax.md](/home/tobiasosborne/Projects/limner/docs/api/syntax.md): described the experimental regex-based implementation.
- [docs/api/terminal.md](/home/tobiasosborne/Projects/limner/docs/api/terminal.md): corrected heuristic detection and removed the false caching claim.
- [benchmark_results.txt](/home/tobiasosborne/Projects/limner/benchmark_results.txt): deleted.
- [src/limner.clj](/home/tobiasosborne/Projects/limner/src/limner.clj): replaced the marketing header with the requested deprecation docstring.
- [.gitignore](/home/tobiasosborne/Projects/limner/.gitignore): deduplicated; retained Clojure CLI, REPL, editor, OS, environment, log, and Beads/Dolt entries. Removed unused Leiningen-only entries after confirming the repository has no Leiningen files or references.
- [CLAUDE.md](/home/tobiasosborne/Projects/limner/CLAUDE.md): updated moved paths and retained the rule that planning belongs in beads.
- [doc_references.edn](/home/tobiasosborne/Projects/limner/test/limner/baselines/doc_references.edn): removed all 20 tutorial entries; both documentation baselines are now empty.
- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): exported closed issue state.
- `README.md` and `AGENTS.md` required no changes; neither referenced a moved path.

2. Tests first:

No new behavior tests were needed for this documentation/repository-only task. After moving the tutorial and before updating the baseline, I saw `documented-references-match-the-shrinking-baseline` fail because all 20 tutorial entries had become stale. It subsequently passed with 2 tests and 3 assertions.

3. Verification:

- JVM: 256 tests, 1494 assertions, 0 failures, 0 errors.
- Babashka: 256 tests, 1493 assertions, 0 failures, 0 errors.
- `bb check`: passed.
- `clj-kondo`: skipped by the gate because it is not installed.
- `git diff HEAD --check`: passed.
- Git recognizes all five moves as renames, with 94–99% similarity.

4. Beads:

- Claimed and closed `limner-0-3`.
- Claimed and closed `limner-p0.1`.
- Created no issues.

5. Decisions and open work:

I archived `docs/architecture.md` because most of it described behavior the implementation does not provide. The former plan files were placed directly under `docs/archive/2025/`. No deviations or open items remain.