# Specification snapshots

These unmodified snapshots are the primary specifications used by the text
foundation ADRs:

| Local file | Version | Upstream URL | SHA-256 |
| --- | --- | --- | --- |
| `uax11-44.html` | UAX #11 revision 44, Unicode 17.0.0 | `https://www.unicode.org/reports/tr11/tr11-44.html` | `a82167102910152a30e3a3c4984fd5f9b1d8caf47e5d53dc1e1aef050ba4f25e` |
| `uax29-47.html` | UAX #29 revision 47, Unicode 17.0.0 | `https://www.unicode.org/reports/tr29/tr29-47.html` | `130d4e0d3f9da41bf9ad84bf580b3b92edc83886457cbc5a53b432761fd2c6ed` |
| `uts51-29.html` | UTS #51 revision 29, Emoji 17.0 | `https://www.unicode.org/reports/tr51/tr51-29.html` | `3b648142de7a17bd66e4263c2ad584c826a893cd39083a41d9549c4c659279e1` |
| `ecma-48-5th-edition.pdf` | ECMA-48 fifth edition, June 1991 | `https://ecma-international.org/wp-content/uploads/ECMA-48_5th_edition_june_1991.pdf` | `9577ad2514c411584b274ef7a4b3238c80aa93defbb349b18b8c78f78873f450` |
| `xterm-ctlseqs.txt` | XTerm Control Sequences, patch 411, 2026-08-23 | `https://invisible-island.net/xterm/ctlseqs/ctlseqs.txt` | `364c1c1987c85b1c1135e57a93e9008054f46d09a338de8dcc66ea9a7c613709` |

The Unicode reports are Unicode Products governed by the Unicode Terms of Use
linked from their copyright notices. They are not covered by the Unicode
License v3 used for the data files under `reference/unicode/`. These copies
retain the upstream notices and are kept for reviewable project reference;
any redistribution must comply with those terms.

ECMA's text copyright policy permits an unchanged standard to be copied and
republished with its copyright notice and disclaimer. The PDF is retained
unchanged; see `https://ecma-international.org/policies/by-ipr/ecma-text-copyright-policy/`.
The xterm control-sequence source is distributed under the MIT/X Consortium
permission notice retained in upstream `ctlseqs.ms`; the rendered text keeps
its authorship header and is unmodified.

To refresh a snapshot, download the exact listed URL, verify its version or
revision header, update the checksum here, and review the relevant ADR against
the changed text. Do not replace a versioned Unicode URL with a `latest` URL.
