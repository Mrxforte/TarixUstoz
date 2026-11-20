"""V2 HTTP views: thin adapters that call application services."""
from __future__ import annotations

from datetime import date

from django.conf import settings
from rest_framework import permissions, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from application.assessment.service import daily_seed
from domain.assessment.entities import TicketKind, TicketPlan, TicketSize
from domain.certificates.entities import CertificateFamily, CertificateKind
from domain.subjects.entities import SubjectCategory
from infrastructure.container import container
from presentation.http.caching import cache_key, get_or_compute
from presentation.http.serializers import (
    AssessmentOverviewSerializer,
    CertificateFamilySerializer,
    CertificateProgramSerializer,
    CertificateResultSerializer,
    LanguageSerializer,
    QuestionRefSerializer,
    SubjectSerializer,
)


def _int_param(request, name: str, default: int = 0) -> int:
    raw = request.query_params.get(name)
    try:
        return int(raw) if raw not in (None, "") else default
    except (TypeError, ValueError):
        return default


def _excluded_ids(request) -> tuple[int, ...]:
    raw = request.query_params.get("exclude", "")
    ids = []
    for token in raw.split(","):
        cleaned = token.strip().removeprefix("q_")
        if cleaned.isdigit():
            ids.append(int(cleaned))
    return tuple(ids)


def _language(request) -> str:
    return container.subjects.resolve_language(request.query_params.get("lang"))


class LanguageViewSet(viewsets.ViewSet):
    """Dinamik tillar ro'yxati (uz, ru, en, kz, tj, ...)."""

    permission_classes = [permissions.AllowAny]

    def list(self, request):
        return Response(LanguageSerializer(container.subjects.list_languages(), many=True).data)


class SubjectViewSet(viewsets.ViewSet):
    """Fanlar katalogi va fan bo'yicha test ma'lumotlari."""

    permission_classes = [permissions.AllowAny]
    lookup_field = "code"

    def list(self, request):
        raw_category = request.query_params.get("category")
        category = SubjectCategory.coerce(raw_category) if raw_category else None
        language = _language(request)
        payload = get_or_compute(
            cache_key("subjects", language or "default", raw_category or "all"),
            lambda: list(SubjectSerializer(
                container.subjects.list_subjects(category=category),
                many=True,
                context={"language": language},
            ).data),
            timeout=getattr(settings, "CACHE_CATALOG_TTL", 600),
        )
        return Response(payload)

    def retrieve(self, request, code: str | None = None):
        language = _language(request)
        payload = get_or_compute(
            cache_key("subject", code, language or "default"),
            lambda: self._subject_payload(code, language),
            timeout=getattr(settings, "CACHE_CATALOG_TTL", 600),
        )
        return Response(payload)

    @staticmethod
    def _subject_payload(code: str, language: str) -> dict:
        subject = container.subjects.get_subject(code)
        programs = container.certificates.list_programs(subject_code=subject.code)
        payload = dict(SubjectSerializer(subject, context={"language": language}).data)
        payload["certificates"] = CertificateProgramSerializer(
            programs, many=True, context={"language": language}
        ).data
        return payload

    @action(detail=True, methods=["get"])
    def overview(self, request, code: str | None = None):
        overview = container.assessment.overview(
            code,
            grade=_int_param(request, "grade"),
            track=request.query_params.get("track", ""),
        )
        return Response(AssessmentOverviewSerializer(overview).data)

    @action(detail=True, methods=["get"])
    def tickets(self, request, code: str | None = None):
        page = container.assessment.build_ticket(
            code,
            grade=_int_param(request, "grade"),
            track=request.query_params.get("track", ""),
            direction=request.query_params.get("direction", ""),
            difficulty=request.query_params.get("difficulty", ""),
            size=_int_param(request, "size", 30),
            page=_int_param(request, "ticket", 1),
        )
        return Response({
            "subject": page.ticket.subject,
            "grade": page.ticket.grade,
            "direction": page.ticket.direction,
            "ticket": page.page,
            "tickets": page.ticket_count,
            "total": page.total,
            "size": page.ticket.size,
            "questions": QuestionRefSerializer(page.ticket.questions, many=True).data,
        })

    @action(detail=True, methods=["get"])
    def packs(self, request, code: str | None = None):
        """Global ticket packs: 10/20/30 talik, mavzu/yo'nalish/kitob/aralash/global."""
        plan = TicketPlan(
            kind=request.query_params.get("kind", TicketKind.BOOK.value),
            size=_int_param(request, "size", TicketSize.LARGE.value),
            subject=code or "",
            grade=_int_param(request, "grade"),
            track=request.query_params.get("track", ""),
            topic_id=_int_param(request, "topic_id") or None,
            direction=request.query_params.get("direction", ""),
            difficulty=request.query_params.get("difficulty", ""),
            index=_int_param(request, "ticket", 1),
            seed=_int_param(request, "seed", 0),
        )
        page = container.assessment.build_pack(plan)
        return Response({
            "kind": plan.kind.value,
            "size": plan.page_size,
            "ticket": page.page,
            "tickets": page.ticket_count,
            "total": page.total,
            "questions": QuestionRefSerializer(page.ticket.questions, many=True).data,
        })

    @action(detail=True, methods=["get"])
    def daily(self, request, code: str | None = None):
        grade = _int_param(request, "grade")
        track = request.query_params.get("track", "")
        seed = daily_seed(
            date.today(),
            request.query_params.get("user_identifier", "guest_user"),
            grade,
            code or "",
            request.query_params.get("nonce", ""),
        )
        question = container.assessment.daily_question(
            code,
            grade=grade,
            track=track,
            day_seed=seed,
            excluded_ids=_excluded_ids(request),
        )
        return Response(QuestionRefSerializer(question).data)


class CertificateProgramViewSet(viewsets.ViewSet):
    """Sertifikat testlari: milliy (sertifikat, attestatsiya) va xalqaro (SAT, IELTS, TOEFL, CEFR)."""

    permission_classes = [permissions.AllowAny]
    lookup_field = "code"
    family: CertificateFamily | None = None

    def _family(self, request) -> CertificateFamily | None:
        if self.family is not None:
            return self.family
        raw = request.query_params.get("family")
        return CertificateFamily(raw) if raw else None

    def _kind(self, request) -> CertificateKind | None:
        raw = request.query_params.get("kind")
        return CertificateKind.coerce(raw) if raw else None

    def list(self, request):
        family = self._family(request)
        kind = self._kind(request)
        subject = request.query_params.get("subject") or None
        language = _language(request)
        payload = get_or_compute(
            cache_key("certificates", family or "all", kind or "all", subject or "all", language or "default"),
            lambda: list(CertificateProgramSerializer(
                container.certificates.list_programs(family=family, kind=kind, subject_code=subject),
                many=True,
                context={"language": language},
            ).data),
            timeout=getattr(settings, "CACHE_CATALOG_TTL", 600),
        )
        return Response(payload)

    def retrieve(self, request, code: str | None = None):
        program = container.certificates.get_program(code)
        return Response(CertificateProgramSerializer(
            program, context={"language": _language(request)}
        ).data)

    @action(detail=False, methods=["get"])
    def families(self, request):
        return Response(CertificateFamilySerializer(container.certificates.families(), many=True).data)

    @action(detail=True, methods=["post"])
    def evaluate(self, request, code: str | None = None):
        result = container.certificates.evaluate(code, request.data.get("sections") or {})
        return Response(CertificateResultSerializer(result).data)


class NationalCertificateViewSet(CertificateProgramViewSet):
    family = CertificateFamily.NATIONAL


class InternationalCertificateViewSet(CertificateProgramViewSet):
    family = CertificateFamily.INTERNATIONAL
