"""Shrink the textbook PDFs so they can live in git.

Scanned books (large PDFs) get their page images re-encoded at 150 dpi / JPEG q60;
small or already compact PDFs are copied unchanged. Output goes to
backend/content/pdfs/ (tracked); `manage.py restore_content` copies them to media/pdfs/.

Usage (from backend/):
  python content/tools/pack_pdfs.py --src media/books/pdfs
"""
from __future__ import annotations

import argparse
import shutil
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import pymupdf

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "pdfs"
OPTIMIZE_ABOVE = 10 * 1024 * 1024


def pack(args: tuple[str, str]) -> str:
    src, out_dir = Path(args[0]), Path(args[1])
    dst = out_dir / src.name
    size = src.stat().st_size
    if size <= OPTIMIZE_ABOVE:
        shutil.copyfile(src, dst)
        return f"{src.name}: {size / 1e6:.1f} MB (copied)"
    tmp = dst.with_suffix(".tmp")
    with pymupdf.open(src) as doc:
        doc.rewrite_images(dpi_threshold=170, dpi_target=150, quality=60)
        doc.save(tmp, garbage=4, deflate=True, clean=True)
    if tmp.stat().st_size < size:
        tmp.replace(dst)
    else:
        tmp.unlink()
        shutil.copyfile(src, dst)
    return f"{src.name}: {size / 1e6:.1f} -> {dst.stat().st_size / 1e6:.1f} MB"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True)
    args = parser.parse_args()
    OUT.mkdir(exist_ok=True)
    files = sorted(Path(args.src).glob("*.pdf"))
    with ProcessPoolExecutor(max_workers=4) as pool:
        for line in pool.map(pack, [(str(f), str(OUT)) for f in files]):
            print(line)


if __name__ == "__main__":
    main()
