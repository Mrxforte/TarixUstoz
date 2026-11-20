"""Certificate use cases: milliy sertifikat, attestatsiya va xalqaro testlar."""
from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from domain.certificates.entities import (
    CertificateFamily,
    CertificateKind,
    CertificateProgram,
)
from domain.certificates.ports import CertificateProgramRepository
from domain.shared.errors import NotFoundError


@dataclass(frozen=True, slots=True)
class CertificateResult:
    total: float
    max_score: float
    band_label: str | None
    cefr_level: str | None
    passed: bool


class CertificateService:
    def __init__(self, programs: CertificateProgramRepository) -> None:
        self._programs = programs

    def list_programs(
        self,
        *,
        family: CertificateFamily | None = None,
        kind: CertificateKind | None = None,
        subject_code: str | None = None,
    ) -> Sequence[CertificateProgram]:
        return self._programs.list(family=family, kind=kind, subject_code=subject_code)

    def get_program(self, code: str) -> CertificateProgram:
        program = self._programs.get(code)
        if program is None:
            raise NotFoundError("Sertifikat dasturi", code)
        return program

    def evaluate(self, code: str, section_scores: dict[str, float]) -> CertificateResult:
        program = self.get_program(code)
        total = program.total(section_scores)
        band = program.band_for(total)
        return CertificateResult(
            total=total,
            max_score=program.max_score,
            band_label=band.label if band is not None else None,
            cefr_level=program.cefr_for(total),
            passed=program.is_passing(total),
        )

    def save_program(self, program: CertificateProgram) -> CertificateProgram:
        return self._programs.save(program)

    @staticmethod
    def families() -> list[dict]:
        """Metadata for the certificate section: two branches, their exam kinds."""
        return [
            {
                "code": family.value,
                "title": "Milliy sertifikatlar" if family is CertificateFamily.NATIONAL else "Xalqaro sertifikatlar",
                "kinds": [
                    {"code": kind.value, "title": kind.title}
                    for kind in CertificateKind.for_family(family)
                ],
            }
            for family in CertificateFamily
        ]
