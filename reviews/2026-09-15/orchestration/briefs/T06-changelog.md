## T06 — PLAN 0.6: CHANGELOG.md

Claim the beads issue for plan item 0.6. It carries the `needs-owner` label
because of the tag. **You do not create any tag.** The orchestrator creates
the local tag after review, and the owner pushes it. Your part is
`CHANGELOG.md` only.

Create `CHANGELOG.md` in the "Keep a Changelog" style: newest first, and
sections Added / Changed / Deprecated / Removed / Fixed / Security, used only
when non-empty. Get every entry from `git log`, and verify it against the
diff. Do not paraphrase from memory.

1. **`## [Unreleased]`**: everything after `01592b2`, written for library
   users rather than agents:
   - the new `bb test` and `bb check` tasks;
   - the honest README;
   - the `limner` namespace is deprecated;
   - the historical docs are archived (if T04 has landed; check `git log`);
   - experimental namespaces are labelled.

   Internal tooling (beads, CLAUDE.md, AGENTS.md, review files) gets at most
   one line under "Changed", or none. Users do not care.

2. **`## [0.1.0] - 2026-09-14`**: the legacy baseline at commit `01592b2`.
   One short paragraph saying this is the first tagged point: the codebase as
   written in 2025, plus the 2026-09-14 fixes. Then the user-visible fixes
   from 2026-09-14, from `git log 7a1b8bc..01592b2`:
   - the `deps.edn` git-dependency fix: #1, reported by @pmonks;
   - grapheme-aware `visible-width`: #2, reported by @pmonks; say plainly
     that the full fix is in progress;
   - `supports-ansi-colors?` returns a boolean;
   - the `bb benchmark` task fix;
   - the `limner` namespace is loadable from `src/`;
   - the `limner/syntax` nil-colour fix;
   - the shadowing warnings are silenced.

   Give each entry its short commit SHA.

3. At the bottom, add link references:
   - `[Unreleased]`: `https://github.com/tobiasosborne/limner/compare/v0.1.0...HEAD`
   - `[0.1.0]`: `https://github.com/tobiasosborne/limner/releases/tag/v0.1.0`

   Note in your report that these links resolve only once the owner pushes the
   tag.

4. Add a "Changelog" item to CLAUDE.md "Practical guidance": every
   user-visible change adds a line under `[Unreleased]`, and reporters are
   credited as `(#N, reported by @handle)`. Law 2 already requires this; make
   the format concrete.

5. If README's Installation section mentions tags, update it to say a `v0.1.0`
   tag marks the legacy baseline. Do this only as a conditional sentence, e.g.
   "once published", since the tag is not pushed yet. Otherwise leave the
   README alone.

Verify: `bb check` passes; report the counts. In the bead, record that the tag
step is pending the orchestrator and owner. Close the bead only if bd supports
leaving a note for the tag; otherwise leave it open with a clear comment.
