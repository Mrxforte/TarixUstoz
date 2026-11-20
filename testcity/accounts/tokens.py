"""Password-reset tokens and e-mail helpers (Django auth backed)."""
from __future__ import annotations

from django.conf import settings
from django.contrib.auth import get_user_model
from django.contrib.auth.tokens import default_token_generator
from django.core.mail import send_mail
from django.utils.encoding import force_bytes, force_str
from django.utils.http import urlsafe_base64_decode, urlsafe_base64_encode


def make_reset_token(email: str) -> tuple[str, str] | None:
    """Return ``(uid, token)`` for an active account, or ``None`` when it does not exist."""
    user = get_user_model().objects.filter(email__iexact=(email or "").strip().lower(), is_active=True).first()
    if user is None:
        return None
    return urlsafe_base64_encode(force_bytes(user.pk)), default_token_generator.make_token(user)


def send_reset_email(email: str, uid: str, token: str) -> None:
    link = f"{settings.FRONTEND_RESET_URL}?uid={uid}&token={token}"
    send_mail(
        subject="TestCity: parolni tiklash",
        message=(
            "Parolingizni tiklash uchun quyidagi havolani oching:\n\n"
            f"{link}\n\n"
            "Agar bu so'rovni siz yubormagan bo'lsangiz, xatni e'tiborsiz qoldiring."
        ),
        from_email=settings.DEFAULT_FROM_EMAIL,
        recipient_list=[email],
        fail_silently=True,
    )


def apply_password_reset(uid: str, token: str, password: str) -> bool:
    """Validate the token and set the new password; ``False`` when the token is invalid."""
    try:
        user_id = force_str(urlsafe_base64_decode(uid))
        user = get_user_model().objects.filter(pk=user_id, is_active=True).first()
    except (TypeError, ValueError, OverflowError):
        return False
    if user is None or not default_token_generator.check_token(user, token):
        return False
    user.set_password(password)
    user.save(update_fields=["password"])
    return True
