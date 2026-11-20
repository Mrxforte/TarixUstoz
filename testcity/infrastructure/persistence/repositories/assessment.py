"""Django ORM adapters for question/topic/book reads used by assessment services."""
from __future__ import annotations

from collections.abc import Sequence

from django.db.models import Max, Min, QuerySet

from api.directions import direction_counts as legacy_direction_counts
from api.models import PdfBook, Question, Topic
from domain.assessment.directions import COMBINATION_DIRECTIONS, normalize_code
from domain.assessment.entities import QuestionFilter, QuestionRef


def to_ref(row: Question) -> QuestionRef:
    return QuestionRef(
        id=row.id,
        text=row.text,
        option_a=row.option_a,
        option_b=row.option_b,
        option_c=row.option_c,
        option_d=row.option_d,
        correct_option=row.correct_option,
        difficulty=row.difficulty,
        topic_id=row.topic_id,
        subject=row.subject,
        grade=row.grade,
        explanation=row.explanation or "",
        direction_code=row.direction_code or "",
    )


class DjangoQuestionRepository:
    """Implements :class:`domain.assessment.ports.QuestionRepository`."""

    def _base(self, query: QuestionFilter) -> QuerySet:
        queryset = Question.objects.filter(verification_status="VERIFIED")
        if query.subject:
            queryset = queryset.filter(subject=query.subject)
        if query.grade:
            queryset = queryset.filter(grade=query.grade)
        if query.topic_id:
            queryset = queryset.filter(topic_id=query.topic_id)
        if query.difficulty is not None:
            queryset = queryset.filter(difficulty=query.difficulty.value)
        if query.since_id:
            queryset = queryset.filter(id__gt=query.since_id)
        if query.direction:
            code = normalize_code(query.direction)
            if code == "WAR":
                queryset = queryset.filter(is_war=True)
            elif code == "FACT":
                queryset = queryset.filter(is_fact=True)
            elif code in COMBINATION_DIRECTIONS:
                queryset = queryset.filter(direction_code=code)
        return queryset

    def count(self, query: QuestionFilter) -> int:
        return self._base(query).count()

    def page_ids(self, query: QuestionFilter, *, offset: int = 0, limit: int = 30) -> Sequence[int]:
        return list(
            self._base(query)
            .order_by("id")
            .values_list("id", flat=True)[offset: offset + limit]
        )

    def fetch(self, ids: Sequence[int]) -> Sequence[QuestionRef]:
        rows = Question.objects.in_bulk(list(ids))
        refs = []
        for question_id in ids:
            row = rows.get(question_id)
            if row is not None:
                refs.append(to_ref(row))
        return refs

    def daily_id(
        self,
        query: QuestionFilter,
        *,
        day_seed: int,
        excluded_ids: Sequence[int] = (),
    ) -> int | None:
        queryset = self._base(query)
        if not queryset.exists():
            queryset = Question.objects.all()
        bounds = queryset.aggregate(min_id=Min("id"), max_id=Max("id"))
        min_id, max_id = bounds["min_id"], bounds["max_id"]
        if min_id is None or max_id is None:
            return None
        span = max(1, max_id - min_id + 1)
        candidates = queryset.exclude(id__in=list(excluded_ids)) if excluded_ids else queryset
        for attempt in range(5):
            target_id = min_id + ((day_seed + attempt * 9973) % span)
            row = candidates.filter(id__gte=target_id).order_by("id").only("id").first()
            if row is not None:
                return row.id
        fallback = candidates.order_by("id").only("id").first() or queryset.order_by("id").only("id").first()
        return fallback.id if fallback is not None else None

    def direction_counts(self, *, grade: int = 0, subject: str = "") -> dict[str, int]:
        return legacy_direction_counts(grade=grade, subject=subject)


class DjangoTopicRepository:
    """Implements :class:`domain.assessment.ports.TopicRepository`."""

    def count(self, *, subject: str = "", grade: int = 0) -> int:
        queryset = Topic.objects.all()
        if subject:
            queryset = queryset.filter(document__subject__icontains=subject)
        if grade:
            queryset = queryset.filter(document__grade=grade)
        return queryset.count()


class DjangoBookRepository:
    """Implements :class:`domain.assessment.ports.BookRepository`."""

    def count(self, *, subject: str = "", grade: int = 0) -> int:
        queryset = PdfBook.objects.all()
        if subject:
            queryset = queryset.filter(subject__icontains=subject)
        if grade:
            queryset = queryset.filter(grade=grade)
        return queryset.count()
