1. Changed files

- [README.md](/home/tobiasosborne/Projects/limner/README.md): rewrote the README with honest 0.x status, limitations, experimental namespaces, verified installation coordinates, executable examples with captured output, namespace overview, development commands, acknowledgements, and unchanged license text.
- [doc_references.edn](/home/tobiasosborne/Projects/limner/test/limner/baselines/doc_references.edn): removed all four README entries; tutorial entries remain untouched.
- [readme_examples.edn](/home/tobiasosborne/Projects/limner/test/limner/baselines/readme_examples.edn): removed all three entries; the baseline is now empty.
- [.beads/issues.jsonl](/home/tobiasosborne/Projects/limner/.beads/issues.jsonl): exported the closed issue state.

2. Red-first tests

No new test source was needed or permitted by scope; T02’s existing gates supplied coverage.

- `documented-references-match-the-shrinking-baseline` failed first because four resolved README references remained in its baseline.
- `readme-examples-match-the-shrinking-baseline` failed first because three corrected examples remained in its baseline.

Both passed after shrinking the baselines.

3. Verification

- JVM: 256 tests, 1,494 assertions, 0 failures, 0 errors.
- Babashka: 256 tests, 1,493 assertions, 0 failures, 0 errors.
- Counts match the pre-edit `bb check` run.
- All five README examples ran on both runtimes with matching captured output.
- No README entries remain in either baseline.
- Relative links `CLAUDE.md` and `LICENSE` exist.
- Unsupported-claim scan and `git diff --check` passed.
- `clj-kondo` was not installed, so `bb check` explicitly skipped it.

4. Beads

- Claimed and closed: `limner-0-2`.
- Created: none.

5. Decisions and open items

- Used the full fetchable `origin/main` SHA `01592b2ede8d9f0b01a0d61f8d9537bd84b81052`; no release tag exists.
- No deviations. Library code, `docs/tutorial.md`, and `plans/` were untouched.
- Changes remain uncommitted for orchestrator review.