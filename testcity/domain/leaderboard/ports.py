"""Leaderboard repository port."""
from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from domain.leaderboard.entities import LeaderboardEntry, LeaderboardPeriod


class LeaderboardRepository(Protocol):
    def top(self, period: LeaderboardPeriod, *, limit: int) -> Sequence[LeaderboardEntry]:
        ...

    def rank_of(self, user_id: int, period: LeaderboardPeriod) -> int | None:
        ...

    def total_players(self) -> int:
        ...

    def add_points(self, user_id: int, points: int, *, streak: int = 0) -> int:
        ...
