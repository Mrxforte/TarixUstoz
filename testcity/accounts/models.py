"""User profile, leaderboard scores and auth provider links."""
from __future__ import annotations

from django.conf import settings
from django.db import models


class UserProfile(models.Model):
    """Extends the Django user with app preferences (language, avatar, auth provider)."""

    AUTH_PROVIDERS = (
        ("email", "Email"),
        ("google", "Google"),
    )

    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="profile")
    language = models.ForeignKey(
        "catalog.Language",
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="profiles",
    )
    display_name = models.CharField(max_length=120, blank=True)
    avatar_url = models.URLField(blank=True)
    auth_provider = models.CharField(max_length=16, choices=AUTH_PROVIDERS, default="email")
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Profil"
        verbose_name_plural = "1. Profillar"

    def __str__(self) -> str:
        return self.display_name or self.user.get_username()


class UserScore(models.Model):
    """Materialised points so the leaderboard is a single indexed query."""

    user = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="score")
    points = models.PositiveIntegerField(default=0)
    weekly_points = models.PositiveIntegerField(default=0)
    streak = models.PositiveIntegerField(default=0)
    last_activity = models.DateTimeField(null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = "Reyting ballari"
        verbose_name_plural = "2. Reyting ballari"
        indexes = [
            models.Index(fields=["-points"], name="score_points_idx"),
            models.Index(fields=["-weekly_points"], name="score_weekly_idx"),
        ]

    def __str__(self) -> str:
        return f"{self.user.get_username()}: {self.points}"
