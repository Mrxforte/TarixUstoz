"""Milliy sertifikat va attestatsiya dasturlari (programs, sections, bands, scoring)."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum

from domain.shared.errors import ValidationError


class CertificationKind(StrEnum):
    """Two state certification tracks served by the platform."""

    NATIONAL_CERTIFICATE = "national_certificate"
    ATTESTATION = "attestation"

    @property
    def title(self) -> str:
        return {
            CertificationKind.NATIONAL_CERTIFICATE: "Milliy sertifikat",
            CertificationKind.ATTESTATION: "Attestatsiya",
        }[self]

    @classmethod
    def coerce(cls, value: "str | CertificationKind") -> "CertificationKind":
        if isinstance(value, cls):
            return value
        try:
            return cls(str(value))
        except ValueError as exc:
            allowed = ", ".join(item.value for item in cls)
            raise ValidationError(f"Noto'g'ri sertifikat turi: {value!r}. Ruxsat etilgan: {allowed}") from exc


@dataclass(frozen=True, slots=True)
class ProgramSection:
    """One scored block of a certification exam (test, amaliy, suhbat, ...)."""

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
        if self.question_count < 0:
            raise ValidationError(f"{self.code}: savollar soni manfiy bo'lishi mumkin emas")


@dataclass(frozen=True, slots=True)
class ScoreBand:
    """A named score interval ("A toifa", "85-100 ball")."""

    label: str
    min_score: float
    max_score: float
    description: str = ""
    order: int = 0

    def __post_init__(self) -> None:
        if self.min_score > self.max_score:
            raise ValidationError(f"{self.label}: minimal ball maksimaldan katta")

    def contains(self, score: float) -> bool:
        return self.min_score <= score <= self.max_score


@dataclass(frozen=True, slots=True)
class CertificationProgram:
    """A certification exam blueprint for one subject and one track."""

    code: str
    name: str
    kind: CertificationKind
    subject_code: str
    max_score: float
    passing_score: float | None = None
    duration_minutes: int = 0
    sections: tuple[ProgramSection, ...] = field(default_factory=tuple)
    bands: tuple[ScoreBand, ...] = field(default_factory=tuple)
    is_active: bool = True

    def __post_init__(self) -> None:
        object.__setattr__(self, "kind", CertificationKind.coerce(self.kind))
        object.__setattr__(self, "sections", tuple(sorted(self.sections, key=lambda s: (s.order, s.code))))
        object.__setattr__(self, "bands", tuple(sorted(self.bands, key=lambda b: (b.min_score, b.label))))
        self.validate()

    def validate(self) -> None:
        if self.max_score <= 0:
            raise ValidationError("Maksimal ball 0 dan katta bo'lishi kerak", field="max_score")
        if self.passing_score is not None and not 0 <= self.passing_score <= self.max_score:
            raise ValidationError("O'tish balli 0 va maksimal ball orasida bo'lishi kerak", field="passing_score")
        if self.sections:
            section_total = round(sum(section.max_score for section in self.sections), 4)
            if abs(section_total - self.max_score) > 0.01:
                raise ValidationError(
                    f"{self.code}: bo'limlar yig'indisi ({section_total}) maksimal ball ({self.max_score}) bilan mos emas",
                    field="sections",
                )
            codes = [section.code for section in self.sections]
            if len(codes) != len(set(codes)):
                raise ValidationError(f"{self.code}: bo'lim kodlari takrorlanmasligi kerak", field="sections")
        self._validate_bands()

    def _validate_bands(self) -> None:
        if not self.bands:
            return
        previous_max = -1.0
        for band in self.bands:
            if band.min_score <= previous_max:
                raise ValidationError(f"{self.code}: '{band.label}' baholar oraliqlari ustma-ust tushgan", field="bands")
            if band.max_score > self.max_score:
                raise ValidationError(f"{self.code}: '{band.label}' maksimal balldan oshib ketgan", field="bands")
            previous_max = band.max_score

    def score(self, section_scores: dict[str, float]) -> float:
        """Total score for a submission; every declared section must be present."""
        missing = {s.code for s in self.sections} - set(section_scores)
        unknown = set(section_scores) - {s.code for s in self.sections}
        if missing:
            raise ValidationError(f"Bo'limlar to'liq emas: {', '.join(sorted(missing))}", field="sections")
        if unknown:
            raise ValidationError(f"Noma'lum bo'limlar: {', '.join(sorted(unknown))}", field="sections")
        total = 0.0
        for section in self.sections:
            value = float(section_scores[section.code])
            if not 0 <= value <= section.max_score:
                raise ValidationError(
                    f"{section.code}: ball 0 va {section.max_score} orasida bo'lishi kerak", field=section.code
                )
            total += value
        return round(total, 2)

    def band_for(self, score: float) -> ScoreBand | None:
        return next((band for band in self.bands if band.contains(score)), None)

    def is_passing(self, score: float) -> bool:
        return self.passing_score is None or score >= self.passing_score
