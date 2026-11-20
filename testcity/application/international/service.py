"""SAT, IELTS va CEFR testlari uchun use case'lar."""
from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from domain.international.entities import InternationalExam, InternationalExamKind
from domain.international.ports import InternationalExamRepository
from domain.shared.errors import NotFoundError


@dataclass(frozen=True, slots=True)
class InternationalResult:
    total: float
    cefr_level: str | None


class InternationalService:
    def __init__(self, exams: InternationalExamRepository) -> None:
        self._exams = exams

    def list_exams(self, *, kind: InternationalExamKind | None = None) -> Sequence[InternationalExam]:
        return self._exams.list(kind=kind)

    def get_exam(self, code: str) -> InternationalExam:
        exam = self._exams.get(code)
        if exam is None:
            raise NotFoundError("Xalqaro test", code)
        return exam

    def evaluate(self, code: str, section_scores: dict[str, float]) -> InternationalResult:
        exam = self.get_exam(code)
        total = exam.total(section_scores)
        cefr_level = exam.cefr_for_ielts(total) if exam.kind is InternationalExamKind.IELTS else None
        return InternationalResult(total=total, cefr_level=cefr_level)

    def save_exam(self, exam: InternationalExam) -> InternationalExam:
        return self._exams.save(exam)
