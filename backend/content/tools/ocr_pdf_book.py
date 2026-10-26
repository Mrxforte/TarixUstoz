"""OCR a scanned textbook PDF into the same text layout used by the pipeline.

Renders each page with PyMuPDF and recognises the text with RapidOCR
(ONNX, no external binaries). Output keeps `<<<PAGE n>>>` markers so
`extract_mavzu_book.py` / `segment_book.py` can consume it.

Usage:
  python content/tools/ocr_pdf_book.py --pdf <path> --out <txt> [--dpi 200] [--pages 1-160]
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import pymupdf
from rapidocr_onnxruntime import RapidOCR


def render(page, dpi: int) -> np.ndarray:
    pix = page.get_pixmap(dpi=dpi)
    img = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
    if pix.n >= 3:
        img = img[:, :, :3][:, :, ::-1]  # RGB -> BGR
    return np.ascontiguousarray(img)


def page_text(engine: RapidOCR, image: np.ndarray) -> str:
    result, _ = engine(image)
    if not result:
        return ""
    lines = []
    for box, text, score in result:
        try:
            confidence = float(score)
        except (TypeError, ValueError):
            confidence = 1.0
        if confidence < 0.4:
            continue
        ys = [p[1] for p in box]
        xs = [p[0] for p in box]
        lines.append((min(ys), min(xs), text.strip()))
    lines.sort(key=lambda item: (round(item[0] / 12), item[1]))
    return "\n".join(t for _, _, t in lines if t)


def parse_pages(spec: str, total: int) -> tuple[int, int]:
    if not spec:
        return 1, total
    if "-" in spec:
        a, b = spec.split("-", 1)
        return int(a), min(int(b), total)
    return int(spec), min(int(spec), total)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--dpi", type=int, default=200)
    parser.add_argument("--pages", default="")
    args = parser.parse_args()

    doc = pymupdf.open(args.pdf)
    first, last = parse_pages(args.pages, len(doc))
    engine = RapidOCR()

    blocks: list[str] = []
    for number in range(first, last + 1):
        page = doc[number - 1]
        text = page_text(engine, render(page, args.dpi))
        blocks.append(f"<<<PAGE {number}>>>\n{text}")
        print(f"page {number}/{last}: {len(text)} chars", flush=True)

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(blocks), encoding="utf-8")
    print(f"wrote {out} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
