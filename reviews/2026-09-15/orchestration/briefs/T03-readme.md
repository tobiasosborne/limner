## T03 — PLAN 0.2: README honesty pass

Claim the beads issue for plan item 0.2. Findings: REVIEW A1, A2, A3, A5 (the README part).

This task edits **README.md** only. It also shrinks the gate baselines that T02 created, for any README entries this task fixes.

- Don't touch `docs/tutorial.md` or `plans/`. The next task (0.3) archives those.
- Don't change library code.
- If the honest README needs a code change to have even one working example of a feature, that change is out of scope. Describe the feature in prose, and file a bead for the missing capability.

### Readers

Two kinds of people read this README:

- Clojure developers deciding whether to use limner. pmonks, who filed issues #1 and #2, is one of them.
- Agents working in the repo.

The reader should come away knowing exactly what works today, what is experimental, and what is being fixed. Write it in a warm, plain, confident voice. Don't be self-flagellating, and don't write marketing.

### Required content

1. **Title and one-paragraph description.** Keep the "limner" name story, but make it one or two sentences, not a preface.

2. **Status section near the top.**
   - Pre-release: 0.x. The API may change before 1.0.
   - The library supports the JVM and babashka.
   - Unicode width handling is being rebuilt on pinned Unicode data. Link issue #2: https://github.com/tobiasosborne/limner/issues/2.
   - List the known limitations from REVIEW B1–B3 in two or three user-facing bullets. For example: styles don't nest correctly yet, and components can misalign with CJK text, emoji, or already-coloured text.
   - Name the experimental namespaces (PLAN D4): `limner.render`, `limner.events`, `limner.state`, `limner.streaming`, `limner.syntax`, `limner.components.markdown`.
   - Say the top-level `limner` namespace is deprecated.

3. **Installation.** `deps.edn` git coordinate and babashka `bb.edn`.
   - Use a real, full sha placeholder convention, or tell readers to use a tag once one exists.
   - Don't invent version numbers.
   - Check the babashka snippet against how `bb.edn` `:deps` actually works.

4. **Quick start with examples that run today** and give correct output. Cover at least:
   - a bordered box (ASCII-content only, and say so);
   - colour;
   - a progress bar or spinner;
   - a layout calculation using the real API (`limner.layout/stack` plus `layout-stack`);
   - a list or panel component.

   Run every example yourself on both runtimes. Any output you show must be the real output, captured, not typed by hand.

   T02's README-examples gate evaluates these code blocks. Use its opt-out marker only for examples that take over the terminal, and give the reason in the marker.

5. **Namespace overview.** A compact table: namespace, what it does, status (core or experimental). Leave out capabilities that don't exist.

6. **Development.**
   - How to run `bb check`, `clojure -M:test` and `bb test`.
   - Point to `CLAUDE.md` for contributor/agent rules.
   - Beads usage in one line.
   - No CI mention beyond "gates run locally".

7. **Acknowledgements.** Credit pmonks for issues #1 and #2 (the deps.edn fix, and the emoji/grapheme and wcwidth analysis). Keep whichever existing acknowledgements are real.

8. **License.** Unchanged.

### Remove

Remove every claim not backed by the repo:

- "production-ready", v1.0, >95% coverage, 371 assertions;
- the performance section and the compatibility matrix;
- "total space utilization";
- the "mathematical foundations" formulas that don't match the code;
- the Dijkstra/philosophy flourish;
- the fake citation block;
- the roadmap items that duplicate beads;
- links to `plans/` and to planned docs that don't exist.

If you keep a short "Design principles" section, each principle must be true today or be phrased as a goal ("the goal is...").

### Verify

- The T02 doc-reference gate and README-examples gate have zero README entries in their baselines. Remove the fixed entries so the gates stay strict.
- `bb check`, or both suites if `bb check` still has an unrelated red gate, gives the same results as before, apart from the baseline shrinkage. Report the counts.
- Link-check the README's relative links: every relative path exists.
