"""Apply curated short titles to a book's topics.json.

Some txt exports have no usable table of contents, so extract_mavzu_book.py
fell back to noisy strings (sometimes the whole paragraph body). Curated titles
live in `source/titles.json` as {"<topic number>": "<title>"}.

Usage:
  python content/tools/apply_titles.py 8-sinf_Jahon_tarixi [--dry-run]
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

BOOKS = Path(__file__).resolve().parents[1] / "books"
MAX_TITLE = 120


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("book")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    book_dir = BOOKS / args.book / "source"
    topics_path = book_dir / "topics.json"
    titles_path = book_dir / "titles.json"
    if not topics_path.exists():
        print(f"missing {topics_path}")
        return 1
    if not titles_path.exists():
        print(f"missing {titles_path}")
        return 1

    topics = json.loads(topics_path.read_text(encoding="utf-8"))
    titles: dict[str, str] = json.loads(titles_path.read_text(encoding="utf-8"))

    changed = 0
    for topic in topics:
        key = str(topic["number"])
        title = (titles.get(key) or "").strip()
        if not title:
            print(f"  ! no title for topic {key} — keeping {topic['title'][:40]!r}")
            continue
        if len(title) > MAX_TITLE:
            print(f"  ! topic {key} title is {len(title)} chars, truncating")
            title = title[:MAX_TITLE].rsplit(" ", 1)[0]
        if title != topic["title"]:
            topic["title"] = title
            changed += 1

    extra = set(titles) - {str(t["number"]) for t in topics}
    if extra:
        print(f"  ! titles.json has unknown topics: {sorted(extra)}")

    print(f"{args.book}: {len(topics)} topics, {changed} titles updated")
    for topic in topics:
        print(f"  §{topic['number']:>2} p{topic['book_page']:>3} {len(topic['text']):>6}ch  {topic['title'][:80]}")

    if args.dry_run:
        print("dry run — topics.json not written")
        return 0
    topics_path.write_text(json.dumps(topics, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"written {topics_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
