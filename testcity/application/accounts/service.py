"""Account use cases: email/parol, Google, profilni yangilash."""
from __future__ import annotations

from domain.accounts.entities import Account
from domain.accounts.ports import AccountRepository
from domain.shared.errors import AuthenticationError, NotFoundError, ValidationError

MIN_PASSWORD_LENGTH = 8


def normalize_email(email: str) -> str:
    return (email or "").strip().lower()


class AccountService:
    def __init__(self, accounts: AccountRepository) -> None:
        self._accounts = accounts

    def register(self, *, email: str, password: str, display_name: str = "") -> Account:
        email = normalize_email(email)
        if "@" not in email or len(email) < 5:
            raise ValidationError("Email noto'g'ri", field="email")
        if len(password or "") < MIN_PASSWORD_LENGTH:
            raise ValidationError(
                f"Parol kamida {MIN_PASSWORD_LENGTH} belgidan iborat bo'lishi kerak", field="password"
            )
        if self._accounts.get_by_email(email) is not None:
            raise ValidationError("Bu email allaqachon ro'yxatdan o'tgan", field="email")
        return self._accounts.create(
            email=email,
            password=password,
            display_name=(display_name or "").strip() or email.split("@")[0],
            auth_provider="email",
        )

    def login(self, *, email: str, password: str) -> Account:
        account = self._accounts.authenticate(email=normalize_email(email), password=password or "")
        if account is None:
            raise AuthenticationError()
        if not account.is_active:
            raise AuthenticationError("Hisob faol emas")
        return account

    def from_google(self, *, email: str, display_name: str = "") -> Account:
        email = normalize_email(email)
        if "@" not in email:
            raise ValidationError("Google tokenida email topilmadi", field="email")
        existing = self._accounts.get_by_email(email)
        if existing is not None:
            return existing
        return self._accounts.create(
            email=email,
            password=None,
            display_name=(display_name or "").strip() or email.split("@")[0],
            auth_provider="google",
        )

    def get(self, account_id: int) -> Account:
        account = self._accounts.get(int(account_id))
        if account is None:
            raise NotFoundError("Foydalanuvchi", str(account_id))
        return account

    def update_profile(
        self,
        account_id: int,
        *,
        display_name: str | None = None,
        language: str | None = None,
    ) -> Account:
        return self._accounts.update_profile(account_id, display_name=display_name, language=language)
