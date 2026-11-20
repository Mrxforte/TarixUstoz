"""Composition root: builds application services with their concrete adapters."""
from __future__ import annotations

from dataclasses import dataclass, field

from application.accounts.service import AccountService
from application.assessment.service import AssessmentService
from application.certificates.service import CertificateService
from application.leaderboard.service import LeaderboardService
from application.subjects.service import SubjectService
from data.repositories.accounts import DjangoAccountRepository
from data.repositories.assessment import (
    DjangoBookRepository,
    DjangoQuestionRepository,
    DjangoTopicRepository,
)
from data.repositories.certificates import DjangoCertificateProgramRepository
from data.repositories.leaderboard import DjangoLeaderboardRepository
from data.repositories.subjects import DjangoLanguageRepository, DjangoSubjectRepository


@dataclass
class Container:
    """Wires ports to adapters. Imported lazily so tests can swap repositories."""

    subject_repository: DjangoSubjectRepository = field(default_factory=DjangoSubjectRepository)
    language_repository: DjangoLanguageRepository = field(default_factory=DjangoLanguageRepository)
    question_repository: DjangoQuestionRepository = field(default_factory=DjangoQuestionRepository)
    topic_repository: DjangoTopicRepository = field(default_factory=DjangoTopicRepository)
    book_repository: DjangoBookRepository = field(default_factory=DjangoBookRepository)
    certificate_repository: DjangoCertificateProgramRepository = field(
        default_factory=DjangoCertificateProgramRepository
    )
    account_repository: DjangoAccountRepository = field(default_factory=DjangoAccountRepository)
    leaderboard_repository: DjangoLeaderboardRepository = field(default_factory=DjangoLeaderboardRepository)

    @property
    def subjects(self) -> SubjectService:
        return SubjectService(self.subject_repository, self.language_repository)

    @property
    def assessment(self) -> AssessmentService:
        return AssessmentService(
            questions=self.question_repository,
            topics=self.topic_repository,
            books=self.book_repository,
            subjects=self.subject_repository,
        )

    @property
    def certificates(self) -> CertificateService:
        return CertificateService(self.certificate_repository)

    @property
    def accounts(self) -> AccountService:
        return AccountService(self.account_repository)

    @property
    def leaderboard(self) -> LeaderboardService:
        return LeaderboardService(self.leaderboard_repository)


container = Container()
