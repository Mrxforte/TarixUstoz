"""Domain-level errors. The HTTP layer maps them to status codes."""
from __future__ import annotations


class DomainError(Exception):
    """Base class for every expected domain failure."""


class ValidationError(DomainError):
    """The caller supplied data that violates a business rule."""

    def __init__(self, message: str, *, field: str | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.field = field


class NotFoundError(DomainError):
    """A requested entity does not exist."""

    def __init__(self, entity: str, key: str) -> None:
        super().__init__(f"{entity} '{key}' topilmadi")
        self.entity = entity
        self.key = key
