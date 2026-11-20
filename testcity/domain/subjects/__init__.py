"""Subject catalogue: every fan (tarix, matematika, ingliz tili, ...) is a record here."""
from __future__ import annotations

from domain.subjects.entities import Subject, SubjectCategory
from domain.subjects.ports import SubjectRepository

__all__ = ["Subject", "SubjectCategory", "SubjectRepository"]
