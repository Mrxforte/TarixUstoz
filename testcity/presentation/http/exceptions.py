"""Maps domain errors to HTTP responses so views stay free of error plumbing."""
from __future__ import annotations

from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import exception_handler

from domain.shared.errors import AuthenticationError, NotFoundError, PermissionDeniedError
from domain.shared.errors import ValidationError as DomainValidationError


def domain_exception_handler(exc, context):
    if isinstance(exc, NotFoundError):
        return Response({"detail": str(exc)}, status=status.HTTP_404_NOT_FOUND)
    if isinstance(exc, AuthenticationError):
        return Response({"detail": exc.message}, status=status.HTTP_401_UNAUTHORIZED)
    if isinstance(exc, PermissionDeniedError):
        return Response({"detail": exc.message}, status=status.HTTP_403_FORBIDDEN)
    if isinstance(exc, DomainValidationError):
        payload = {"detail": exc.message}
        if exc.field:
            payload["field"] = exc.field
        return Response(payload, status=status.HTTP_400_BAD_REQUEST)
    return exception_handler(exc, context)
