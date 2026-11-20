"""Segment any extracted Uzbek textbook into paragraphs (§) and chapters.

Generic version of segment_topics.py: reads a normalized text with <<<PAGE n>>>
markers, parses the MUNDARIJA for paragraph titles, then locates every printed
heading in the body.

Usage:
  python content/tools/segment_book.py \
      --src content/uz7/source/uz7_tarixi.txt \
      --out content/uz7/source/topics.json
"""
from __future__ import annotations

import argparse
import json
import re
import unicodedata
from pathlib import Path


def load_pages(path: Path) -> dict[int, str]:
    raw = path.read_text(encoding="utf-8")
    parts = re.split(r"<<<PAGE (\d+)>>>", raw)
    return {int(parts[i]): parts[i + 1] for i in range(1, len(parts), 2)}


def fold(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    text = text.replace("\u2018", "'").replace("\u2019", "'")
    return re.sub(r"[^a-z0-9']+", " ", text.lower()).strip()


def toc_entries(toc: str) -> dict[int, tuple[str, int]]:
    """Parse 'N-§. Title .... page'. The PDF glued page numbers to § numbers."""
    marks = list(re.finditer(r"-\s*§\.", toc))
    runs = [re.search(r"(\d+)$", toc[: m.start()]) for m in marks]

    found: list[tuple[int, int, str]] = []
    expected = 1
    for idx, run in enumerate(runs):
        if not run or not run.group(1).endswith(str(expected)):
            continue
        body_end = marks[idx + 1].start() - 1 if idx + 1 < len(marks) else len(toc)
        body = toc[marks[idx].end(): body_end + 1]
        m = re.match(r"\s*(.+?)\s*\.{2,}", body)
        if not m:
            continue
        found.append((expected, idx, re.sub(r"\s+", " ", m.group(1)).strip()))
        expected += 1

    entries: dict[int, tuple[str, int]] = {}
    for pos, (num, idx, title) in enumerate(found):
        if pos + 1 < len(found):
            nxt_num, nxt_idx, _ = found[pos + 1]
            page_text = runs[nxt_idx].group(1)[: -len(str(nxt_num))]
            page = int(page_text) if page_text.isdigit() else 0
        else:
            digits = re.search(r"\.{2,}\s*(\d{1,3})", toc[marks[idx].end():])
            page = int(digits.group(1)) if digits else 0
        entries[num] = (title, page)

    known = [n for n in sorted(entries) if entries[n][1] > 0]
    for num in sorted(entries):
        if entries[num][1] == 0:
            prev = [n for n in known if n < num]
            nxt = [n for n in known if n > num]
            guess = entries[prev[-1]][1] if prev else (entries[nxt[0]][1] if nxt else 1)
            entries[num] = (entries[num][0], max(1, guess))
    return entries


def toc_chapters(toc: str) -> list[tuple[int, str]]:
    """Return [(first paragraph number, chapter title)] from the table of contents."""
    chapters: list[tuple[int, str]] = []
    for m in re.finditer(r"([IVX]{1,4})\s*BOB", toc):
        first = len(re.findall(r"-\s*§\.", toc[: m.start()])) + 1
        tail = toc[m.end():]
        end = re.search(r"-\s*§\.", tail)
        title = tail[: end.start()] if end else tail[:120]
        title = re.sub(r"\d+\s*$", "", title)
        title = re.sub(r"\.{2,}.*$", "", title).strip(" .")
        title = re.sub(r"\s+", " ", title)
        if title:
            chapters.append((first, f"{m.group(1)} BOB. {title}"))
    return chapters


def page_index(full: str) -> list[tuple[int, int]]:
    return [(m.start(), int(m.group(1))) for m in re.finditer(r"<<<PAGE (\d+)>>>", full)]


def page_at(offsets: list[tuple[int, int]], idx: int) -> int:
    page = offsets[0][1]
    for start, p in offsets:
        if start <= idx:
            page = p
        else:
            break
    return page


def locate_sections(full: str, entries: dict[int, tuple[str, int]], max_page: int) -> dict[int, int]:
    offsets = page_index(full)
    starts: dict[int, int] = {}
    for num, (title, page) in entries.items():
        words = fold(title).split()[:3]
        candidates = []
        for m in re.finditer(rf"{num}\s*-\s*§\.", full):
            page_here = page_at(offsets, m.start())
            if not (6 <= page_here <= max_page):
                continue
            probe = fold(full[m.end(): m.end() + 160])
            score = sum(1 for w in words if w in probe)
            candidates.append((-score, abs(page_here - page), m.start()))
        acceptable = False
        if candidates:
            candidates.sort()
            best = candidates[0]
            if best[0] <= -1 or abs(best[1]) <= 2:
                starts[num] = best[2]
                acceptable = True
        if not acceptable:
            # fallback: the printed heading may miss the "N-§." prefix
            raw_words = fold(title).split()[:3]
            window_start = full.find(f"<<<PAGE {max(1, page - 2)}>>>")
            if window_start < 0:
                continue
            window_end = full.find(f"<<<PAGE {page + 4}>>>")
            if window_end < 0:
                window_end = len(full)
            probe_region = full[window_start:window_end]
            pattern = r"\s+".join(
                re.escape(w).replace("'", "[\u2018\u2019']") for w in raw_words
            )
            raw = re.search(pattern, probe_region, re.I)
            if raw:
                starts[num] = window_start + raw.start()
    return starts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--toc-pages", default="157,158")
    args = parser.parse_args()

    src = Path(args.src)
    pages = load_pages(src)
    toc_pages = [int(p) for p in args.toc_pages.split(",")]
    toc = " ".join(pages.get(p, "") for p in toc_pages)

    entries = toc_entries(toc)
    print(f"toc entries: {len(entries)}")
    chapters = toc_chapters(toc)
    print("chapters:", [(n, t[:40]) for n, t in chapters])

    max_page = max(pages) - (max(pages) - 150 if len(toc_pages) <= 2 else 10)
    full = "\n".join(f"<<<PAGE {p}>>>\n{pages[p]}" for p in sorted(pages))
    starts = locate_sections(full, entries, max_page=max_page)
    print(f"located: {len(starts)} missing={[n for n in sorted(entries) if n not in starts]}")

    def chapter_for(num: int) -> str:
        title = "KIRISH"
        for start, name in chapters:
            if num >= start:
                title = name
        return title

    ordered = sorted(starts.items())
    offsets = page_index(full)
    docs_end = len(full)
    for marker in ("MUNDARIJA", "MUHIM TARIXIY SANALAR", "ATAMALAR"):
        idx = full.find(marker, offsets[-1][0] - 6000)
        if idx > 0:
            docs_end = min(docs_end, idx)

    topics = []
    for i, (num, start) in enumerate(ordered):
        end = ordered[i + 1][1] if i + 1 < len(ordered) else docs_end
        topics.append({
            "number": num,
            "title": entries[num][0],
            "book_page": page_at(offsets, start),
            "toc_page": entries[num][1],
            "chapter": chapter_for(num),
            "text": full[start:end].strip(),
        })

    out = Path(args.out)
    out.write_text(json.dumps(topics, ensure_ascii=False, indent=1), encoding="utf-8")
    for t in topics:
        print(f"§{t['number']:>2} p{t['book_page']:>3} {len(t['text']):>5}ch  {t['title'][:56]}")
    print(f"wrote {out} topics={len(topics)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
