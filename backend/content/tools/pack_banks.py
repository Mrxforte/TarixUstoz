"""Pack the generated question banks into small, git-friendly archives.

Each `<book>/generated/questions.ndjson` (~90 MB) is compressed with xz
(~3 MB) into backend/content/banks/<book>.ndjson.xz and described in
backend/content/banks/manifest.json. The archives are what is committed to
git; `manage.py restore_content` rebuilds the database from them.

Usage (from backend/):
  python content/tools/pack_banks.py --src-root <dir that contains <book>/generated/questions.ndjson>
"""
from __future__ import annotations

import argparse
import hashlib
import json
import lzma
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BOOKS = ROOT / "books"
BANKS = ROOT / "banks"

SUBJECTS = {
    "Jahon_tarixi": "Jahon tarixi",
    "Ozbekiston_tarixi": "O'zbekiston tarixi",
    "Qadimgi_dunyo_tarixi": "Qadimgi dunyo tarixi",
}


def parse_folder(name: str) -> tuple[int, str]:
    grade, rest = name.split("-sinf_", 1)
    return int(grade), SUBJECTS[rest]


def pack(args: tuple[str, str]) -> dict:
    folder, src_root = args
    src = Path(src_root) / folder / "generated" / "questions.ndjson"
    dst = BANKS / f"{folder}.ndjson.xz"
    sha = hashlib.sha256()
    count = 0
    raw_bytes = 0
    prefix = ""
    with src.open("rb") as fin, lzma.open(dst, "wb", preset=9 | lzma.PRESET_EXTREME) as fout:
        for line in fin:
            if not line.strip():
                continue
            if not prefix:
                prefix = json.loads(line)["external_id"].split("-")[0]
            sha.update(line)
            fout.write(line)
            count += 1
            raw_bytes += len(line)
    grade, subject = parse_folder(folder)
    return {
        "folder": folder,
        "grade": grade,
        "subject": subject,
        "prefix": prefix,
        "count": count,
        "raw_bytes": raw_bytes,
        "raw_sha256": sha.hexdigest(),
        "archive": dst.name,
        "archive_bytes": dst.stat().st_size,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src-root", default=str(BOOKS))
    args = parser.parse_args()

    BANKS.mkdir(exist_ok=True)
    folders = sorted(
        p.name for p in Path(args.src_root).iterdir()
        if p.is_dir() and (p / "generated" / "questions.ndjson").exists()
    )
    with ProcessPoolExecutor() as pool:
        entries = list(pool.map(pack, [(f, args.src_root) for f in folders]))
    entries.sort(key=lambda e: (e["grade"], e["subject"]))
    (BANKS / "manifest.json").write_text(
        json.dumps({"books": entries}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    for e in entries:
        print(f"{e['folder']}: {e['count']} -> {e['archive_bytes'] / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
