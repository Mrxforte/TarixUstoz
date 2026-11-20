"""Repository port for international exams (SAT, IELTS, CEFR)."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.international.entities import InternationalExam, InternationalExamKind


class InternationalExamRepository(Protocol):
    def list(
        self,
        *,
        kind: InternationalExamKind | None = None,
        active_only: bool = True,
    ) -> Sequence[InternationalExam]:
        ...

    def get(self, code: str) -> InternationalExam | None:
        ...

    def save(self, exam: InternationalExam) -> InternationalExam:
        ...
