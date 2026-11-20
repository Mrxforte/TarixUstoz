"""Maps domain errors to HTTP responses so views stay free of error plumbing."""
from __future__ import annotations

from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler

from domain.shared.errors import NotFoundError
from domain.shared.errors import ValidationError as DomainValidationError


def domain_exception_handler(exc, context):
    if isinstance(exc, NotFoundError):
        return Response({"detail": str(exc)}, status=status.HTTP_404_NOT_FOUND)
    if isinstance(exc, DomainValidationError):
        payload = {"detail": exc.message}
        if exc.field:
            payload["field"] = exc.field
        return Response(payload, status=status.HTTP_400_BAD_REQUEST)
    return exception_handler(exc, context)
