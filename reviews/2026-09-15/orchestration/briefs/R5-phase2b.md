## Run R5 — Phase 2.3, 2.4, 2.5 (three beads, in this order)

Earlier runs are done: Phase 1, the error-policy ADR (2.1), the grapheme render
buffer and its VT test emulator (2.2), and zero reflection warnings (2.6). Read
the render-buffer and error-policy ADRs first. All code from here on uses
`limner.text` for text and follows the error policy.

---

## Part 1 — PLAN 2.3: render loop with effects serialised and injectable (REVIEW E3)

Claim the 2.3 bead. Write an ADR for the render-loop concurrency model first.

### Required design

- **One writer.** Exactly one thread performs terminal output. Nothing does
  I/O inside `swap!`. `force-render!` and any state-watcher-triggered render
  become requests (a coalescing flag or a queue) that the writer thread
  serves.
- **Injection.** Clock, output writer (a `java.io.Writer` or `(fn [s])`),
  terminal-size source, and sleep function are all injectable, with
  production defaults. `stop!`, the frame loop and the resize check are all
  deterministic under test with a fake clock and a fake sleep.
- **Error boundary.** Implement the boundary exactly as the error-policy ADR
  specifies (`:on-error`, keep the last good frame, no printing).
- **`stop!` semantics.**
  - Idempotent.
  - Bounded wait.
  - Restores the terminal state through the injected writer.
  - Rethrows only a fatal error that ended the loop, never an error the
    boundary already handled.
  - Document all of this in the docstring.
- **`state/bind-to-render`.** Uses the request mechanism; it never calls
  output from the watcher thread.
- **Frame pacing.** Compute sleep from the injected clock. Render only when
  the state or the size changed, or when a render was requested. Document
  the idle behaviour: no busy loop, no 60 fps of identical frames.

### Tests

Deterministic. No real threads sleeping on wall-clock time; use promises and
latches where concurrency itself is under test.

- Concurrent `force-render!` from many threads gives no interleaved or
  duplicated output. Assert through the VT grid and a byte log.
- The error boundary behaves as specified.
- `stop!` is idempotent and bounded, with the correct rethrow behaviour.
- A resize triggers a full redraw.
- There is no output inside `swap!`. Use a fake atom or a watcher test, or
  structure the code so the writer is never reachable from swap functions,
  and test that property.

Stress test: 1000 iterations of concurrent requests. It must stay
deterministic enough to never flake, so run it 20 times in a row locally and
report that.

---

## Part 2 — PLAN 2.4: `limner.tty`, POSIX terminal input and capabilities (PLAN D7; REVIEW E4, E5)

Claim the 2.4 bead. Write an ADR for terminal I/O first.

### Scope

- **POSIX only.** On Windows, `limner.tty` throws a clear `ex-info` with
  `:limner.tty/unsupported-platform`.
- **Raw mode** via `stty` on `/dev/tty`. Save the current settings with
  `stty -g`, apply raw settings, and always restore:
  - through `with-raw-mode` (a macro or a function taking a thunk) using
    `try`/`finally`;
  - plus a JVM shutdown hook as a backstop;
  - document the restore guarantees and their limits (for example SIGKILL).
  - Babashka provides `babashka.process`; the JVM has `ProcessBuilder`. Pick
    one portable implementation, or a small shim, and justify the choice.
- **Key reader** over an injectable `InputStream` (a byte source).
  - Decodes UTF-8 incrementally; a multi-byte character can straddle reads.
  - Parses xterm key sequences per `reference/specs/xterm-ctlseqs.txt`:
    - arrows, home/end, insert/delete, page up/down, F1–F12;
    - modifier encodings `CSI 1;m X` and `CSI n;m ~`;
    - Shift-Tab `CSI Z`;
    - Alt (ESC-prefix);
    - Ctrl letters;
    - Backspace (DEL and BS);
    - Enter (CR, LF);
    - bracketed paste (`CSI 200~` … `CSI 201~`) as a single paste event;
    - SGR mouse (`CSI < b;x;y M/m`), including scroll, drag and modifier
      bits.
  - **Bare ESC vs escape-sequence start:** resolve with an injectable timeout
    and clock (default 50 ms; cite the precedent you choose).
  - **Unknown sequences** become an `{:type :unknown :raw "…"}` event, never
    dropped and never an exception.
  - **Events** are plain maps. Document the event schema in the ADR.
- **Relationship to `limner.events`:** `limner.events/parse-key` must either
  delegate to the new parser or be deprecated in favour of it. Decide, and
  keep existing event tests passing or update them with reasons. REVIEW
  probes 7, 7b and 7c become red tests first. Shift-Tab must work with
  `handle-tab-navigation`.
- **Terminal size:** `stty size </dev/tty` with an injectable source. Remove
  the silent clamping from 2.1-era `get-terminal-size`. Undetectable size is
  an explicit `nil` or an `ex-info`, depending on the API; decide and
  document. Callers choose their fallback.
- **Capability detection additions** in `limner.terminal`:
  - `NO_COLOR` (https://no-color.org: any non-empty value disables colour);
  - `FORCE_COLOR` / `CLICOLOR_FORCE`;
  - TTY detection (`System/console`, plus an injectable check);
  - `COLORTERM`/`TERM` as today.
  - Document the precedence order in the ADR, with sources, and test the
    full precedence matrix.

### Tests

- Byte-stream tests for every key and mouse form, including UTF-8 straddling
  reads, paste containing escape sequences, and the ESC timeout under a fake
  clock.
- Raw mode tested against a fake `stty` runner: restore on success, on
  exception and on thread interrupt.
- A real-tty smoke test can only run manually. Add it as a bb task
  `bb tty-smoke`, not part of `bb check`, and say so.

---

## Part 3 — PLAN 2.5: event system without unsafe concurrency (REVIEW E1, E2)

Claim the 2.5 bead.

- **Remove blocking operations inside go blocks.** No `<!!`, `>!!` or
  `alts!!` anywhere reachable from a `go` block.
- **Handlers run on a real thread** (a dedicated executor or `thread`), not
  on the go dispatch pool.
- **Handler timeouts are defined honestly.** A timed-out handler is either
  cancelled cooperatively (interrupt, documented) or keeps running with its
  result discarded. Choose one and document it in an ADR section or in the
  concurrency ADR from 2.3.
- **State updates use `swap!`** with a function of the current state, so no
  update is lost when another writer changes the atom. REVIEW E2 becomes a
  red test first: a concurrent external `swap!` during event processing must
  not be lost.
- **Stats** are atoms updated atomically; `stop!` is idempotent and bounded.
- **core.async stays or goes.** If the requirements are simpler without
  core.async (a `LinkedBlockingQueue` plus one worker thread), removing the
  dependency is acceptable. It would simplify babashka parity. Decide with
  evidence, update `deps.edn`/`bb.edn`, and record the decision in
  CHANGELOG.
- **Integration.** `limner.tty` events feed the event queue: provide a
  documented `start-input-loop!` wiring `limner.tty` → the event system →
  the state atom → render requests, and an example in `examples/` that runs
  under bb.
- **README.** If `limner.events`, `limner.render` and `limner.state` now meet
  the core bar (ADRs, fail-loud, deterministic tests, grapheme-correct), move
  them from experimental to core. Otherwise keep them experimental and list
  what's missing in a bead.

All three beads: `bb check` passes on both runtimes. Report the counts, the
stress-test repetitions and every decision taken.
