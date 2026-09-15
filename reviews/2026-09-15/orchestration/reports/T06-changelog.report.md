1. Changed files:

- [CHANGELOG.md](/home/tobiasosborne/Projects/limner/CHANGELOG.md): added `[Unreleased]`, legacy `0.1.0`, verified commit SHAs, reporter credits, and release links.
- [CLAUDE.md](/home/tobiasosborne/Projects/limner/CLAUDE.md:250): added concrete changelog guidance.
- [README.md](/home/tobiasosborne/Projects/limner/README.md:31): conditionally documented the future `v0.1.0` legacy-baseline tag.
- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): exported the completed bead state. The export also captured pre-existing local `limner-p4.1` tracker state.

2. Red-first tests:

None; this was documentation and tracker work with no behavior change.

3. Verification:

- `bb check`: passed.
- JVM: 258 tests, 1,525 assertions, 0 failures, 0 errors.
- Babashka: 258 tests, 1,524 assertions, 0 failures, 0 errors.
- clj-kondo: skipped because it is not installed.
- `git diff --check`: passed.

4. Beads:

- Claimed and closed `limner-0-6`.
- Added a comment recording `git tag v0.1.0 01592b2` as pending orchestrator review and owner push.
- Created no beads.

5. Decisions and open work:

- Included T05’s injectable clock/terminal-size APIs because they are user-visible changes.
- No tag, commit, push, or GitHub action was performed.
- The changelog links will resolve only after the owner pushes `v0.1.0`.