"""Django auth adapter for the account port."""
from __future__ import annotations

from django.contrib.auth import authenticate as django_authenticate
from django.contrib.auth import get_user_model

from accounts.models import UserProfile, UserScore
from catalog.models import Language
from domain.accounts.entities import Account

User = get_user_model()


def to_entity(user) -> Account:
    profile = getattr(user, "profile", None)
    score = getattr(user, "score", None)
    return Account(
        id=user.id,
        email=user.email or user.get_username(),
        display_name=(profile.display_name if profile else "") or user.get_username(),
        is_active=user.is_active,
        language=profile.language.code if profile is not None and profile.language_id else "",
        auth_provider=profile.auth_provider if profile is not None else "email",
        points=score.points if score is not None else 0,
        streak=score.streak if score is not None else 0,
    )


class DjangoAccountRepository:
    """Implements :class:`domain.accounts.ports.AccountRepository`."""

    def _queryset(self):
        return User.objects.select_related("profile__language", "score")

    def get(self, account_id: int) -> Account | None:
        user = self._queryset().filter(id=account_id).first()
        return to_entity(user) if user is not None else None

    def get_by_email(self, email: str) -> Account | None:
        email = (email or "").strip().lower()
        if not email:
            return None
        user = self._queryset().filter(email__iexact=email).first()
        if user is None:
            user = self._queryset().filter(username__iexact=email).first()
        return to_entity(user) if user is not None else None

    def create(
        self,
        *,
        email: str,
        password: str | None,
        display_name: str = "",
        auth_provider: str = "email",
    ) -> Account:
        email = (email or "").strip().lower()
        user = User.objects.create_user(username=email, email=email, password=password)
        if password is None:
            user.set_unusable_password()
            user.save(update_fields=["password"])
        UserProfile.objects.create(
            user=user,
            display_name=display_name,
            auth_provider=auth_provider,
        )
        UserScore.objects.get_or_create(user=user)
        return self.get(user.id) or to_entity(user)

    def authenticate(self, *, email: str, password: str) -> Account | None:
        user = django_authenticate(username=(email or "").strip().lower(), password=password or "")
        return to_entity(user) if user is not None else None

    def update_profile(
        self,
        account_id: int,
        *,
        display_name: str | None = None,
        language: str | None = None,
    ) -> Account:
        user = User.objects.filter(id=account_id).first()
        if user is None:
            return Account(id=account_id, email="")
        profile, _ = UserProfile.objects.get_or_create(user=user)
        if display_name is not None:
            profile.display_name = display_name.strip()
        if language is not None:
            profile.language = Language.objects.filter(code=language.strip().lower()).first()
        profile.save()
        return self.get(account_id) or to_entity(user)
