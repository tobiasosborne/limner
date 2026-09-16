## Run R6-review — quality review of run R6 (Phase 3 core components)

A less capable worker implemented run R6 from `briefs/R6-phase3-core.md`
(read it); its report is `reports/R6-phase3-core.report.md`. Your job is one
pass of senior review with authority to fix, not a rewrite.

Commit range: `e8334b4..HEAD`. Nothing outside that range is in scope, and
the experimental namespaces (`render`, `events`, `state`, `streaming`,
`syntax`, `markdown`) are out of scope.

1. Run `bb check` first. If it is red, fixing that is your first task.
2. Re-run the REVIEW F1–F9 probes by hand (`evidence/probe.clj` is frozen;
   reproduce the relevant probes in `clojure -M -e` and `bb -e`) against
   the migrated components: borders, layout, table, list, input, progress,
   statusbar, panel. Every rendered line of every component must measure
   exactly the requested width with `limner.text/width` for CJK, an emoji
   ZWJ sequence, pre-coloured text, and an OSC 8 link. A coloured title in
   a coloured border must keep both styles. Fix what fails, red test first.
3. Read the full diff against CLAUDE.md (Rules 1, 2, 5, 6, 7, 8, 10), ADRs
   0003 and 0004, and the R6 brief. Check especially:
   - tests assert exact rendered strings (Rule 5), and the property tests
     actually generate wide and zero-width clusters, not only ASCII;
   - no component measures with `count`/`subs`/`nth`/`format` on visible
     text, and the new grep gate's allow-list carries honest reasons;
   - error classes added to ADR 0004 match the code;
   - the README "Status" section and `docs/api/*.md` describe exactly what
     the code now does, no more (Law 2);
   - CHANGELOG lists every breaking change the diff actually contains;
   - the two runtimes agree (spot-probe both).
4. Fix every real defect directly with red-green tests, committing once per
   logical fix with `Bead: <id>` and your identity trailer. Do not restyle
   working code or expand scope. If a defect is too large for this pass,
   file a bead and say so. If a closed bead's acceptance criteria are not
   met and you cannot fix it, reopen it and say why.

Final report: defects found (with severity), what you fixed (commit SHAs),
what you left open (bead IDs), and `bb check` counts on both runtimes.
