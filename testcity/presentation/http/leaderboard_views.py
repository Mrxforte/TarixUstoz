"""Leaderboard endpoints (cached per period + limit)."""
from __future__ import annotations

from django.conf import settings
from rest_framework import permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from infrastructure.container import container
from presentation.http.caching import cache_key, get_or_compute
from presentation.http.serializers import LeaderboardEntrySerializer


def _int_param(request, name: str, default: int = 0) -> int:
    raw = request.query_params.get(name)
    try:
        return int(raw) if raw not in (None, "") else default
    except (TypeError, ValueError):
        return default


class LeaderboardViewSet(viewsets.ViewSet):
    """Global reyting: barcha davr va haftalik."""

    permission_classes = [permissions.AllowAny]

    def list(self, request):
        period = request.query_params.get("period", "all")
        limit = _int_param(request, "limit", 50)
        key = cache_key("leaderboard", period, limit)
        payload = get_or_compute(
            key,
            lambda: [
                LeaderboardEntrySerializer(entry).data
                for entry in container.leaderboard.top(period, limit=limit).entries
            ],
            timeout=getattr(settings, "CACHE_LEADERBOARD_TTL", 60),
        )
        board = container.leaderboard.top(period, limit=limit)
        response = {
            "period": board.period.value,
            "total_players": board.total_players,
            "entries": payload,
        }
        if request.user.is_authenticated:
            response["me"] = {
                "rank": container.leaderboard.rank_of(request.user.id, period),
                "points": getattr(request.user, "score", None).points if hasattr(request.user, "score") else 0,
            }
        return Response(response)

    @action(detail=False, methods=["post"], permission_classes=[permissions.IsAuthenticated])
    def submit(self, request):
        try:
            total = container.leaderboard.submit_points(
                int(request.user.id),
                int(request.data.get("points", 0)),
                streak=int(request.data.get("streak", 0)),
            )
        except (TypeError, ValueError):
            return Response({"detail": "points butun son bo'lishi kerak"}, status=status.HTTP_400_BAD_REQUEST)
        return Response({"points": total})
