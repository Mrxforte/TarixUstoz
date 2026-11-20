"""Small, dependency-free value objects used across domains."""
from __future__ import annotations

import re

_SLUG_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def normalize_slug(value: str) -> str:
    """Lower-case, dash-separated identifier ("Milliy Sertifikat" -> "milliy-sertifikat")."""
    cleaned = re.sub(r"[^a-z0-9]+", "-", (value or "").strip().lower())
    return cleaned.strip("-")


def is_valid_slug(value: str) -> bool:
    return bool(_SLUG_RE.match(value or ""))


def clamp_grade(grade: int) -> int:
    """School grades supported by the platform (5-11)."""
    return max(5, min(11, int(grade)))
