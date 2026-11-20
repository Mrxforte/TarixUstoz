"""Django ORM adapter for certification programs (milliy sertifikat, attestatsiya)."""
from __future__ import annotations

from collections.abc import Sequence

from catalog.models import CERTIFICATION_KIND_VALUES
from catalog.models import ExamProgram, ProgramSection as ProgramSectionModel, ScoreBand as ScoreBandModel
from domain.certification.entities import (
    CertificationKind,
    CertificationProgram,
    ProgramSection,
    ScoreBand,
)


def to_entity(program: ExamProgram) -> CertificationProgram:
    return CertificationProgram(
        code=program.code,
        name=program.name,
        kind=CertificationKind.coerce(program.kind),
        subject_code=program.subject.code if program.subject_id else "",
        max_score=float(program.max_score),
        passing_score=float(program.passing_score) if program.passing_score is not None else None,
        duration_minutes=program.duration_minutes,
        sections=tuple(
            ProgramSection(
                code=section.code,
                name=section.name,
                max_score=float(section.max_score),
                question_count=section.question_count,
                time_limit_minutes=section.time_limit_minutes,
                order=section.order,
            )
            for section in program.sections.all()
        ),
        bands=tuple(
            ScoreBand(
                label=band.label,
                min_score=float(band.min_score),
                max_score=float(band.max_score),
                description=band.description,
                order=band.order,
            )
            for band in program.bands.all()
        ),
        is_active=program.is_active,
    )


class DjangoCertificationProgramRepository:
    """Implements :class:`domain.certification.ports.CertificationProgramRepository`."""

    def list(
        self,
        *,
        kind: CertificationKind | None = None,
        subject_code: str | None = None,
        active_only: bool = True,
    ) -> Sequence[CertificationProgram]:
        queryset = (
            ExamProgram.objects.filter(kind__in=CERTIFICATION_KIND_VALUES)
            .select_related("subject")
            .prefetch_related("sections", "bands")
        )
        if kind is not None:
            queryset = queryset.filter(kind=CertificationKind.coerce(kind).value)
        if subject_code:
            queryset = queryset.filter(subject__code=subject_code.strip().lower())
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [to_entity(program) for program in queryset]

    def get(self, code: str) -> CertificationProgram | None:
        program = (
            ExamProgram.objects.filter(
                code=(code or "").strip().lower(), kind__in=CERTIFICATION_KIND_VALUES
            )
            .select_related("subject")
            .prefetch_related("sections", "bands")
            .first()
        )
        return to_entity(program) if program is not None else None

    def save(self, program: CertificationProgram) -> CertificationProgram:
        program.validate()
        subject_model = None
        if program.subject_code:
            from catalog.models import Subject as SubjectModel

            subject_model = SubjectModel.objects.filter(code=program.subject_code).first()

        row, _ = ExamProgram.objects.update_or_create(
            code=program.code,
            defaults={
                "name": program.name,
                "kind": program.kind.value,
                "subject": subject_model,
                "duration_minutes": program.duration_minutes,
                "max_score": program.max_score,
                "passing_score": program.passing_score,
                "is_active": program.is_active,
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
            for section in program.sections
        ])
        ScoreBandModel.objects.bulk_create([
            ScoreBandModel(
                program=row,
                label=band.label,
                min_score=band.min_score,
                max_score=band.max_score,
                description=band.description,
                order=band.order,
            )
            for band in program.bands
        ])
        return self.get(program.code) or program
