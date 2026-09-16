## Run R4-review — quality review of run R4 (beads 2.1, 2.2, 2.6)

A less capable worker implemented run R4 from `briefs/R4-phase2a.md` (read
it). Its report is `out/R4-phase2a.report.md` if present. Your job is one
pass of senior review with authority to fix, not a rewrite.

Commit range: `BASE..HEAD`, where `BASE` is the value given at the end of
this brief. Nothing outside that range is in scope.

1. Run `bb check` first. If it is red, fixing that is your first task.
2. Read the full diff of the range and every new or changed file, against
   CLAUDE.md (Laws, Rules 1, 2, 5, 6, 7, 8, 10, 11), the R4 brief, and the
   ADRs in `docs/adr/`. Check especially:
   - tests assert exact values, not "runs without error" (Rule 5);
   - no remaining catch-and-fabricate, `println` or `*err*` in `src/`
     outside the allow-lists, and the allow-lists carry honest reasons;
   - the render buffer never stores or emits half a wide cluster;
   - `find-dirty-regions` is linear;
   - docs, CHANGELOG and docstrings match the code (Law 2);
   - both runtimes behave the same.
   Spot-probe behaviour in a REPL (`clojure -M -e ...` and `bb -e ...`)
   rather than trusting the tests alone.
3. Fix every real defect directly, with red-green tests, committing once per
   logical fix with `Bead: <id>` of the bead concerned and your identity
   trailer. Do not restyle working code, and do not expand scope. If a
   defect is too large to fix well in this pass, file a bead and say so.
4. If a bead in the range was closed but its acceptance criteria are not
   met and you cannot fix it, reopen it (`bd update <id> --status open`)
   and say why.

Final report: the defects found (with severity), what you fixed (commit
SHAs), what you left open (bead IDs), and `bb check` counts on both runtimes.

## Range and extras

`BASE` is `5b386d8`. The worker's report is `reports/R4-phase2a.report.md`.

Two small items the worker flagged and left, which you should do:
- `CLAUDE.md` practical guidance still says loading every namespace printed
  18 reflection warnings; it is now zero. Correct that one line.
- Leave `reviews/2026-09-15/evidence/probe.clj` untouched; it is frozen.
