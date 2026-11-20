"""Subject catalogue: every fan (tarix, matematika, ingliz tili, ...) is a record here."""
from __future__ import annotations

from domain.subjects.entities import Language, Subject, SubjectCategory
from domain.subjects.ports import LanguageRepository, SubjectRepository

__all__ = ["Language", "LanguageRepository", "Subject", "SubjectCategory", "SubjectRepository"]
