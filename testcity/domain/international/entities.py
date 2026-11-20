"""Xalqaro testlar: SAT, IELTS va CEFR darajalari uchun sof biznes-qoidalar."""
from __future__ import annotations

import math

from dataclasses import dataclass, field
from enum import StrEnum

from domain.shared.errors import ValidationError


class InternationalExamKind(StrEnum):
    SAT = "sat"
    IELTS = "ielts"
    CEFR = "cefr"

    @property
    def title(self) -> str:
        return {
            InternationalExamKind.SAT: "SAT",
            InternationalExamKind.IELTS: "IELTS",
            InternationalExamKind.CEFR: "CEFR",
        }[self]

    @classmethod
    def coerce(cls, value: "str | InternationalExamKind") -> "InternationalExamKind":
        if isinstance(value, cls):
            return value
        try:
            return cls(str(value))
        except ValueError as exc:
            allowed = ", ".join(item.value for item in cls)
            raise ValidationError(f"Noto'g'ri xalqaro test turi: {value!r}. Ruxsat etilgan: {allowed}") from exc


# IELTS overall band -> CEFR level (British Council mapping).
_CEFR_THRESHOLDS: tuple[tuple[float, str], ...] = (
    (8.5, "C2"),
    (7.0, "C1"),
    (5.5, "B2"),
    (4.0, "B1"),
    (0.0, "A2"),
)


def ielts_overall_band(section_bands: "list[float] | tuple[float, ...]") -> float:
    """IELTS overall band: mean of the four sections, rounded to the nearest 0.5."""
    if not section_bands:
        raise ValidationError("IELTS uchun kamida bitta bo'lim balli kerak", field="sections")
    for band in section_bands:
        if not 0 <= float(band) <= 9:
            raise ValidationError("IELTS bo'lim balli 0 va 9 orasida bo'lishi kerak", field="sections")
    average = sum(float(b) for b in section_bands) / len(section_bands)
    # IELTS 6.25 -> 6.5: round half-bands up instead of Python's banker's rounding.
    return math.floor(average * 2 + 0.5) / 2


def cefr_from_ielts(band: float) -> str:
    """Map an IELTS band to its CEFR level ("B2", "C1", ...)."""
    for threshold, level in _CEFR_THRESHOLDS:
        if float(band) >= threshold:
            return level
    return "A2"


def sat_total(math_score: int, evidence_based_reading_writing: int) -> int:
    """SAT total (400-1600) from the two 200-800 section scores."""
    for name, value in (("math", math_score), ("reading_writing", evidence_based_reading_writing)):
        if not 200 <= int(value) <= 800:
            raise ValidationError(f"SAT {name} bo'limi 200 va 800 orasida bo'lishi kerak", field=name)
    return int(math_score) + int(evidence_based_reading_writing)


@dataclass(frozen=True, slots=True)
class ExamSection:
    code: str
    name: str
    max_score: float
    question_count: int = 0
    time_limit_minutes: int = 0
    order: int = 0

    def __post_init__(self) -> None:
        if not (self.code or "").strip():
            raise ValidationError("Bo'lim kodi bo'sh bo'lishi mumkin emas", field="code")
        if self.max_score <= 0:
            raise ValidationError(f"{self.code}: maksimal ball 0 dan katta bo'lishi kerak", field="max_score")


@dataclass(frozen=True, slots=True)
class CefrLevel:
    level: str
    min_score: float
    description: str = ""

    def __post_init__(self) -> None:
        normalized = (self.level or "").strip().upper()
        if not normalized:
            raise ValidationError("CEFR darajasi bo'sh bo'lishi mumkin emas", field="level")
        object.__setattr__(self, "level", normalized)


@dataclass(frozen=True, slots=True)
class InternationalExam:
    """An international exam blueprint (SAT, IELTS, CEFR)."""

    code: str
    name: str
    kind: InternationalExamKind
    max_score: float
    duration_minutes: int = 0
    sections: tuple[ExamSection, ...] = field(default_factory=tuple)
    cefr_levels: tuple[CefrLevel, ...] = field(default_factory=tuple)
    is_active: bool = True

    def __post_init__(self) -> None:
        object.__setattr__(self, "kind", InternationalExamKind.coerce(self.kind))
        object.__setattr__(self, "sections", tuple(sorted(self.sections, key=lambda s: (s.order, s.code))))
        object.__setattr__(
            self, "cefr_levels", tuple(sorted(self.cefr_levels, key=lambda level: level.min_score, reverse=True))
        )

    def section(self, code: str) -> ExamSection | None:
        return next((s for s in self.sections if s.code == code), None)

    def total(self, section_scores: dict[str, float]) -> float:
        if self.kind is InternationalExamKind.SAT:
            return float(
                sat_total(
                    int(section_scores.get("math", 0)),
                    int(section_scores.get("reading_writing", 0)),
                )
            )
        if self.kind is InternationalExamKind.IELTS:
            ordered = [float(section_scores[s.code]) for s in self.sections if s.code in section_scores]
            return ielts_overall_band(ordered)
        total = sum(float(section_scores.get(s.code, 0)) for s in self.sections)
        return round(total, 2)

    def cefr_for_ielts(self, band: float) -> str:
        return cefr_from_ielts(band)
