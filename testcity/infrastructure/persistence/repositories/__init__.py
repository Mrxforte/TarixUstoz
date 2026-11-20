"""Concrete repository implementations (Django ORM)."""
from infrastructure.persistence.repositories.assessment import (
    DjangoBookRepository,
    DjangoQuestionRepository,
    DjangoTopicRepository,
)
from infrastructure.persistence.repositories.certification import DjangoCertificationProgramRepository
from infrastructure.persistence.repositories.international import DjangoInternationalExamRepository
from infrastructure.persistence.repositories.subjects import DjangoSubjectRepository

__all__ = [
    "DjangoBookRepository",
    "DjangoCertificationProgramRepository",
    "DjangoInternationalExamRepository",
    "DjangoQuestionRepository",
    "DjangoSubjectRepository",
    "DjangoTopicRepository",
]
