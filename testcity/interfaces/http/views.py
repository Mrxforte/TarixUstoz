"""V2 HTTP views: thin adapters that call application services."""
from __future__ import annotations

from datetime import date

from rest_framework import permissions, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from application.assessment.service import daily_seed
from domain.certification.entities import CertificationKind
from domain.international.entities import InternationalExamKind
from domain.subjects.entities import SubjectCategory
from infrastructure.container import container
from interfaces.http.serializers import (
    AssessmentOverviewSerializer,
    CertificationProgramSerializer,
    CertificationResultSerializer,
    InternationalExamSerializer,
    InternationalResultSerializer,
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


class SubjectViewSet(viewsets.ViewSet):
    """Fanlar katalogi va fan bo'yicha test ma'lumotlari."""

    permission_classes = [permissions.AllowAny]
    lookup_field = "code"

    def list(self, request):
        raw_category = request.query_params.get("category")
        category = SubjectCategory.coerce(raw_category) if raw_category else None
        subjects = container.subjects.list_subjects(category=category)
        return Response(SubjectSerializer(subjects, many=True).data)

    def retrieve(self, request, code: str | None = None):
        subject = container.subjects.get_subject(code)
        programs = container.certification.list_programs(subject_code=subject.code)
        payload = dict(SubjectSerializer(subject).data)
        payload["programs"] = CertificationProgramSerializer(programs, many=True).data
        return Response(payload)

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


class CertificationProgramViewSet(viewsets.ViewSet):
    """Milliy sertifikat va attestatsiya dasturlari."""

    permission_classes = [permissions.AllowAny]
    lookup_field = "code"

    def list(self, request):
        raw_kind = request.query_params.get("kind")
        kind = CertificationKind.coerce(raw_kind) if raw_kind else None
        programs = container.certification.list_programs(
            kind=kind,
            subject_code=request.query_params.get("subject") or None,
        )
        return Response(CertificationProgramSerializer(programs, many=True).data)

    def retrieve(self, request, code: str | None = None):
        program = container.certification.get_program(code)
        return Response(CertificationProgramSerializer(program).data)

    @action(detail=True, methods=["post"])
    def evaluate(self, request, code: str | None = None):
        result = container.certification.evaluate(code, request.data.get("sections") or {})
        return Response(CertificationResultSerializer(result).data)


class InternationalExamViewSet(viewsets.ViewSet):
    """SAT, IELTS va CEFR testlari."""

    permission_classes = [permissions.AllowAny]
    lookup_field = "code"

    def list(self, request):
        raw_kind = request.query_params.get("kind")
        kind = InternationalExamKind.coerce(raw_kind) if raw_kind else None
        exams = container.international.list_exams(kind=kind)
        return Response(InternationalExamSerializer(exams, many=True).data)

    def retrieve(self, request, code: str | None = None):
        exam = container.international.get_exam(code)
        return Response(InternationalExamSerializer(exam).data)

    @action(detail=True, methods=["post"])
    def evaluate(self, request, code: str | None = None):
        result = container.international.evaluate(code, request.data.get("sections") or {})
        return Response(InternationalResultSerializer(result).data)
