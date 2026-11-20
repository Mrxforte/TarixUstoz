"""Subject catalogue use cases."""
from __future__ import annotations

from collections.abc import Sequence

from domain.shared.errors import NotFoundError
from domain.subjects.entities import Subject, SubjectCategory
from domain.subjects.ports import SubjectRepository


class SubjectService:
    def __init__(self, subjects: SubjectRepository) -> None:
        self._subjects = subjects

    def list_subjects(self, *, category: SubjectCategory | None = None) -> Sequence[Subject]:
        return self._subjects.list(category=category)

    def get_subject(self, code: str) -> Subject:
        subject = self._subjects.get(code)
        if subject is None:
            raise NotFoundError("Fan", code)
        return subject

    def ensure_subject(self, subject: Subject) -> Subject:
        return self._subjects.save(subject)
