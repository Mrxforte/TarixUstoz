"""Milliy sertifikat va attestatsiya use cases."""
from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from domain.certification.entities import CertificationKind, CertificationProgram
from domain.certification.ports import CertificationProgramRepository
from domain.shared.errors import NotFoundError


@dataclass(frozen=True, slots=True)
class CertificationResult:
    total: float
    max_score: float
    band_label: str | None
    passed: bool


class CertificationService:
    def __init__(self, programs: CertificationProgramRepository) -> None:
        self._programs = programs

    def list_programs(
        self,
        *,
        kind: CertificationKind | None = None,
        subject_code: str | None = None,
    ) -> Sequence[CertificationProgram]:
        return self._programs.list(kind=kind, subject_code=subject_code)

    def get_program(self, code: str) -> CertificationProgram:
        program = self._programs.get(code)
        if program is None:
            raise NotFoundError("Sertifikat dasturi", code)
        return program

    def evaluate(self, code: str, section_scores: dict[str, float]) -> CertificationResult:
        program = self.get_program(code)
        total = program.score(section_scores)
        band = program.band_for(total)
        return CertificationResult(
            total=total,
            max_score=program.max_score,
            band_label=band.label if band is not None else None,
            passed=program.is_passing(total),
        )

    def save_program(self, program: CertificationProgram) -> CertificationProgram:
        return self._programs.save(program)
