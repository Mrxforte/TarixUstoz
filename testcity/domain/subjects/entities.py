"""Subject entity.

The platform started as a history-only app. Every subject is now a first-class
record so new fanlar (matematika, ona tili, ingliz tili, ...) and exam tracks
(milliy sertifikat, attestatsiya, SAT, IELTS/CEFR) can be registered without
touching the history content pipeline.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum

from domain.shared.value_objects import is_valid_slug, normalize_slug

HISTORY_SUBJECT_CODE = "tarix"


class SubjectCategory(StrEnum):
    SCHOOL = "school"
    NATIONAL_CERTIFICATE = "national_certificate"
    ATTESTATION = "attestation"
    INTERNATIONAL = "international"

    @classmethod
    def coerce(cls, value: "str | SubjectCategory") -> "SubjectCategory":
        if isinstance(value, cls):
            return value
        try:
            return cls(str(value))
        except ValueError as exc:
            allowed = ", ".join(item.value for item in cls)
            raise ValueError(f"Noto'g'ri fan toifasi: {value!r}. Ruxsat etilgan: {allowed}") from exc


@dataclass(frozen=True, slots=True)
class Subject:
    """A fan that the platform serves content and tests for."""

    code: str
    name: str
    category: SubjectCategory = SubjectCategory.SCHOOL
    name_en: str = ""
    grades: tuple[int, ...] = field(default_factory=tuple)
    icon: str = ""
    order: int = 0
    is_active: bool = True

    def __post_init__(self) -> None:
        code = normalize_slug(self.code)
        if not is_valid_slug(code):
            raise ValueError(f"Fan kodi noto'g'ri: {self.code!r}")
        if not (self.name or "").strip():
            raise ValueError("Fan nomi bo'sh bo'lishi mumkin emas")
        object.__setattr__(self, "code", code)
        object.__setattr__(self, "category", SubjectCategory.coerce(self.category))
        object.__setattr__(self, "grades", tuple(sorted({int(g) for g in self.grades})))

    @property
    def is_history(self) -> bool:
        return self.code == HISTORY_SUBJECT_CODE

    def supports_grade(self, grade: int) -> bool:
        return not self.grades or int(grade) in self.grades
