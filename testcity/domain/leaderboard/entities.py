"""Leaderboard: ranked players by points."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum

from domain.shared.errors import ValidationError


class LeaderboardPeriod(StrEnum):
    ALL = "all"
    WEEKLY = "weekly"

    @classmethod
    def coerce(cls, value: "str | LeaderboardPeriod | None") -> "LeaderboardPeriod":
        if value in (None, ""):
            return cls.ALL
        if isinstance(value, cls):
            return value
        try:
            return cls(str(value).strip().lower())
        except ValueError as exc:
            allowed = ", ".join(item.value for item in cls)
            raise ValidationError(f"Noto'g'ri davr: {value!r}. Ruxsat etilgan: {allowed}", field="period") from exc


@dataclass(frozen=True, slots=True)
class LeaderboardEntry:
    rank: int
    user_id: int
    display_name: str
    points: int
    streak: int = 0

    def with_rank(self, rank: int) -> "LeaderboardEntry":
        return LeaderboardEntry(
            rank=rank,
            user_id=self.user_id,
            display_name=self.display_name,
            points=self.points,
            streak=self.streak,
        )


@dataclass(frozen=True, slots=True)
class Leaderboard:
    period: LeaderboardPeriod
    entries: tuple[LeaderboardEntry, ...] = field(default_factory=tuple)
    total_players: int = 0

    def rank_of(self, user_id: int) -> int | None:
        return next((entry.rank for entry in self.entries if entry.user_id == user_id), None)
