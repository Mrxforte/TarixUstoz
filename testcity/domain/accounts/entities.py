"""Accounts: identity of a platform user across email and Google sign-in."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class Account:
    id: int | None
    email: str
    display_name: str = ""
    is_active: bool = True
    language: str = ""
    auth_provider: str = "email"
    points: int = 0
    streak: int = 0
