"""Normalize the extracted 7-sinf Jahon tarixi textbook text.

The PDF was extracted with HTML entities and two different font encodings:
clean pages use plain Uzbek Latin, while a large part of the book uses a
legacy subset font where a, e, o, p, x were encoded as a-grave, a-ring,
i-circumflex, eth and o-tilde. Both are repaired here.

Input : source/7_jahon_tarixi.raw.txt
Output: source/7_jahon_tarixi.txt  (<<<PAGE n>>> markers + clean text)
"""
from __future__ import annotations

import html
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "books" / "7-sinf_Jahon_tarixi" / "source" / "Jahon_tarixi.raw.txt"
OUT = ROOT / "books" / "7-sinf_Jahon_tarixi" / "source" / "Jahon_tarixi.txt"
PAGE_RE = re.compile(
    r"={10,}\s*DARSLIK:\s*(?P<book>[^|]+?)\s*\|\s*BET:\s*(?P<page>\d+)\s*={10,}"
)

# legacy subset-font glyph -> real Uzbek letter
LEGACY_FONT = str.maketrans(
    {
        "\u00e0": "a",  # a-grave
        "\u00ee": "o",  # i-circumflex
        "\u00e5": "e",  # a-ring
        "\u00f5": "x",  # o-tilde
        "\u00f0": "p",  # eth
        "\u00c0": "A",
        "\u00ce": "O",
        "\u00c5": "E",
        "\u00d5": "X",
        "\u00d0": "P",
    }
)

TYPOGRAPHY = {
    "\u00e2\u20ac\u02dc": "\u2018",
    "\u00e2\u20ac\u2122": "\u2019",
    "\u00e2\u20ac\u0153": "\u201c",
    "\u00e2\u20ac\u009d": "\u201d",
    "\u00e2\u20ac\u201c": "\u2013",
    "\u00e2\u20ac\u201d": "\u2014",
    "\u00c2\u00ab": "\u00ab",
    "\u00c2\u00bb": "\u00bb",
    "\u00e2\u20ac": "",
    "\u00c2": "",
    "\ufeff": "",
    "\u00ad": "",
}


def normalize(text: str) -> str:
    text = re.sub(r"&#(\d+);", lambda m: chr(int(m.group(1))), text)
    text = html.unescape(text)
    for bad, good in TYPOGRAPHY.items():
        text = text.replace(bad, good)
    text = text.translate(LEGACY_FONT)
    return text.replace("\r\n", "\n").replace("\r", "\n")


def repair_line_breaks(body: str) -> str:
    body = re.sub(r"[ \t]+", " ", body)
    # soft hyphenation produced by PDF line breaks: "ko‘ -chishlar"
    body = re.sub(r"(\w)\s?-\s?(?=[a-z\u2018\u2019])", r"\1", body)
    return body.strip()


def split_pages(text: str) -> list[tuple[int, str]]:
    parts = PAGE_RE.split(text)
    return [(int(parts[i + 1]), parts[i + 2]) for i in range(1, len(parts), 3)]


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", default=str(RAW))
    parser.add_argument("--out", default=str(OUT))
    args = parser.parse_args()

    text = normalize(Path(args.raw).read_text(encoding="utf-8"))
    pages = split_pages(text)
    if not pages:
        print("No page markers found", file=sys.stderr)
        return 1

    leftover = Counter(ch for ch in text if ord(ch) > 127)
    print(f"pages: {len(pages)}")
    print(
        "non-ascii left:",
        ", ".join(f"U+{ord(c):04X}({n})" for c, n in leftover.most_common(20)),
    )

    out = Path(args.out)
    with out.open("w", encoding="utf-8") as fh:
        for page, body in pages:
            fh.write(f"\n<<<PAGE {page}>>>\n{repair_line_breaks(body)}\n")
    print(f"wrote {out} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
