"""Google ID token verification (no SDK dependency, uses the tokeninfo endpoint)."""
from __future__ import annotations

import requests
from django.conf import settings

from domain.shared.errors import AuthenticationError


def verify_google_id_token(id_token: str) -> dict:
    """Return the verified payload or raise :class:`AuthenticationError`."""
    if not id_token:
        raise AuthenticationError("id_token yuborilmadi")
    try:
        response = requests.get(
            settings.GOOGLE_TOKENINFO_URL,
            params={"id_token": id_token},
            timeout=5,
        )
    except requests.RequestException as exc:  # network/HTTP failure
        raise AuthenticationError("Google bilan bog'lanishda xatolik") from exc

    if response.status_code != 200:
        raise AuthenticationError("Google token tekshirilmadi")

    payload = response.json()
    expected_audience = getattr(settings, "GOOGLE_CLIENT_ID", "")
    if expected_audience and payload.get("aud") != expected_audience:
        raise AuthenticationError("Token boshqa ilova uchun berilgan")
    if str(payload.get("email_verified", "")).lower() not in ("true", "1"):
        raise AuthenticationError("Google email tasdiqlanmagan")
    if not payload.get("email"):
        raise AuthenticationError("Google tokenida email topilmadi")
    return payload
