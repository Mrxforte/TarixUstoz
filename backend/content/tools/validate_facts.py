"""Validate a hand-authored fact file against the segmented textbook.

Usage: python content/tools/validate_facts.py content/facts/ch1.json [...]

Every item must carry an `evidence` string that really occurs in the text of
the paragraph it claims to come from (whitespace/punctuation insensitive).
"""
from __future__ import annotations

import json
import re
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_TOPICS = ROOT / "books" / "7-sinf_Jahon_tarixi" / "source" / "topics.json"


def load_topic_text(path: Path) -> dict[int, str]:
    items = json.loads(path.read_text(encoding="utf-8"))
    return {t["number"]: t["text"] for t in items}


TOPIC_TEXT = load_topic_text(DEFAULT_TOPICS)

REQUIRED = {
    "date_event": ("date", "date_label", "event"),
    "person": ("person", "role", "activity"),
    "term": ("term", "definition"),
    "place": ("place", "event"),
    "cause": ("cause", "effect"),
    "number": ("number", "unit", "meaning"),
}


def norm(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("\u2018", "'").replace("\u2019", "'")
    text = text.lower().replace("'", "'")
    return re.sub(r"[^a-z0-9']+", " ", text).strip()


def check(path: Path) -> list[str]:
    data = json.loads(path.read_text(encoding="utf-8"))
    errors: list[str] = []
    seen: set[tuple] = set()

    for topic in data.get("topics", []):
        num = topic.get("number")
        if num not in TOPIC_TEXT:
            errors.append(f"{path.name}: unknown topic {num}")
            continue
        body = norm(TOPIC_TEXT[num])
        for idx, item in enumerate(topic.get("items", [])):
            where = f"{path.name}: topic {num} item {idx}"
            kind = item.get("kind")
            if kind not in REQUIRED:
                errors.append(f"{where}: bad kind {kind!r}")
                continue
            for field in REQUIRED[kind]:
                if not str(item.get(field, "")).strip():
                    errors.append(f"{where}: missing {field}")
            for field in ("event", "activity", "definition", "effect", "meaning"):
                if field in item and len(str(item[field])) > 220:
                    errors.append(f"{where}: {field} too long")
            evidence = norm(str(item.get("evidence", "")))
            if len(evidence) < 12:
                errors.append(f"{where}: evidence too short")
            elif evidence not in body:
                errors.append(f"{where}: evidence not found in textbook text")
            key = tuple(sorted((k, str(v)) for k, v in item.items() if k != "evidence"))
            if key in seen:
                errors.append(f"{where}: duplicate item")
            seen.add(key)
    return errors


def main(argv: list[str]) -> int:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("files", nargs="+")
    parser.add_argument("--topics", default=str(DEFAULT_TOPICS))
    args = parser.parse_args(argv)

    global TOPIC_TEXT
    TOPIC_TEXT = load_topic_text(Path(args.topics))

    failed = 0
    for arg in args.files:
        path = Path(arg)
        errors = check(path)
        status = "OK" if not errors else f"{len(errors)} problem(s)"
        print(f"{path.name}: {status}")
        for err in errors[:40]:
            print("  -", err)
        failed += bool(errors)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
