"""Authentication endpoints: email/parol, Google, JWT va parolni tiklash."""
from __future__ import annotations

from django.contrib.auth import get_user_model
from rest_framework import permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from accounts.tokens import apply_password_reset, make_reset_token, send_reset_email
from application.accounts.service import normalize_email
from data.google import verify_google_id_token
from domain.shared.errors import ValidationError
from infrastructure.container import container
from presentation.http.serializers import AccountSerializer


def _tokens_for(account) -> dict:
    user = get_user_model().objects.get(pk=account.id)
    refresh = RefreshToken.for_user(user)
    refresh["email"] = account.email
    return {"access": str(refresh.access_token), "refresh": str(refresh)}


def _auth_payload(account) -> dict:
    return {
        "tokens": _tokens_for(account),
        "user": AccountSerializer(account).data,
    }


class RegisterView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        account = container.accounts.register(
            email=request.data.get("email", ""),
            password=request.data.get("password", ""),
            display_name=request.data.get("display_name", ""),
        )
        return Response(_auth_payload(account), status=status.HTTP_201_CREATED)


class LoginView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        account = container.accounts.login(
            email=request.data.get("email", ""),
            password=request.data.get("password", ""),
        )
        return Response(_auth_payload(account))


class GoogleLoginView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        payload = verify_google_id_token(request.data.get("id_token", ""))
        account = container.accounts.from_google(
            email=payload["email"],
            display_name=payload.get("name", ""),
        )
        return Response(_auth_payload(account))


class PasswordResetRequestView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        email = normalize_email(request.data.get("email", ""))
        credentials = make_reset_token(email)
        if credentials is not None:
            send_reset_email(email, *credentials)
        # Always the same answer: never reveal whether the account exists.
        return Response({"detail": "Agar email mavjud bo'lsa, tiklash havolasi yuborildi."})


class PasswordResetConfirmView(APIView):
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        password = request.data.get("password", "")
        if len(password or "") < 8:
            raise ValidationError("Parol kamida 8 belgidan iborat bo'lishi kerak", field="password")
        applied = apply_password_reset(
            request.data.get("uid", ""),
            request.data.get("token", ""),
            password,
        )
        if not applied:
            raise ValidationError("Tiklash havolasi yaroqsiz yoki muddati o'tgan", field="token")
        return Response({"detail": "Parol yangilandi. Endi yangi parol bilan kiring."})


class MeView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        account = container.accounts.get(request.user.id)
        return Response(AccountSerializer(account).data)

    def patch(self, request):
        account = container.accounts.update_profile(
            request.user.id,
            display_name=request.data.get("display_name"),
            language=request.data.get("language"),
        )
        return Response(AccountSerializer(account).data)
