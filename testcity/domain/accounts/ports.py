"""Account repository port (implemented with Django auth in the data layer)."""
from __future__ import annotations

from typing import Protocol

from domain.accounts.entities import Account


class AccountRepository(Protocol):
    def get(self, account_id: int) -> Account | None:
        ...

    def get_by_email(self, email: str) -> Account | None:
        ...

    def create(
        self,
        *,
        email: str,
        password: str | None,
        display_name: str = "",
        auth_provider: str = "email",
    ) -> Account:
        ...

    def authenticate(self, *, email: str, password: str) -> Account | None:
        ...

    def update_profile(self, account_id: int, *, display_name: str | None = None, language: str | None = None) -> Account:
        ...
