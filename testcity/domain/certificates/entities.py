"""Certificate tests: milliy sertifikat, attestatsiya (milliy) va SAT, IELTS, TOEFL, CEFR (xalqaro)."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum

from domain.certificates.scoring import cefr_from_ielts, cefr_from_score, ielts_overall_band, sat_total, toefl_total
from domain.shared.errors import ValidationError


class CertificateFamily(StrEnum):
    """Two branches of the certificate section."""

    NATIONAL = "national"
    INTERNATIONAL = "international"


class CertificateKind(StrEnum):
    MILLIY_SERTIFIKAT = "national_certificate"
    ATTESTATSIYA = "attestation"
    SAT = "sat"
    IELTS = "ielts"
    TOEFL = "toefl"
    CEFR = "cefr"

    @property
    def family(self) -> CertificateFamily:
        if self in (CertificateKind.MILLIY_SERTIFIKAT, CertificateKind.ATTESTATSIYA):
            return CertificateFamily.NATIONAL
        return CertificateFamily.INTERNATIONAL

    @property
    def title(self) -> str:
        return {
            CertificateKind.MILLIY_SERTIFIKAT: "Milliy sertifikat",
            CertificateKind.ATTESTATSIYA: "Attestatsiya",
            CertificateKind.SAT: "SAT",
            CertificateKind.IELTS: "IELTS",
            CertificateKind.TOEFL: "TOEFL",
            CertificateKind.CEFR: "CEFR",
        }[self]

    @classmethod
    def coerce(cls, value: "str | CertificateKind") -> "CertificateKind":
        if isinstance(value, cls):
            return value
        try:
            return cls(str(value))
        except ValueError as exc:
            allowed = ", ".join(item.value for item in cls)
            raise ValidationError(
                f"Noto'g'ri sertifikat turi: {value!r}. Ruxsat etilgan: {allowed}", field="kind"
            ) from exc

    @classmethod
    def for_family(cls, family: "CertificateFamily | str") -> tuple["CertificateKind", ...]:
        wanted = CertificateFamily(str(family))
        return tuple(kind for kind in cls if kind.family is wanted)


@dataclass(frozen=True, slots=True)
class CertificateSection:
    """One scored block of a certificate exam (test, listening, math, ...)."""

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
class CertificateBand:
    """A named score interval ("A toifa", "Band 7.0", "B2")."""

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
class CertificateProgram:
    """One certificate exam blueprint — national or international."""

    code: str
    name: str
    kind: CertificateKind
    subject_code: str = ""
    max_score: float = 100
    passing_score: float | None = None
    duration_minutes: int = 0
    sections: tuple[CertificateSection, ...] = field(default_factory=tuple)
    bands: tuple[CertificateBand, ...] = field(default_factory=tuple)
    cefr_levels: tuple[CefrLevel, ...] = field(default_factory=tuple)
    is_active: bool = True
    translations: dict[str, str] = field(default_factory=dict)

    def __post_init__(self) -> None:
        object.__setattr__(self, "kind", CertificateKind.coerce(self.kind))
        object.__setattr__(self, "sections", tuple(sorted(self.sections, key=lambda s: (s.order, s.code))))
        object.__setattr__(self, "bands", tuple(sorted(self.bands, key=lambda b: (b.min_score, b.label))))
        object.__setattr__(
            self, "cefr_levels", tuple(sorted(self.cefr_levels, key=lambda level: level.min_score, reverse=True))
        )
        self.validate()

    @property
    def family(self) -> CertificateFamily:
        return self.kind.family

    def name_in(self, language: str | None) -> str:
        """Display name for a language code, falling back to the default name."""
        if not language:
            return self.name
        return self.translations.get(language.strip().lower(), self.name)

    @property
    def is_national(self) -> bool:
        return self.family is CertificateFamily.NATIONAL

    @property
    def is_international(self) -> bool:
        return self.family is CertificateFamily.INTERNATIONAL

    def validate(self) -> None:
        if self.max_score <= 0:
            raise ValidationError("Maksimal ball 0 dan katta bo'lishi kerak", field="max_score")
        if self.passing_score is not None and not 0 <= self.passing_score <= self.max_score:
            raise ValidationError("O'tish balli 0 va maksimal ball orasida bo'lishi kerak", field="passing_score")
        if self.is_national and not self.subject_code:
            raise ValidationError("Milliy sertifikat va attestatsiya fanga bog'langan bo'lishi kerak", field="subject_code")
        codes = [section.code for section in self.sections]
        if len(codes) != len(set(codes)):
            raise ValidationError(f"{self.code}: bo'lim kodlari takrorlanmasligi kerak", field="sections")
        if self.is_national and self.sections:
            section_total = round(sum(section.max_score for section in self.sections), 4)
            if abs(section_total - self.max_score) > 0.01:
                raise ValidationError(
                    f"{self.code}: bo'limlar yig'indisi ({section_total}) maksimal ball ({self.max_score}) bilan mos emas",
                    field="sections",
                )
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

    def total(self, section_scores: dict[str, float]) -> float:
        """Total score for a submission, interpreted per exam kind."""
        if self.kind is CertificateKind.SAT:
            return float(
                sat_total(
                    int(section_scores.get("math", 0)),
                    int(section_scores.get("reading_writing", 0)),
                )
            )
        if self.kind is CertificateKind.IELTS:
            ordered = [float(section_scores[s.code]) for s in self.sections if s.code in section_scores]
            return ielts_overall_band(ordered)
        if self.kind is CertificateKind.TOEFL:
            return toefl_total({code: value for code, value in section_scores.items() if code in self.section_codes})
        return self.score(section_scores)

    @property
    def section_codes(self) -> tuple[str, ...]:
        return tuple(section.code for section in self.sections)

    def score(self, section_scores: dict[str, float]) -> float:
        """Sum of the declared sections; every section must be present and in range."""
        missing = set(self.section_codes) - set(section_scores)
        unknown = set(section_scores) - set(self.section_codes)
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

    def band_for(self, score: float) -> CertificateBand | None:
        return next((band for band in self.bands if band.contains(score)), None)

    def is_passing(self, score: float) -> bool:
        return self.passing_score is None or score >= self.passing_score

    def cefr_for(self, total: float) -> str | None:
        """CEFR level for a total score (IELTS uses the official band mapping)."""
        if not self.cefr_levels:
            return None
        if self.kind is CertificateKind.IELTS:
            return cefr_from_ielts(total)
        return cefr_from_score(total, tuple((level.min_score, level.level) for level in self.cefr_levels))
