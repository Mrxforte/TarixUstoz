"""Build one book's question bank end-to-end (validate -> generate -> report).

Usage:
  python content/tools/build_book_bank.py 8-sinf_Jahon_tarixi --grade 8 --subject jahon \
      --prefix j8j --target 100000

Book folder layout (content/books/<name>/):
  source/topics.json
  facts/*.json
  generated/questions.ndjson
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOKS = ROOT / "books"
TOOLS = ROOT / "tools"


def run(cmd: list[str]) -> int:
    print("→", " ".join(cmd[-4:]))
    return subprocess.call(cmd)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("book")
    parser.add_argument("--grade", type=int, required=True)
    parser.add_argument("--subject", required=True,
                        help="jahon | uzbekiston (used for ids and the API subject)")
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--target", type=int, default=100000)
    args = parser.parse_args()

    book = BOOKS / args.book
    topics = book / "source" / "topics.json"
    facts = book / "facts"
    out = book / "generated"

    if not topics.exists():
        print(f"missing {topics}")
        return 1
    files = sorted(facts.glob("*.json"))
    if not files:
        print(f"no fact files in {facts}")
        return 1

    print(f"book: {args.book}  fact files: {len(files)}")
    code = run([sys.executable, str(TOOLS / "validate_facts.py"), *map(str, files),
                "--topics", str(topics)])
    if code != 0:
        print("validation failed — fix the facts first")
        return code

    code = run([sys.executable, str(TOOLS / "generate_questions.py"), str(args.target),
                "--facts", str(facts), "--topics", str(topics), "--out", str(out),
                "--grade", str(args.grade), "--subject", args.subject, "--prefix", args.prefix])
    return code


if __name__ == "__main__":
    raise SystemExit(main())
