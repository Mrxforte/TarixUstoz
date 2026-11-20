"""Scoring rules for certificate tests (national and international).

All functions are pure: no Django, no I/O — safe to unit test and reuse from the
API, management commands and background jobs.
"""
from __future__ import annotations

import math

from domain.shared.errors import ValidationError


def ielts_overall_band(section_bands: "list[float] | tuple[float, ...]") -> float:
    """IELTS overall band: mean of the four sections, rounded up to the nearest 0.5."""
    if not section_bands:
        raise ValidationError("IELTS uchun kamida bitta bo'lim balli kerak", field="sections")
    for band in section_bands:
        if not 0 <= float(band) <= 9:
            raise ValidationError("IELTS bo'lim balli 0 va 9 orasida bo'lishi kerak", field="sections")
    average = sum(float(b) for b in section_bands) / len(section_bands)
    # IELTS 6.25 -> 6.5: round half-bands up instead of Python's banker's rounding.
    return math.floor(average * 2 + 0.5) / 2


# IELTS overall band -> CEFR level (British Council mapping).
_CEFR_THRESHOLDS: tuple[tuple[float, str], ...] = (
    (8.5, "C2"),
    (7.0, "C1"),
    (5.5, "B2"),
    (4.0, "B1"),
    (0.0, "A2"),
)


def cefr_from_ielts(band: float) -> str:
    """Map an IELTS band to its CEFR level ("B2", "C1", ...)."""
    for threshold, level in _CEFR_THRESHOLDS:
        if float(band) >= threshold:
            return level
    return "A2"


def cefr_from_score(score: float, levels: "tuple[tuple[float, str], ...]") -> str | None:
    """Highest CEFR level whose threshold the score reaches (levels sorted high->low)."""
    for threshold, level in levels:
        if float(score) >= float(threshold):
            return level
    return None


def sat_total(math_score: int, evidence_based_reading_writing: int) -> int:
    """SAT total (400-1600) from the two 200-800 section scores."""
    for name, value in (("math", math_score), ("reading_writing", evidence_based_reading_writing)):
        if not 200 <= int(value) <= 800:
            raise ValidationError(f"SAT {name} bo'limi 200 va 800 orasida bo'lishi kerak", field=name)
    return int(math_score) + int(evidence_based_reading_writing)


def toefl_total(section_scores: "dict[str, float]") -> float:
    """TOEFL iBT total: sum of the four 0-30 sections (0-120)."""
    for code, value in section_scores.items():
        if not 0 <= float(value) <= 30:
            raise ValidationError(f"TOEFL '{code}' bo'limi 0 va 30 orasida bo'lishi kerak", field=code)
    return round(sum(float(v) for v in section_scores.values()), 2)
