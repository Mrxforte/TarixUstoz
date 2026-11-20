"""Composition root: builds application services with their concrete adapters."""
from __future__ import annotations

from dataclasses import dataclass, field

from application.assessment.service import AssessmentService
from application.certification.service import CertificationService
from application.international.service import InternationalService
from application.subjects.service import SubjectService
from infrastructure.persistence.repositories.assessment import (
    DjangoBookRepository,
    DjangoQuestionRepository,
    DjangoTopicRepository,
)
from infrastructure.persistence.repositories.certification import DjangoCertificationProgramRepository
from infrastructure.persistence.repositories.international import DjangoInternationalExamRepository
from infrastructure.persistence.repositories.subjects import DjangoSubjectRepository


@dataclass
class Container:
    """Wires ports to adapters. Imported lazily so tests can swap repositories."""

    subject_repository: DjangoSubjectRepository = field(default_factory=DjangoSubjectRepository)
    question_repository: DjangoQuestionRepository = field(default_factory=DjangoQuestionRepository)
    topic_repository: DjangoTopicRepository = field(default_factory=DjangoTopicRepository)
    book_repository: DjangoBookRepository = field(default_factory=DjangoBookRepository)
    certification_repository: DjangoCertificationProgramRepository = field(
        default_factory=DjangoCertificationProgramRepository
    )
    international_repository: DjangoInternationalExamRepository = field(
        default_factory=DjangoInternationalExamRepository
    )

    @property
    def subjects(self) -> SubjectService:
        return SubjectService(self.subject_repository)

    @property
    def assessment(self) -> AssessmentService:
        return AssessmentService(
            questions=self.question_repository,
            topics=self.topic_repository,
            books=self.book_repository,
            subjects=self.subject_repository,
        )

    @property
    def certification(self) -> CertificationService:
        return CertificationService(self.certification_repository)

    @property
    def international(self) -> InternationalService:
        return InternationalService(self.international_repository)


container = Container()
