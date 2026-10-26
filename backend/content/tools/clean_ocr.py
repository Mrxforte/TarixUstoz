"""Light cleanup of RapidOCR output for scanned textbooks.

RapidOCR systematically misreads a handful of glyphs (e for c, t for l/1, b for h)
and keeps running-header junk. This pass removes only high-confidence noise so the
facts agents can quote verbatim text that still reads like the printed book.

Usage:
  python content/tools/clean_ocr.py --in source/Ozbekiston_tarixi.ocr.txt \
      --out source/Ozbekiston_tarixi.clean.txt
"""
from __future__ import annotations

import argparse
import re
from pathlib import Path

ARTIFACT_LINES = {
    "httr", "http", "https", "://e", "uz", "*", "-", "~", ".", "e", "i", "1",
    "O'QITUVCHI", "o'qituvchi",
}

# whole-word OCR confusions seen repeatedly in this scan set
WORD_FIXES = {
    "cdi": "edi", "cdilar": "edilar", "ckan": "ekan", "ctdi": "etdi",
    "ctdilar": "etdilar", "ctgan": "etgan", "cgalladi": "egalladi",
    "cgallashga": "egallashga", "cshitgach": "eshitgach", "cshilgan": "eshilgan",
    "cshitgan": "eshitgan", "cshitdi": "eshitdi", "bilan": "bilan",
    "Bohur": "Bobur", "bohur": "Bobur", "ycrda": "yerda", "ycr": "yer",
    "qaycrdan": "qayerdan", "qaycrda": "qayerda", "1aklif": "taklif",
    "1arix": "tarix", "1umanidagi": "tumanidagi", "1o'plab": "to'plab",
    "Sulion": "Sulton", "davlal": "davlat", "lumanidagi": "tumanidagi",
    "Shayxonlohur": "Shayxontohur", "nashrgatavsiyaelilgan": "nashrga tavsiya etilgan",
    "Mualljlar": "Mualliflar", "cldi": "eldi", "o'qiluvchisi": "o'qituvchisi",
    "kalla": "katta", "ma'lumotlarining": "ma'lumotlarining",
    "univcrsiteti": "universiteti", "universitcti": "universiteti",
    "kitoh": "kitob", "jamg'armasi": "jamg'armasi",
}

HYPHEN_JOIN = re.compile(r"(?P<a>[A-Za-z\u2018\u2019'\u02bb\u02bc])-\n(?P<b>[a-z\u2018\u2019'\u02bb\u02bc])")
PAGE_MARK = re.compile(r"^<<<PAGE \d+>>>$", re.M)


def clean(raw: str) -> str:
    out_lines: list[str] = []
    for line in raw.splitlines():
        stripped = line.strip()
        if stripped.lower() in ARTIFACT_LINES:
            continue
        if re.fullmatch(r"[|\[\]{}<>~*/\\_-]{1,3}", stripped):
            continue
        out_lines.append(line.rstrip())

    text = "\n".join(out_lines)
    text = HYPHEN_JOIN.sub(lambda m: m.group("a") + m.group("b"), text)

    def fix_word(match: re.Match[str]) -> str:
        word = match.group(0)
        fixed = WORD_FIXES.get(word) or WORD_FIXES.get(word.lower())
        if not fixed:
            return word
        return fixed
    text = re.sub(r"[A-Za-z\u2018\u2019'\u02bb\u02bc]+", fix_word, text)

    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="src", required=True)
    parser.add_argument("--out", dest="dst", required=True)
    args = parser.parse_args()

    raw = Path(args.src).read_text(encoding="utf-8")
    cleaned = clean(raw)
    Path(args.dst).write_text(cleaned, encoding="utf-8")
    pages = len(PAGE_MARK.findall(cleaned))
    print(f"{args.src} -> {args.dst}: {len(raw)} -> {len(cleaned)} chars, {pages} pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
