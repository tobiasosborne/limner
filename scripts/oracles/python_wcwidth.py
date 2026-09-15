"""Emit pinned jquast/wcwidth scalar and Emoji 17 sequence measurements."""

from importlib.metadata import version
from pathlib import Path
import sys

from wcwidth import list_versions, wcwidth, wcswidth


UNICODE_VERSION = "15.1.0"


def emoji_cases(path: Path):
    for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line or line[0] not in "0123456789ABCDEF":
            continue
        code_points, tail = line.split(";", 1)
        status = tail.strip().split()[0]
        text = "".join(chr(int(value, 16)) for value in code_points.split())
        yield line_number, status, text


def main(argv):
    if len(argv) != 4:
        raise SystemExit("usage: python_wcwidth.py EMOJI-TEST CP.tsv SEQUENCES.tsv")
    emoji_path, code_point_path, sequence_path = map(Path, argv[1:])
    if UNICODE_VERSION not in list_versions():
        raise SystemExit(f"wcwidth {version('wcwidth')} lacks Unicode {UNICODE_VERSION}")

    with code_point_path.open("w", encoding="utf-8") as output:
        output.write(f"# package=wcwidth=={version('wcwidth')}\n")
        output.write(f"# unicode={UNICODE_VERSION}\n")
        for code_point in range(0x110000):
            if not 0xD800 <= code_point <= 0xDFFF:
                output.write(f"{code_point:06X}\t"
                             f"{wcwidth(chr(code_point), unicode_version=UNICODE_VERSION)}\n")

    with sequence_path.open("w", encoding="utf-8") as output:
        output.write(f"# package=wcwidth=={version('wcwidth')}\n")
        output.write(f"# unicode={UNICODE_VERSION}\n")
        for line_number, status, text in emoji_cases(emoji_path):
            output.write(f"{line_number}\t{status}\t"
                         f"{wcswidth(text, unicode_version=UNICODE_VERSION)}\n")


if __name__ == "__main__":
    main(sys.argv)
