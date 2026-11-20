"""Assessment use cases: subject overview, test tickets and the daily question."""
from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date

import hashlib

from domain.assessment.entities import (
    AssessmentOverview,
    Difficulty,
    QuestionFilter,
    QuestionRef,
    Ticket,
)
from domain.assessment.ports import BookRepository, QuestionRepository, TopicRepository
from domain.shared.errors import NotFoundError, ValidationError
from domain.subjects.entities import Subject, SubjectCategory
from domain.subjects.ports import SubjectRepository
from domain.tarix.subjects import canonical_history_subject

MAX_TICKET_SIZE = 100
MIN_TICKET_SIZE = 1
_FALLBACK_SUBJECTS = {"tarix": "Tarix"}


def daily_seed(day: date, user_identifier: str, grade: int, subject: str, nonce: str = "") -> int:
    """Stable per-day seed: the same student gets the same question all day."""
    raw = f"{day.isoformat()}_{user_identifier}_{grade}_{subject}_{nonce}"
    return int(hashlib.md5(raw.encode("utf-8")).hexdigest(), 16)


@dataclass(frozen=True, slots=True)
class TicketPage:
    """A ticket plus the paging metadata the app needs to show "1/12"."""

    ticket: Ticket
    total: int
    ticket_count: int
    page: int


class AssessmentService:
    def __init__(
        self,
        *,
        questions: QuestionRepository,
        topics: TopicRepository,
        books: BookRepository,
        subjects: SubjectRepository,
    ) -> None:
        self._questions = questions
        self._topics = topics
        self._books = books
        self._subjects = subjects

    def _subject(self, code: str) -> Subject:
        subject = self._subjects.get(code) if code else None
        if subject is not None:
            return subject
        fallback = _FALLBACK_SUBJECTS.get((code or "").strip().lower())
        if fallback is None:
            raise NotFoundError("Fan", code)
        return Subject(code=code, name=fallback, category=SubjectCategory.SCHOOL, grades=(6, 7, 8, 9, 10, 11))

    def stored_subject(self, subject_code: str, track: str = "", grade: int = 0) -> str:
        """The subject name stored on the questions of the history pipeline."""
        subject = self._subject(subject_code)
        if subject.is_history:
            return canonical_history_subject(track, grade) if track else ""
        return subject.name

    def overview(self, subject_code: str, *, grade: int = 0, track: str = "") -> AssessmentOverview:
        subject = self._subject(subject_code)
        stored = self.stored_subject(subject_code, track, grade)
        query = QuestionFilter(subject=stored, grade=grade)
        return AssessmentOverview(
            subject=subject.code,
            grade=grade,
            question_count=self._questions.count(query),
            topic_count=self._topics.count(subject=stored, grade=grade),
            book_count=self._books.count(subject=stored, grade=grade),
            direction_counts=dict(self._questions.direction_counts(grade=grade, subject=stored)),
        )

    def build_ticket(
        self,
        subject_code: str,
        *,
        grade: int = 0,
        track: str = "",
        direction: str = "",
        difficulty: Difficulty | str | None = None,
        size: int = 30,
        page: int = 1,
    ) -> TicketPage:
        if not MIN_TICKET_SIZE <= int(size) <= MAX_TICKET_SIZE:
            raise ValidationError(
                f"Ticket hajmi {MIN_TICKET_SIZE} va {MAX_TICKET_SIZE} orasida bo'lishi kerak", field="size"
            )
        if int(page) < 1:
            raise ValidationError("Ticket raqami 1 dan kichik bo'lishi mumkin emas", field="page")

        subject = self._subject(subject_code)
        stored = self.stored_subject(subject_code, track, grade)
        query = QuestionFilter(
            subject=stored,
            grade=grade,
            direction=direction,
            difficulty=Difficulty.coerce(difficulty),
        )
        total = self._questions.count(query)
        page = int(page)
        size = int(size)
        ticket_count = max(1, math.ceil(total / size)) if total else 0
        ids = list(self._questions.page_ids(query, offset=(page - 1) * size, limit=size))
        refs = list(self._questions.fetch(ids))

        if total and len(refs) < size:
            taken = {ref.id for ref in refs}
            pool = [qid for qid in self._questions.page_ids(query, offset=0, limit=200) if qid not in taken]
            refs.extend(self._questions.fetch(pool[: size - len(refs)]))

        return TicketPage(
            ticket=Ticket(
                questions=tuple(refs),
                subject=subject.code,
                grade=grade,
                direction=(direction or "").strip().upper(),
            ),
            total=total,
            ticket_count=ticket_count,
            page=page,
        )

    def daily_question(
        self,
        subject_code: str,
        *,
        grade: int = 0,
        track: str = "",
        day_seed: int = 0,
        excluded_ids: tuple[int, ...] = (),
    ) -> QuestionRef:
        stored = self.stored_subject(subject_code, track, grade)
        query = QuestionFilter(subject=stored, grade=grade)
        question_id = self._questions.daily_id(query, day_seed=day_seed, excluded_ids=excluded_ids)
        if question_id is None:
            raise NotFoundError("Kunlik savol", subject_code)
        found = self._questions.fetch([question_id])
        if not found:
            raise NotFoundError("Kunlik savol", subject_code)
        return found[0]
