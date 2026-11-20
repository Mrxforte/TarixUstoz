"""Repository ports for assessment data (questions, topics, direction counts)."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.assessment.entities import QuestionFilter, QuestionRef


class QuestionRepository(Protocol):
    def count(self, query: QuestionFilter) -> int:
        """Number of verified questions matching the filter."""

    def random_ids(self, query: QuestionFilter, *, limit: int) -> Sequence[int]:
        """Up to ``limit`` random question ids matching the filter."""

    def fetch(self, ids: Sequence[int]) -> Sequence[QuestionRef]:
        """Load question snapshots for the given ids, keeping the ids' order."""

    def daily_id(self, query: QuestionFilter, *, day_seed: int) -> int | None:
        """Deterministically pick one question id for a given day seed."""

    def direction_counts(self, *, grade: int = 0, subject: str = "") -> dict[str, int]:
        """Counts per direction card, materialised for cheap reads."""


class TopicRepository(Protocol):
    def count(self, *, subject: str = "", grade: int = 0) -> int:
        ...


class BookRepository(Protocol):
    def count(self, *, subject: str = "", grade: int = 0) -> int:
        ...
