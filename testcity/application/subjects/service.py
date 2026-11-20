"""Subject catalogue and language use cases."""
from __future__ import annotations

from collections.abc import Sequence

from domain.shared.errors import NotFoundError
from domain.subjects.entities import Language, Subject, SubjectCategory
from domain.subjects.ports import LanguageRepository, SubjectRepository


class SubjectService:
    def __init__(
        self,
        subjects: SubjectRepository,
        languages: LanguageRepository | None = None,
    ) -> None:
        self._subjects = subjects
        self._languages = languages

    def list_subjects(self, *, category: SubjectCategory | None = None) -> Sequence[Subject]:
        return self._subjects.list(category=category)

    def get_subject(self, code: str) -> Subject:
        subject = self._subjects.get(code)
        if subject is None:
            raise NotFoundError("Fan", code)
        return subject

    def ensure_subject(self, subject: Subject) -> Subject:
        return self._subjects.save(subject)

    def list_languages(self) -> Sequence[Language]:
        return self._languages.list() if self._languages else []

    def resolve_language(self, code: str | None) -> str:
        """Requested language when it exists and is active, otherwise the default one."""
        if self._languages is None:
            return (code or "").strip().lower()
        if code:
            language = self._languages.get(code)
            if language is not None and language.is_active:
                return language.code
        default = self._languages.default()
        return default.code if default is not None else ""
