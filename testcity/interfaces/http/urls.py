"""Routes for the clean-architecture API (v2).

The v1 routes in ``api.urls`` stay untouched: the released mobile app depends on
their exact payloads.
"""
from django.urls import include, path
from rest_framework.routers import DefaultRouter

from interfaces.http.views import (
    CertificationProgramViewSet,
    InternationalExamViewSet,
    SubjectViewSet,
)

router = DefaultRouter()
router.register(r"subjects", SubjectViewSet, basename="subjects")
router.register(r"certification", CertificationProgramViewSet, basename="certification")
router.register(r"international", InternationalExamViewSet, basename="international")

urlpatterns = [
    path("", include(router.urls)),
]
