"""Django ORM adapter for international exams (SAT, IELTS, CEFR)."""
from __future__ import annotations

from collections.abc import Sequence

from catalog.models import INTERNATIONAL_KIND_VALUES, ExamProgram
from domain.international.entities import (
    CefrLevel,
    ExamSection,
    InternationalExam,
    InternationalExamKind,
)


def to_entity(program: ExamProgram) -> InternationalExam:
    kind = InternationalExamKind.coerce(program.kind)
    bands = list(program.bands.all())
    cefr_levels = ()
    if kind in (InternationalExamKind.IELTS, InternationalExamKind.CEFR):
        cefr_levels = tuple(
            CefrLevel(level=band.label, min_score=float(band.min_score), description=band.description)
            for band in bands
        )
    return InternationalExam(
        code=program.code,
        name=program.name,
        kind=kind,
        max_score=float(program.max_score),
        duration_minutes=program.duration_minutes,
        sections=tuple(
            ExamSection(
                code=section.code,
                name=section.name,
                max_score=float(section.max_score),
                question_count=section.question_count,
                time_limit_minutes=section.time_limit_minutes,
                order=section.order,
            )
            for section in program.sections.all()
        ),
        cefr_levels=cefr_levels,
        is_active=program.is_active,
    )


class DjangoInternationalExamRepository:
    """Implements :class:`domain.international.ports.InternationalExamRepository`."""

    def list(
        self,
        *,
        kind: InternationalExamKind | None = None,
        active_only: bool = True,
    ) -> Sequence[InternationalExam]:
        queryset = (
            ExamProgram.objects.filter(kind__in=INTERNATIONAL_KIND_VALUES)
            .prefetch_related("sections", "bands")
        )
        if kind is not None:
            queryset = queryset.filter(kind=InternationalExamKind.coerce(kind).value)
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [to_entity(program) for program in queryset]

    def get(self, code: str) -> InternationalExam | None:
        program = (
            ExamProgram.objects.filter(
                code=(code or "").strip().lower(), kind__in=INTERNATIONAL_KIND_VALUES
            )
            .prefetch_related("sections", "bands")
            .first()
        )
        return to_entity(program) if program is not None else None

    def save(self, exam: InternationalExam) -> InternationalExam:
        from catalog.models import ProgramSection as ProgramSectionModel, ScoreBand as ScoreBandModel

        row, _ = ExamProgram.objects.update_or_create(
            code=exam.code,
            defaults={
                "name": exam.name,
                "kind": exam.kind.value,
                "subject": None,
                "duration_minutes": exam.duration_minutes,
                "max_score": exam.max_score,
                "is_active": exam.is_active,
            },
        )
        row.sections.all().delete()
        row.bands.all().delete()
        ProgramSectionModel.objects.bulk_create([
            ProgramSectionModel(
                program=row,
                code=section.code,
                name=section.name,
                question_count=section.question_count,
                max_score=section.max_score,
                time_limit_minutes=section.time_limit_minutes,
                order=section.order,
            )
            for section in exam.sections
        ])
        ScoreBandModel.objects.bulk_create([
            ScoreBandModel(
                program=row,
                label=level.level,
                min_score=level.min_score,
                max_score=exam.max_score,
                description=level.description,
                order=index,
            )
            for index, level in enumerate(exam.cefr_levels)
        ])
        return self.get(exam.code) or exam
