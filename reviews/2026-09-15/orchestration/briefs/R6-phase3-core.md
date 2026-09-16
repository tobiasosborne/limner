## Run R6 — Phase 3 core components (eight beads, in this order)

Phases 1 and 2 (beads 2.1, 2.2, 2.6) are done. The foundation is
`limner.unicode*`, `limner.ansi*` and `limner.text` (ADRs 0001–0003), the
fail-loud error policy (ADR 0004) and the grapheme render buffer (ADR 0005).
Read ADRs 0003 and 0004 before starting. Beads 2.3, 2.4 and 2.5 are
deliberately out of scope for this project and are not blockers for you.

Do these beads in this order, each one claimed, implemented, gated, closed
and committed before the next (they form a dependency chain in beads):

1. `limner-3-borders`
2. `limner-3-layout`
3. `limner-3-table`
4. `limner-3-list`
5. `limner-3-input`
6. `limner-3-progress`
7. `limner-3-statusbar`
8. `limner-3-panel`

Each bead's body (`bd show <id>`) has Background, Fix and Acceptance
sections; those are the specification. The REVIEW F1–F9 findings and the
`evidence/probe.clj` probes named in each bead are the first red tests.

### Rules that apply to every bead in this run

- **All text measurement, slicing, padding, truncation and wrapping goes
  through `limner.text`** (CLAUDE.md Rule 8). After this run, no component
  namespace calls `count`, `subs`, `nth`, `format "%Ns"`, `.length` or
  `.substring` on user-visible text, and no component owns a private ANSI
  regex. Add a grep gate for this in `test/limner/error_gates_test.clj` (or
  a sibling namespace) with an allow-list carrying reasons.
- **Styles compose through the styled-text model**, never by string
  concatenation with `ESC[0m` (REVIEW B1). A coloured title inside a
  coloured border keeps both styles.
- **Rendered output is compared as an exact string** (Rule 5), with escape
  sequences made visible in failure messages. Every component gets at least
  one golden test containing CJK, an emoji ZWJ sequence, and pre-coloured
  text, and a `test.check` property that every rendered line has the
  requested display width.
- **Invalid input throws** per ADR 0004 with a `:limner/error` class; add
  any new classes to the ADR 0004 table in the same commit.
- **Split files over 200 lines of code along real seams** when you touch
  them (Rule 11), but do not rename public vars without recording the
  break in CHANGELOG.
- **Do not change** `limner.render`, `limner.events`, `limner.state`,
  `limner.streaming`, `limner.syntax` or `limner.components.markdown`
  beyond what a component bead strictly requires.
- **Docs in lockstep** (Law 2): docstrings, `docs/api/*.md` for the
  namespace, and a CHANGELOG line per bead. README examples that call a
  function whose behaviour changed must still run (`readme_examples_test`).
- **`bb check` green on both runtimes before each commit.** Also run
  `bb bench-check` once at the end of the run and report it.
- After the last bead, update the README "Status" section: the component
  limitations listed there are gone if, and only if, the golden tests prove
  it. Say exactly what remains.

Stop the run and report if a bead cannot be finished well; later beads
depend on earlier ones.
