# Limner Code Review - Refactoring TODO List

**Generated:** 2025-12-01
**Status:** Planning Phase

This document tracks refactoring tasks identified during code review. Tasks are organized by priority: Critical (must fix), Important (should fix soon), and Nice to Have (quality improvements).

---

## 🔴 Critical Issues (Must Fix)

These issues can cause data corruption, crashes, or broken functionality.

### 1. Fix Color System in core.clj
**File:** `src/limner/core.clj:6-15`
**Issue:** Only 6 colors supported in 2025, no 256-color or RGB support
**Tasks:**
- [ ] Add full 256-color palette support (`\u001B[38;5;Nm` format)
- [ ] Add RGB/truecolor support (`\u001B[38;2;R;G;Bm` format)
- [ ] Create color builder functions: `rgb`, `color-256`, `color-8`
- [ ] Add color validation with proper error messages
- [ ] Update all components to use new color API
- [ ] Add tests for color edge cases (invalid colors, nil handling)

**Estimated Impact:** High - affects all visual rendering

---

### 2. Fix Unicode/String Width Handling
**File:** `src/limner/core.clj:22-25`
**Issue:** `visible-length` breaks with emoji, CJK characters, combining chars
**Tasks:**
- [ ] Research Clojure Unicode width libraries (or port wcwidth)
- [ ] Implement proper grapheme cluster counting
- [ ] Handle wide characters (CJK - count as 2)
- [ ] Handle zero-width characters (combining marks, ZWJ)
- [ ] Handle emoji with variation selectors and ZWJ sequences
- [ ] Update `borders.clj` to use corrected width calculations
- [ ] Add comprehensive Unicode tests (emoji, Chinese, Arabic, etc.)

**Estimated Impact:** Critical - affects layout and alignment everywhere

---

### 3. Replace Raw Thread Usage with Proper Concurrency
**File:** `src/limner/render.clj:332-353`
**Issue:** Raw Java threads, no thread pools, potential leaks
**Tasks:**
- [ ] Replace `Thread.` with `future` or `core.async`
- [ ] Implement proper shutdown mechanism (no timeout guessing)
- [ ] Add thread pool for render operations
- [ ] Implement backpressure handling for rapid state updates
- [ ] Add proper exception handling in render thread
- [ ] Use `java.util.concurrent.CountDownLatch` for coordination
- [ ] Test thread cleanup on abnormal termination
- [ ] Document thread safety guarantees

**Estimated Impact:** High - prevents thread leaks and crashes

---

### 4. Fix or Remove state.clj
**File:** `src/limner/state.clj` (entire file)
**Issue:** Over-engineered, race conditions in undo/redo, unsafe metadata mutation
**Tasks:**

**Option A (Recommended): Simplify**
- [ ] Remove custom state management, use plain atoms
- [ ] Move undo/redo to separate library or remove entirely
- [ ] Keep only essential watchers functionality
- [ ] Reduce file to <100 lines

**Option B (If keeping): Fix it**
- [ ] Make undo/redo atomic using STM (refs + transactions)
- [ ] Use proper thread-safe data structure for history
- [ ] Add locks around metadata mutation
- [ ] Add comprehensive concurrency tests
- [ ] Document thread safety model

**Decision needed:** Simplify vs Fix?

**Estimated Impact:** Medium-High - affects app state reliability

---

### 5. Add Terminal Capability Detection
**Files:** All rendering code
**Issue:** Assumes all terminals support ANSI, Unicode, mouse, etc.
**Tasks:**
- [ ] Add terminfo/termcap integration (use Java `System.getenv("TERM")`)
- [ ] Detect ANSI color support (check `TERM`, `COLORTERM` env vars)
- [ ] Detect Unicode support (check locale, `LANG` env var)
- [ ] Detect mouse support (query terminal capabilities)
- [ ] Create capability registry/map
- [ ] Gracefully degrade when features unavailable
- [ ] Add ASCII fallback for box drawing characters
- [ ] Test on various terminals (xterm, tmux, Windows Terminal, etc.)

**Estimated Impact:** High - prevents broken rendering on different terminals

---

## 🟡 Important Issues (Should Fix Soon)

These issues cause poor user experience or maintenance problems.

### 6. Add Comprehensive Error Handling
**Files:** All modules
**Issue:** No error handling, crashes propagate to user
**Tasks:**
- [ ] Wrap render loop in try/catch with recovery
- [ ] Add error boundaries for component rendering
- [ ] Validate inputs in public APIs (use `:pre` conditions)
- [ ] Handle terminal resize gracefully (catch exceptions)
- [ ] Add error logging (with configurable output)
- [ ] Create error recovery strategies:
  - [ ] Render errors → show error panel
  - [ ] Layout errors → fallback to simple stack
  - [ ] Terminal errors → attempt recovery or clean exit
- [ ] Test error scenarios systematically

**Estimated Impact:** Medium - prevents crashes, improves reliability

---

### 7. Make Event Handling Asynchronous
**File:** `src/limner/events.clj:318-410`
**Issue:** Synchronous event handling blocks UI
**Tasks:**
- [ ] Create event queue (use `core.async` channel)
- [ ] Process events asynchronously
- [ ] Add event handler timeout mechanism
- [ ] Support async event handlers (return promises/channels)
- [ ] Add event batching for rapid input
- [ ] Implement proper event cancellation
- [ ] Add event handler error isolation
- [ ] Document async event model

**Estimated Impact:** Medium - prevents UI freezing

---

### 8. Add Performance Testing and Benchmarks
**Files:** New `bench/` directory
**Issue:** No performance measurements, optimizing blind
**Tasks:**
- [ ] Set up Criterium for benchmarking
- [ ] Benchmark render loop at various FPS targets
- [ ] Benchmark diff algorithm with different change patterns
- [ ] Benchmark layout calculations for various sizes
- [ ] Benchmark string operations in borders.clj
- [ ] Profile memory allocation hotspots
- [ ] Create performance regression tests
- [ ] Document performance characteristics in README
- [ ] Set performance budgets (e.g., "60 FPS on X scenario")

**Estimated Impact:** Medium - enables informed optimization

---

### 9. Handle Terminal Resizing Properly
**File:** `src/limner/render.clj:194-203, 226-235`
**Issue:** No resize detection, buffers not resized dynamically
**Tasks:**
- [ ] Add SIGWINCH signal handler (terminal resize signal)
- [ ] Implement buffer resizing without recreation
- [ ] Add buffer pooling (reuse old buffers)
- [ ] Trigger layout recalculation on resize
- [ ] Add resize debouncing (don't thrash on rapid resizes)
- [ ] Test resize during active rendering
- [ ] Handle resize to smaller terminal (clip content)
- [ ] Document resize behavior

**Estimated Impact:** Medium - critical for real-world usage

---

### 10. Improve Test Coverage - Edge Cases
**Files:** All `test/**/*_test.clj`
**Issue:** Only happy path tested, no edge case or failure testing
**Tasks:**
- [ ] Add tests for overlapping ANSI codes
- [ ] Add tests for malformed escape sequences
- [ ] Add tests for buffer overflow scenarios
- [ ] Add tests for concurrent operations
- [ ] Add tests for terminal resize during render
- [ ] Add property-based tests (use `test.check`)
- [ ] Add tests for Unicode edge cases
- [ ] Add tests for error recovery paths
- [ ] Achieve >80% code coverage
- [ ] Add integration tests (full render pipeline)

**Estimated Impact:** Medium - catches bugs before users do

---

## 🟢 Nice to Have (Quality Improvements)

These improve performance, maintainability, or developer experience.

### 11. Optimize String Operations in borders.clj
**File:** `src/limner/borders.clj:44-53, 60-64`
**Issue:** Inefficient string concatenation and padding
**Tasks:**
- [ ] Replace `(apply str (repeat n " "))` with `StringBuilder` or `StringBuffer`
- [ ] Pre-allocate string buffers for box drawing
- [ ] Cache common padding strings (memoize)
- [ ] Use transducers for string building pipeline
- [ ] Profile before/after performance
- [ ] Consider using Java's `String.repeat()` (Java 11+)

**Estimated Impact:** Low-Medium - reduces GC pressure

---

### 12. Implement Buffer Pooling
**File:** `src/limner/render.clj:18-23, 40-45`
**Issue:** Buffers created and destroyed frequently, GC pressure
**Tasks:**
- [ ] Create buffer pool (object pool pattern)
- [ ] Implement buffer recycling
- [ ] Add pool size limits and eviction
- [ ] Track buffer pool statistics
- [ ] Test pool under load
- [ ] Document pool configuration options

**Estimated Impact:** Low - reduces allocation overhead

---

### 13. Remove Atom Abuse in layout.clj
**File:** `src/limner/layout.clj:95-100`
**Issue:** Using atoms for local iteration state
**Tasks:**
- [ ] Rewrite using `loop/recur` with accumulator
- [ ] Remove all atoms used for local state
- [ ] Use functional iteration patterns consistently
- [ ] Verify no performance regression

**Estimated Impact:** Low - code clarity

---

### 14. Improve Documentation
**Files:** `README.md`, all source files
**Issue:** Missing critical operational details
**Tasks:**
- [ ] Document thread safety guarantees (or lack thereof)
- [ ] Document memory usage patterns
- [ ] Document performance characteristics
- [ ] Document terminal compatibility matrix
- [ ] Add error handling guide for users
- [ ] Add architecture decision records (ADRs)
- [ ] Add inline examples in docstrings
- [ ] Create troubleshooting guide
- [ ] Add performance tuning guide

**Estimated Impact:** Low - helps users and contributors

---

### 15. Optimize Render Loop Sleep
**File:** `src/limner/render.clj:350`
**Issue:** 1ms sleep burns CPU unnecessarily
**Tasks:**
- [ ] Calculate actual sleep time based on target FPS
- [ ] Use condition variables for state change notifications
- [ ] Only render when state actually changes
- [ ] Add adaptive sleep (longer when idle)
- [ ] Measure CPU usage before/after

**Estimated Impact:** Low - reduces CPU usage

---

### 16. Add Input Validation
**Files:** All public API functions
**Issue:** No validation, garbage in → crash out
**Tasks:**
- [ ] Add `:pre` and `:post` conditions to public functions
- [ ] Use spec for API validation (optional)
- [ ] Validate color values
- [ ] Validate dimensions (width/height > 0)
- [ ] Validate constraint values
- [ ] Return helpful error messages
- [ ] Test invalid inputs systematically

**Estimated Impact:** Low-Medium - better error messages

---

### 17. Enhance events.clj Keyboard Support
**File:** `src/limner/events.clj:6-72`
**Issue:** Missing modifier combinations
**Tasks:**
- [ ] Add `Ctrl-Shift-X` combo support
- [ ] Add `Alt-Shift-X` combo support
- [ ] Add `Ctrl-Alt-X` combo support
- [ ] Add support for more function keys (F13-F24)
- [ ] Test key combos across different terminals
- [ ] Document supported key combinations

**Estimated Impact:** Low - power user feature

---

## 📊 Progress Tracking

### Priority Summary
- **Critical:** 5 tasks (Must fix before v1.0)
- **Important:** 5 tasks (Fix before production use)
- **Nice to Have:** 7 tasks (Quality and polish)

### Estimated Effort
- **Critical:** ~3-4 weeks (if done properly)
- **Important:** ~2-3 weeks
- **Nice to Have:** ~1-2 weeks

**Total:** ~6-9 weeks for full refactoring

---

## 🎯 Recommended Order of Attack

### Phase 1: Stability (Week 1-2)
1. Fix Unicode handling (#2) - breaks everything
2. Fix color system (#1) - needed for components
3. Add error handling (#6) - prevent crashes

### Phase 2: Concurrency (Week 3-4)
4. Replace raw threads (#3) - architectural
5. Fix or simplify state.clj (#4) - correctness
6. Make events async (#7) - UX

### Phase 3: Compatibility (Week 5-6)
7. Add terminal detection (#5) - real-world usage
8. Handle terminal resizing (#9) - critical UX
9. Improve test coverage (#10) - catch regressions

### Phase 4: Polish (Week 7-9)
10. Performance benchmarks (#8) - measure
11. Optimize hotspots (#11, #12, #15) - improve
12. Documentation (#14) - share knowledge
13. Nice to haves (#13, #16, #17) - quality

---

## 📝 Notes

### Breaking Changes Expected
- Color API will change (critical #1)
- State management API may change significantly (#4)
- Event handling API may change (#7)
- Buffer creation API may change (#12)

### Testing Strategy
- Add tests BEFORE refactoring each module
- Keep old tests passing during refactoring
- Add new tests for edge cases
- Use property-based testing where appropriate

### Decision Points
- **state.clj:** Simplify vs Fix? → Recommend simplify
- **Color system:** Support which color modes? → All three (8, 256, RGB)
- **Event system:** core.async vs other? → core.async recommended
- **Buffer pooling:** Worth the complexity? → Profile first

---

## 🔗 Related Documents
- Original code review: (this document based on verbal review)
- Architecture docs: `README.md`
- Test coverage report: (TODO: generate)
- Performance benchmarks: (TODO: create)

---

**Next Steps:**
1. Review and prioritize this list
2. Decide on state.clj approach (simplify vs fix)
3. Create feature branches for each critical task
4. Start with Phase 1 (Stability)
5. Set up CI/CD for automated testing

**Questions? Concerns?**
Discuss in project issues or reach out to maintainers.
