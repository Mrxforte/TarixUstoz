"""Assessment entities shared by every subject: questions, topics, test tickets."""
from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum


class Difficulty(StrEnum):
    EASY = "EASY"
    MEDIUM = "MEDIUM"
    HARD = "HARD"
    EXAM = "EXAM"

    @classmethod
    def coerce(cls, value: "str | Difficulty | None") -> "Difficulty | None":
        if value is None or value == "":
            return None
        if isinstance(value, cls):
            return value
        normalized = str(value).strip().upper()
        if normalized in ("ANY", "ALL", "NONE"):
            return None
        return cls(normalized)


class QuestionType(StrEnum):
    SINGLE_CHOICE = "SINGLE_CHOICE"
    TRUE_FALSE = "TRUE_FALSE"
    CHRONOLOGICAL = "CHRONOLOGICAL"
    MATCHING = "MATCHING"
    SHORT_ANSWER = "SHORT_ANSWER"


@dataclass(frozen=True, slots=True)
class QuestionRef:
    """Lightweight question snapshot used by quizzes (no ORM instances leak out)."""

    id: int
    text: str
    option_a: str
    option_b: str
    option_c: str
    option_d: str
    correct_option: str
    difficulty: str = Difficulty.MEDIUM.value
    topic_id: int | None = None
    subject: str = ""
    grade: int = 6
    explanation: str = ""
    direction_code: str = ""

    @property
    def options(self) -> dict[str, str]:
        return {"A": self.option_a, "B": self.option_b, "C": self.option_c, "D": self.option_d}

    def is_correct(self, answer: str) -> bool:
        return (answer or "").strip().upper() == (self.correct_option or "").strip().upper()


@dataclass(frozen=True, slots=True)
class QuestionFilter:
    """Read model used to build a ticket / daily question for one subject."""

    subject: str = ""
    grade: int = 0
    topic_id: int | None = None
    direction: str = ""
    difficulty: Difficulty | None = None
    since_id: int | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "direction", (self.direction or "").strip().upper())
        object.__setattr__(self, "difficulty", Difficulty.coerce(self.difficulty))


@dataclass(frozen=True, slots=True)
class AssessmentOverview:
    """What the subject screen shows before the student starts a test."""

    subject: str
    grade: int
    question_count: int
    topic_count: int
    book_count: int
    direction_counts: dict[str, int]

    @property
    def has_content(self) -> bool:
        return self.question_count > 0


@dataclass(frozen=True, slots=True)
class Ticket:
    """A ready-to-serve test ticket."""

    questions: tuple[QuestionRef, ...]
    subject: str = ""
    grade: int = 0
    direction: str = ""

    @property
    def size(self) -> int:
        return len(self.questions)
