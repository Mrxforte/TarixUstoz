"""Extract and segment a textbook exported as plain text (`media/books/txt/*.txt`).

Those files use this layout:
    ================================================================================
    DARSLIK: 8-sinf_Jahon_tarixi.pdf | BET: 12
    ================================================================================
    ...page text...
and mark paragraphs as "N-mavzu: TITLE".

Outputs (inside content/books/<book_slug>/source/):
    <Name>.txt      text with <<<PAGE n>>> markers
    topics.json     [{number, title, book_page, chapter, text}]
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

PAGE_RE = re.compile(
    r"={10,}\s*DARSLIK:\s*(?P<book>[^|\n]+?)\s*\|\s*(?:SINF:\s*\d+\s*\|\s*FAN:[^|\n]+\|\s*)?BET:\s*(?P<page>\d+)\s*={10,}"
)
TOC_LINE = re.compile(r"(\d{1,2})\s*-\s*[Mm][Aa][Vv][Zz][Uu]\s*[.:]\s*(.+?)\s*\.{2,}\s*(\d{1,3})")
HEADING = re.compile(
    r"(?m)^[ \t]*(?:(\d{1,2})\s*[-\u2013\u2014]\s*(?:[Mm][Aa][Vv][Zz][Uu]\s*[.:]|[\u00a7Ss$085]\s*\.)|[\u00a7Ss$]\s*(\d{1,2})\s*\.)\s*(.*)$"
)
SECTION_TOC = re.compile(r"(\d{1,2})\s*-\s*\u00a7\.\s*(.+?)\s*\.{2,}\s*(\d{1,3})")
DASH_S_TOC = re.compile(r"(\d{1,2})\s*-\s*S\s*\.\s*(.+?)\s*\.{2,}\s*(\d{1,3})")
SIGN_FIRST_TOC = re.compile(r"\u00a7\s*(\d{1,2})\.\s*(.+?)\s*\.{2,}\s*(\d{1,3})")
MAX_TOC_TITLE = 100

MOJIBAKE = str.maketrans({
    "\u00d2": "T",   # Ò used for capital T in headings
    "\u00c5": "Y",   # Å in JO‘RAYÅV
    "\u00c0": "A",
    "\u00b9": "¹",
    "\uf0a8": "•",
    "\uf0b4": "•",
})


def clean_text(text: str) -> str:
    text = text.translate(MOJIBAKE)
    lines = []
    for line in text.splitlines():
        s = line.strip()
        if not s:
            lines.append("")
            continue
        if s.lower().startswith("http://eduportal") or s.lower().startswith("https://eduportal"):
            continue
        if re.fullmatch(r"\d{1,3}", s):
            continue
        lines.append(s)
    text = "\n".join(lines)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


OCR_PAGE_RE = re.compile(r"(?m)^<<<PAGE (\d+)>>>[ \t]*$")


def split_pages(raw: str) -> list[tuple[int, str]]:
    parts = PAGE_RE.split(raw)
    if parts and len(parts) > 3:
        pages: list[tuple[int, str]] = []
        for i in range(1, len(parts) - 1, 3):
            pages.append((int(parts[i + 1]), parts[i + 2]))
        return pages
    # OCR exports already carry "<<<PAGE n>>>" markers instead of DARSLIK headers
    marks = list(OCR_PAGE_RE.finditer(raw))
    pages = []
    for i, m in enumerate(marks):
        end = marks[i + 1].start() if i + 1 < len(marks) else len(raw)
        pages.append((int(m.group(1)), raw[m.end(): end]))
    return pages


def toc_entries(pages: list[tuple[int, str]]) -> dict[int, tuple[str, int]]:
    """Parse table-of-contents lines. Matching is line-local so a broken TOC line
    can never swallow the following pages of body text."""
    entries: dict[int, tuple[str, int]] = {}
    for _page, body in pages:
        for line in body.splitlines():
            line = re.sub(r"\s+", " ", line.strip())
            if not line or "...." not in line:
                continue
            for regex in (TOC_LINE, SECTION_TOC, DASH_S_TOC, SIGN_FIRST_TOC):
                m = regex.search(line)
                if not m:
                    continue
                num, title, page = int(m.group(1)), m.group(2).strip(), int(m.group(3))
                if not (1 <= num <= 60 and num not in entries):
                    continue
                if len(title) > MAX_TOC_TITLE:
                    continue
                entries[num] = (title, page)
                break
    return entries


def chapter_titles(pages: list[tuple[int, str]]) -> list[tuple[int, str]]:
    """[(first paragraph number, 'I BOB. ...')] using the table of contents order."""
    chapters: list[tuple[int, str]] = []
    current: list[str] = []
    for page, body in pages:
        for line in body.splitlines():
            s = line.strip()
            if not s:
                continue
            m = re.match(r"([IVX]{1,4})\s+BOB\.?\s*(.*)$", s)
            if m:
                current = [f"{m.group(1)} BOB. {m.group(2)}".strip()]
                pending = True
                chapters.append((0, ""))  # placeholder, numbered later
                chapters[-1] = (len(chapters), " ".join(current))
                continue
    return chapters


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()

    raw = Path(args.src).read_text(encoding="utf-8")
    pages = split_pages(raw)
    if not pages:
        print("no page markers found")
        return 1
    cleaned = [(p, clean_text(body)) for p, body in pages]

    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    full = "\n".join(f"<<<PAGE {p}>>>\n{body}" for p, body in cleaned)
    (out_dir / f"{args.name}.txt").write_text(full, encoding="utf-8")

    entries = toc_entries(cleaned)
    print(f"pages: {len(cleaned)}  toc entries: {len(entries)}")

    # body headings: "N-mavzu:", "N-§.", "§ N." lines that are not TOC lines
    starts: dict[int, int] = {}
    last_num = 0
    for m in HEADING.finditer(full):
        num = int(m.group(1) or m.group(2))
        tail = full[m.end(): m.end() + 120]
        if "...." in tail[:80]:
            continue
        if num <= last_num:  # back-matter lists and duplicate mentions
            continue
        starts[num] = m.start()
        last_num = num

    chapters: list[tuple[int, str]] = []
    for m in re.finditer(r"(?m)^[ \t]*([IVX]{1,4})\s+BOB\.?\s*(.+)$", full):
        number_after = None
        for num, pos in sorted(starts.items(), key=lambda kv: kv[1]):
            if pos > m.start():
                number_after = num
                break
        if number_after:
            chapters.append((number_after, f"{m.group(1)} BOB. {m.group(2).strip()}"))

    def chapter_for(num: int) -> str:
        title = "KIRISH"
        for start, name in chapters:
            if num >= start:
                title = name
        return title

    def page_at(idx: int) -> int:
        page = cleaned[0][0]
        offset = 0
        for p, body in cleaned:
            offset += len(f"<<<PAGE {p}>>>\n{body}\n")
            if idx < offset:
                return p
        return cleaned[-1][0]

    ordered = sorted(starts.items())
    curated_path = out_dir / "titles.json"
    curated: dict[str, str] = {}
    if curated_path.exists():
        curated = json.loads(curated_path.read_text(encoding="utf-8"))

    topics = []
    for i, (num, start) in enumerate(ordered):
        end = ordered[i + 1][1] if i + 1 < len(ordered) else len(full)
        title = entries.get(num, (f"{num}-mavzu", 0))[0]
        if curated.get(str(num)):
            title = curated[str(num)]
        topics.append({
            "number": num,
            "title": title,
            "book_page": page_at(start),
            "chapter": chapter_for(num),
            "text": full[start:end].strip(),
        })

    out = out_dir / "topics.json"
    out.write_text(json.dumps(topics, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"topics: {len(topics)} -> {out}")
    for t in topics:
        print(f"  §{t['number']:>2} p{t['book_page']:>3} {len(t['text']):>5}ch {t['title'][:50]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
