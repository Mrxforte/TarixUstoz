"""Response caching helpers shared by the v2 endpoints."""
from __future__ import annotations

import hashlib
import json

from django.core.cache import cache
from rest_framework import status
from rest_framework.response import Response


def cache_key(*parts: object) -> str:
    return "testcity:" + ":".join(str(part).lower() for part in parts)


def get_or_compute(key: str, producer, *, timeout: int, version: int = 1):
    """Read a payload from the cache, computing and storing it on a miss."""
    cached = cache.get(key)
    if cached is not None:
        return cached
    value = producer()
    cache.set(key, value, timeout=timeout)
    return value


def etag_response(request, payload, *, cache_seconds: int = 60) -> Response:
    """Conditional GET: identical payloads are answered with 304 Not Modified."""
    encoded = json.dumps(payload, sort_keys=True, ensure_ascii=False, default=str).encode("utf-8")
    etag = f'W/"{hashlib.md5(encoded).hexdigest()}"'
    if request.headers.get("If-None-Match", "").strip() == etag:
        response = Response(status=status.HTTP_304_NOT_MODIFIED)
    else:
        response = Response(payload)
    response["ETag"] = etag
    response["Cache-Control"] = f"public, max-age={cache_seconds}, must-revalidate"
    return response
