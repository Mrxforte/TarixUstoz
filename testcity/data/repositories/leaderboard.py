"""Django ORM adapter for the leaderboard."""
from __future__ import annotations

from collections.abc import Sequence

from django.utils import timezone

from accounts.models import UserScore
from domain.leaderboard.entities import LeaderboardEntry, LeaderboardPeriod

_PERIOD_FIELDS = {
    LeaderboardPeriod.ALL: "points",
    LeaderboardPeriod.WEEKLY: "weekly_points",
}


class DjangoLeaderboardRepository:
    """Implements :class:`domain.leaderboard.ports.LeaderboardRepository`."""

    def top(self, period: LeaderboardPeriod, *, limit: int) -> Sequence[LeaderboardEntry]:
        field = _PERIOD_FIELDS[LeaderboardPeriod.coerce(period)]
        rows = (
            UserScore.objects.select_related("user", "user__profile")
            .order_by(f"-{field}", "id")[:limit]
        )
        entries = []
        for rank, row in enumerate(rows, start=1):
            profile = getattr(row.user, "profile", None)
            entries.append(
                LeaderboardEntry(
                    rank=rank,
                    user_id=row.user_id,
                    display_name=(profile.display_name if profile else "") or row.user.get_username(),
                    points=int(getattr(row, field)),
                    streak=row.streak,
                )
            )
        return entries

    def rank_of(self, user_id: int, period: LeaderboardPeriod) -> int | None:
        field = _PERIOD_FIELDS[LeaderboardPeriod.coerce(period)]
        row = UserScore.objects.filter(user_id=user_id).only(field).first()
        if row is None:
            return None
        return UserScore.objects.filter(**{f"{field}__gt": getattr(row, field)}).count() + 1

    def total_players(self) -> int:
        return UserScore.objects.count()

    def add_points(self, user_id: int, points: int, *, streak: int = 0) -> int:
        score, _ = UserScore.objects.get_or_create(user_id=user_id)
        score.points += max(0, points)
        score.weekly_points += max(0, points)
        score.streak = max(score.streak, max(0, streak))
        score.last_activity = timezone.now()
        score.save(update_fields=["points", "weekly_points", "streak", "last_activity", "updated_at"])
        return score.points
