"""Leaderboard use cases."""
from __future__ import annotations

from domain.leaderboard.entities import Leaderboard, LeaderboardPeriod
from domain.leaderboard.ports import LeaderboardRepository
from domain.shared.errors import ValidationError

MIN_LIMIT = 1
MAX_LIMIT = 200
MAX_POINTS_PER_SUBMISSION = 10000


class LeaderboardService:
    def __init__(self, leaderboard: LeaderboardRepository) -> None:
        self._leaderboard = leaderboard

    def top(self, period: LeaderboardPeriod | str | None = None, *, limit: int = 50) -> Leaderboard:
        resolved = LeaderboardPeriod.coerce(period)
        limit = max(MIN_LIMIT, min(int(limit), MAX_LIMIT))
        return Leaderboard(
            period=resolved,
            entries=tuple(self._leaderboard.top(resolved, limit=limit)),
            total_players=self._leaderboard.total_players(),
        )

    def rank_of(self, user_id: int, period: LeaderboardPeriod | str | None = None) -> int | None:
        return self._leaderboard.rank_of(user_id, LeaderboardPeriod.coerce(period))

    def submit_points(self, user_id: int, points: int, *, streak: int = 0) -> int:
        if not 0 <= int(points) <= MAX_POINTS_PER_SUBMISSION:
            raise ValidationError(
                f"Ball 0 va {MAX_POINTS_PER_SUBMISSION} orasida bo'lishi kerak", field="points"
            )
        return self._leaderboard.add_points(int(user_id), int(points), streak=int(streak))
