"""Repository port for the subject catalogue (implemented in infrastructure)."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.subjects.entities import Subject, SubjectCategory


class SubjectRepository(Protocol):
    def list(
        self,
        *,
        category: SubjectCategory | None = None,
        active_only: bool = True,
    ) -> Sequence[Subject]:
        ...

    def get(self, code: str) -> Subject | None:
        ...

    def save(self, subject: Subject) -> Subject:
        ...
