# Text performance baselines and budgets

These measurements are engineering guardrails, not user-facing speed claims.
Run the full corpus with `clojure -M:text-bench` and `bb benchmark:text`. Run
the shorter regression check with `bb bench-check`; it executes the JVM and
babashka checks serially and is intentionally not part of `bb check` because
host load makes timing noisy.

## Method and environment

Each result is the median of five independently timed rounds after warm-up;
the spread is the minimum–maximum round rate. Small corpora use 600 calls per
JVM round and 60 per babashka round. The exact 10 KiB UTF-8 corpus uses 10 and
1 respectively. Warm-up uses 100 calls for small corpora, 30 for medium
corpora, and 5 for 10 KiB text. Rates are complete operation calls per second.

- JVM: Clojure 1.11.1, Java 21.0.12.
- babashka: 1.12.212, SCI Clojure 1.12.3 compatibility, Java 25 runtime.
- Machine and date: the WSL development machine, 2026-09-15.
- Corpora: 80-unit ASCII; marked Latin; 40 CJK characters (80 columns); emoji
  ZWJ and flags; Devanagari conjuncts; 160 differently styled spans; and
  exactly 10,240 UTF-8 bytes of mixed text.
- Operations: `width`, `limner.unicode.grapheme/clusters`, `from-ansi`,
  `to-ansi`, `truncate`, and `wrap`.

## Measured throughput

| Corpus | Operation | JVM median calls/s | JVM spread | bb median calls/s | bb spread |
| --- | --- | ---: | ---: | ---: | ---: |
| ASCII | width | 215,216.6 | 76,490.9–406,533.0 | 100,637.2 | 96,664.9–102,388.9 |
| ASCII | clusters | 102,347.6 | 58,412.0–170,571.5 | 8,399.3 | 3,172.3–8,766.4 |
| ASCII | from-ansi | 24,978.4 | 15,765.3–51,334.9 | 10,694.4 | 10,213.4–11,205.1 |
| ASCII | to-ansi | 101,774.7 | 46,194.2–117,028.0 | 35,364.8 | 28,396.9–39,302.9 |
| ASCII | truncate | 3,482.3 | 2,447.5–6,829.2 | 938.7 | 856.1–968.0 |
| ASCII | wrap | 3,255.0 | 2,889.5–3,257.9 | 454.8 | 448.8–461.5 |
| Latin + marks | width | 15,104.3 | 10,886.3–15,747.2 | 1,121.3 | 1,097.3–1,133.7 |
| Latin + marks | clusters | 12,925.2 | 8,898.1–14,151.2 | 658.4 | 627.2–659.7 |
| Latin + marks | from-ansi | 38,843.1 | 35,775.2–46,316.0 | 11,174.1 | 11,060.2–11,336.3 |
| Latin + marks | to-ansi | 64,554.0 | 57,959.9–70,858.3 | 37,038.3 | 34,444.2–37,403.1 |
| Latin + marks | truncate | 2,412.3 | 2,121.5–2,476.6 | 232.4 | 224.2–234.7 |
| Latin + marks | wrap | 1,192.3 | 1,168.8–1,205.5 | 130.8 | 126.6–136.2 |
| CJK | width | 298,004.1 | 274,801.4–501,463.9 | 99,321.1 | 47,344.6–111,296.2 |
| CJK | clusters | 242,336.6 | 231,036.3–355,156.3 | 14,147.2 | 12,866.7–14,813.3 |
| CJK | from-ansi | 177,857.2 | 171,155.1–179,404.8 | 11,565.8 | 10,187.6–11,709.1 |
| CJK | to-ansi | 229,323.3 | 218,611.6–257,224.3 | 41,410.6 | 41,104.2–42,513.9 |
| CJK | truncate | 13,057.4 | 10,807.6–13,515.6 | 1,652.0 | 1,442.6–1,687.8 |
| CJK | wrap | 6,054.0 | 5,454.0–6,245.1 | 840.7 | 788.7–851.0 |
| Emoji ZWJ | width | 13,559.0 | 10,538.0–13,881.2 | 878.2 | 839.6–891.4 |
| Emoji ZWJ | clusters | 21,283.4 | 18,415.1–21,864.6 | 662.0 | 631.1–665.9 |
| Emoji ZWJ | from-ansi | 132,802.4 | 125,413.1–135,269.5 | 10,424.6 | 10,218.3–11,136.5 |
| Emoji ZWJ | to-ansi | 206,804.3 | 193,592.6–211,685.5 | 33,394.5 | 26,913.0–37,636.3 |
| Emoji ZWJ | truncate | 3,381.7 | 3,168.9–3,474.7 | 197.7 | 196.3–199.9 |
| Emoji ZWJ | wrap | 1,909.7 | 1,891.0–1,930.8 | 118.0 | 116.3–118.6 |
| Devanagari | width | 7,533.5 | 6,039.7–8,776.0 | 591.7 | 569.0–601.5 |
| Devanagari | clusters | 9,467.7 | 7,469.2–10,365.4 | 335.0 | 330.7–340.3 |
| Devanagari | from-ansi | 99,683.3 | 98,873.4–101,056.7 | 10,473.0 | 9,764.3–10,682.2 |
| Devanagari | to-ansi | 140,019.5 | 137,655.1–141,026.6 | 31,193.1 | 28,580.9–33,573.9 |
| Devanagari | truncate | 1,435.7 | 1,416.7–1,447.1 | 132.8 | 130.4–135.1 |
| Devanagari | wrap | 732.7 | 730.2–733.9 | 79.3 | 77.2–80.1 |
| Many spans | width | 4,965.0 | 4,798.1–4,980.4 | 290.6 | 288.0–295.6 |
| Many spans | clusters | 9,925.8 | 9,500.7–10,004.6 | 256.2 | 253.1–265.6 |
| Many spans | from-ansi | 1,171.4 | 1,114.2–1,180.2 | 82.8 | 78.2–83.2 |
| Many spans | to-ansi | 4,384.0 | 4,249.2–4,395.5 | 649.7 | 624.9–673.1 |
| Many spans | truncate | 496.9 | 491.5–500.5 | 48.8 | 47.6–49.4 |
| Many spans | wrap | 275.9 | 273.6–276.6 | 29.0 | 28.5–29.7 |
| Mixed 10 KiB | width | 499.3 | 485.1–501.1 | 32.0 | 31.1–33.0 |
| Mixed 10 KiB | clusters | 871.3 | 643.4–883.3 | 21.9 | 20.6–22.3 |
| Mixed 10 KiB | from-ansi | 13,231.0 | 11,683.6–13,689.3 | 4,987.5 | 4,468.3–5,577.2 |
| Mixed 10 KiB | to-ansi | 14,112.3 | 12,547.1–14,496.9 | 8,257.6 | 6,802.7–8,568.9 |
| Mixed 10 KiB | truncate | 107.2 | 98.0–109.2 | 7.1 | 6.9–7.5 |
| Mixed 10 KiB | wrap | 57.3 | 55.8–57.6 | 4.2 | 4.1–4.3 |

## Width budgets

The ratios below use the benchmark-local copy of the removed legacy
`visible-width`. A slowdown below 1 means the new implementation is faster.

| Budget | Required | JVM measured | babashka measured |
| --- | ---: | ---: | ---: |
| ASCII width slowdown | ≤1.00× | 0.503× | 0.712× |
| Mixed 80-column-line slowdown | ≤3.00× JVM, ≤2.00× bb | 1.484× | 1.144× |
| CJK width speedup on babashka | ≥10× | 6.86× (informational) | 59.77× |

The pre-optimisation measurements were 39.15×/184.79× ASCII slowdown
(JVM/babashka), 3.35×/4.63× mixed slowdown, and 0.12×/0.25× CJK speedup.
Those misses justified a flat BMP property word, whole-run fast paths, and a
one-pass segment-and-measure loop. The earlier “10× CJK on babashka” estimate
proved achievable once a pure CJK run avoided per-code-point property lookup.

`bb bench-check` uses deliberately generous noisy-host tripwires: ASCII may
reach 1.50× legacy, babashka CJK must remain at least 8× faster, and mixed text
may reach 3.50× on the JVM or 2.50× on babashka. These are regression-check
margins, not replacements for the budgets above.

## Table construction cost

Five cold babashka subprocesses measured requiring `limner.unicode`. Before
the flat BMP table (commit `a392d33`) the times were 21.815, 23.854, 22.079,
23.381, and 22.175 ms (median 22.175 ms). With the 65,536-entry packed `int`
table and eight 4,352-entry supplementary page indexes they were 109.902,
110.041, 111.197, 109.979, and 110.474 ms (median 110.041 ms). The lookup
tables therefore add about 87.9 ms of cold load time and 392 KiB of primitive
payload in exchange for O(1) BMP properties and bounded linear lookup inside
one 256-code-point supplementary page.
