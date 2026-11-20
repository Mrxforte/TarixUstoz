"""Auth (email/Google/reset) and leaderboard HTTP tests."""
from __future__ import annotations

from unittest import mock

from django.contrib.auth import get_user_model
from django.core import mail
from django.core.cache import cache
from django.test import TestCase
from rest_framework.test import APIClient

from accounts.tokens import make_reset_token
from infrastructure.container import container


class AuthApiTests(TestCase):
    def setUp(self):
        cache.clear()
        self.client = APIClient()

    def _register(self, email="ali@test.uz", password="secret123"):
        return self.client.post(
            "/api/v2/auth/register/",
            {"email": email, "password": password, "display_name": "Ali"},
            format="json",
        )

    def test_register_returns_tokens_and_profile(self):
        response = self._register()
        self.assertEqual(response.status_code, 201)
        payload = response.json()
        self.assertIn("access", payload["tokens"])
        self.assertIn("refresh", payload["tokens"])
        self.assertEqual(payload["user"]["email"], "ali@test.uz")
        self.assertEqual(payload["user"]["auth_provider"], "email")

    def test_register_rejects_short_password(self):
        response = self.client.post(
            "/api/v2/auth/register/", {"email": "a@b.uz", "password": "123"}, format="json"
        )
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["field"], "password")

    def test_register_rejects_duplicate_email(self):
        self._register()
        response = self._register()
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["field"], "email")

    def test_login_success_and_failure(self):
        self._register()
        ok = self.client.post(
            "/api/v2/auth/login/", {"email": "ali@test.uz", "password": "secret123"}, format="json"
        )
        self.assertEqual(ok.status_code, 200)
        self.assertIn("access", ok.json()["tokens"])

        bad = self.client.post(
            "/api/v2/auth/login/", {"email": "ali@test.uz", "password": "wrong"}, format="json"
        )
        self.assertEqual(bad.status_code, 401)

    def test_me_requires_authentication(self):
        self.assertEqual(self.client.get("/api/v2/auth/me/").status_code, 401)

    def test_me_returns_and_updates_profile(self):
        access = self._register().json()["tokens"]["access"]
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
        me = self.client.get("/api/v2/auth/me/")
        self.assertEqual(me.status_code, 200)
        self.assertEqual(me.json()["email"], "ali@test.uz")

        patched = self.client.patch("/api/v2/auth/me/", {"display_name": "Vali"}, format="json")
        self.assertEqual(patched.status_code, 200)
        self.assertEqual(patched.json()["display_name"], "Vali")

    def test_password_reset_request_is_neutral(self):
        self._register()
        mail.outbox.clear()
        known = self.client.post("/api/v2/auth/password-reset/", {"email": "ali@test.uz"}, format="json")
        unknown = self.client.post("/api/v2/auth/password-reset/", {"email": "yoq@test.uz"}, format="json")
        self.assertEqual(known.status_code, 200)
        self.assertEqual(unknown.status_code, 200)
        self.assertEqual(known.json(), unknown.json())
        self.assertEqual(len(mail.outbox), 1)

    def test_password_reset_confirm_changes_password(self):
        self._register()
        uid, token = make_reset_token("ali@test.uz")
        response = self.client.post(
            "/api/v2/auth/password-reset/confirm/",
            {"uid": uid, "token": token, "password": "newsecret123"},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        login = self.client.post(
            "/api/v2/auth/login/", {"email": "ali@test.uz", "password": "newsecret123"}, format="json"
        )
        self.assertEqual(login.status_code, 200)

    def test_password_reset_confirm_rejects_bad_token(self):
        self._register()
        response = self.client.post(
            "/api/v2/auth/password-reset/confirm/",
            {"uid": "MQ", "token": "not-a-token", "password": "newsecret123"},
            format="json",
        )
        self.assertEqual(response.status_code, 400)

    def test_google_login_requires_token(self):
        self.assertEqual(self.client.post("/api/v2/auth/google/", {}, format="json").status_code, 401)

    def test_google_login_creates_account(self):
        class _Response:
            status_code = 200

            @staticmethod
            def json():
                return {"aud": "", "email_verified": "true", "email": "g@test.uz", "name": "Google User"}

        with mock.patch("data.google.requests.get", return_value=_Response()):
            response = self.client.post("/api/v2/auth/google/", {"id_token": "token"}, format="json")

        self.assertEqual(response.status_code, 200)
        user = get_user_model().objects.get(email="g@test.uz")
        self.assertFalse(user.has_usable_password())
        self.assertEqual(user.profile.auth_provider, "google")


class LeaderboardApiTests(TestCase):
    def setUp(self):
        cache.clear()
        self.client = APIClient()
        container.accounts.register(email="one@test.uz", password="secret123", display_name="One")
        container.accounts.register(email="two@test.uz", password="secret123", display_name="Two")
        container.accounts.register(email="three@test.uz", password="secret123", display_name="Three")
        users = {user.email: user for user in get_user_model().objects.all()}
        container.leaderboard.submit_points(users["one@test.uz"].id, 30)
        container.leaderboard.submit_points(users["two@test.uz"].id, 70)
        container.leaderboard.submit_points(users["three@test.uz"].id, 50)

    def test_leaderboard_is_ranked_by_points(self):
        payload = self.client.get("/api/v2/leaderboard/").json()
        self.assertEqual(payload["period"], "all")
        self.assertEqual([entry["display_name"] for entry in payload["entries"]], ["Two", "Three", "One"])
        self.assertEqual([entry["rank"] for entry in payload["entries"]], [1, 2, 3])

    def test_weekly_period_is_supported(self):
        payload = self.client.get("/api/v2/leaderboard/", {"period": "weekly"}).json()
        self.assertEqual(payload["period"], "weekly")
        self.assertEqual(payload["entries"][0]["display_name"], "Two")

    def test_invalid_period_returns_400(self):
        self.assertEqual(self.client.get("/api/v2/leaderboard/", {"period": "daily"}).status_code, 400)

    def test_submit_requires_authentication(self):
        response = self.client.post("/api/v2/leaderboard/submit/", {"points": 10}, format="json")
        self.assertEqual(response.status_code, 401)

    def test_submit_increments_points_and_rank(self):
        login = self.client.post(
            "/api/v2/auth/login/", {"email": "one@test.uz", "password": "secret123"}, format="json"
        )
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {login.json()['tokens']['access']}")
        response = self.client.post("/api/v2/leaderboard/submit/", {"points": 100, "streak": 3}, format="json")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["points"], 130)

        cache.clear()
        payload = self.client.get("/api/v2/leaderboard/").json()
        self.assertEqual(payload["entries"][0]["display_name"], "One")
        self.assertEqual(payload["me"]["rank"], 1)
