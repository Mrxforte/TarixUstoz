"""Routes for the clean-architecture API (v2).

The v1 routes in ``api.urls`` stay untouched: the released mobile app depends on
their exact payloads.
"""
from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenRefreshView

from presentation.http.auth_views import (
    GoogleLoginView,
    LoginView,
    MeView,
    PasswordResetConfirmView,
    PasswordResetRequestView,
    RegisterView,
)
from presentation.http.leaderboard_views import LeaderboardViewSet
from presentation.http.views import (
    CertificateProgramViewSet,
    InternationalCertificateViewSet,
    LanguageViewSet,
    NationalCertificateViewSet,
    SubjectViewSet,
)

router = DefaultRouter()
router.register(r"languages", LanguageViewSet, basename="languages")
router.register(r"subjects", SubjectViewSet, basename="subjects")
router.register(r"certificates", CertificateProgramViewSet, basename="certificates")
router.register(r"certification", NationalCertificateViewSet, basename="certification")
router.register(r"international", InternationalCertificateViewSet, basename="international")
router.register(r"leaderboard", LeaderboardViewSet, basename="leaderboard")

urlpatterns = [
    path("auth/register/", RegisterView.as_view(), name="auth-register"),
    path("auth/login/", LoginView.as_view(), name="auth-login"),
    path("auth/google/", GoogleLoginView.as_view(), name="auth-google"),
    path("auth/refresh/", TokenRefreshView.as_view(), name="auth-refresh"),
    path("auth/password-reset/", PasswordResetRequestView.as_view(), name="auth-password-reset"),
    path("auth/password-reset/confirm/", PasswordResetConfirmView.as_view(), name="auth-password-reset-confirm"),
    path("auth/me/", MeView.as_view(), name="auth-me"),
    path("", include(router.urls)),
]
