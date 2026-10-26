"""Normalise typography inside a generated question bank (in place).

Fixed artifacts:
  * ASCII apostrophes  ->  o‘ / g‘ (U+2018) or tutuq belgisi ’ (U+2019)
  * Cyrillic look-alikes (а е о с р х у і) -> Latin equivalents
  * doubled spaces, spaces before punctuation

Usage:
  python content/tools/normalize_bank.py <questions.ndjson> [--dry-run]
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

CYR_TO_LAT = {
    "а": "a", "А": "A", "е": "e", "Е": "E", "о": "o", "О": "O",
    "с": "c", "С": "C", "р": "p", "Р": "P", "х": "x", "Х": "X",
    "у": "y", "У": "Y", "і": "i", "І": "I", "ѕ": "s", "ԛ": "q",
    "В": "B", "Н": "H", "К": "K", "М": "M", "Т": "T",
}
CYR_RE = re.compile("[" + "".join(CYR_TO_LAT) + "]")
APOS_OG = re.compile(r"([oOgG])'")
FIELDS = ("text", "option_a", "option_b", "option_c", "option_d", "explanation")


def fix(text: str) -> str:
    if not text:
        return text
    if CYR_RE.search(text):
        text = CYR_RE.sub(lambda m: CYR_TO_LAT[m.group(0)], text)
    if "'" in text:
        text = APOS_OG.sub("\\1\u2018", text)
        text = text.replace("'", "\u2019")
    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r"\s+([,.;:!?])", r"\1", text)
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("bank")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    path = Path(args.bank)
    tmp = path.with_suffix(".ndjson.tmp")
    changed = 0
    rows = 0
    fields_changed: dict[str, int] = {}

    with path.open(encoding="utf-8") as src, tmp.open("w", encoding="utf-8") as dst:
        for line in src:
            if not line.strip():
                continue
            rows += 1
            row = json.loads(line)
            dirty = False
            for field in FIELDS:
                before = row.get(field, "")
                after = fix(before)
                if after != before:
                    row[field] = after
                    fields_changed[field] = fields_changed.get(field, 0) + 1
                    dirty = True
            if dirty:
                changed += 1
            dst.write(json.dumps(row, ensure_ascii=False) + "\n")

    print(f"{path.name}: rows={rows} changed={changed} {fields_changed}")
    if args.dry_run:
        tmp.unlink()
        print("dry run — file left untouched")
        return 0
    tmp.replace(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
