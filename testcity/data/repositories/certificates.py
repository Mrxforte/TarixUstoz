"""Django ORM adapter for certificate programs (milliy + xalqaro)."""
from __future__ import annotations

from collections.abc import Sequence

from catalog.models import (
    CERTIFICATION_KIND_VALUES,
    INTERNATIONAL_KIND_VALUES,
    ExamProgram,
    ExamProgramTranslation,
    ProgramSection as ProgramSectionModel,
    ScoreBand as ScoreBandModel,
)
from domain.certificates.entities import (
    CefrLevel,
    CertificateBand,
    CertificateFamily,
    CertificateKind,
    CertificateProgram,
    CertificateSection,
)

ALL_KIND_VALUES = CERTIFICATION_KIND_VALUES + INTERNATIONAL_KIND_VALUES


def to_entity(program: ExamProgram) -> CertificateProgram:
    kind = CertificateKind.coerce(program.kind)
    bands = list(program.bands.all())
    cefr_levels = ()
    if kind in (CertificateKind.IELTS, CertificateKind.TOEFL, CertificateKind.CEFR):
        cefr_levels = tuple(
            CefrLevel(level=band.label, min_score=float(band.min_score), description=band.description)
            for band in bands
            if band.label.upper() in {"A1", "A2", "B1", "B2", "C1", "C2"}
        )
    return CertificateProgram(
        code=program.code,
        name=program.name,
        kind=kind,
        subject_code=program.subject.code if program.subject_id else "",
        max_score=float(program.max_score),
        passing_score=float(program.passing_score) if program.passing_score is not None else None,
        duration_minutes=program.duration_minutes,
        sections=tuple(
            CertificateSection(
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
            CertificateBand(
                label=band.label,
                min_score=float(band.min_score),
                max_score=float(band.max_score),
                description=band.description,
                order=band.order,
            )
            for band in bands
            if band.label.upper() not in {"A1", "A2", "B1", "B2", "C1", "C2"}
        ),
        cefr_levels=cefr_levels,
        is_active=program.is_active,
        translations={t.language.code: t.name for t in program.translations.all()},
    )


class DjangoCertificateProgramRepository:
    """Implements :class:`domain.certificates.ports.CertificateProgramRepository`."""

    def _base(self):
        return (
            ExamProgram.objects.filter(kind__in=ALL_KIND_VALUES)
            .select_related("subject")
            .prefetch_related("sections", "bands", "translations__language")
        )

    def list(
        self,
        *,
        family: CertificateFamily | None = None,
        kind: CertificateKind | None = None,
        subject_code: str | None = None,
        active_only: bool = True,
    ) -> Sequence[CertificateProgram]:
        queryset = self._base()
        if family is not None:
            queryset = queryset.filter(kind__in=[item.value for item in CertificateKind.for_family(family)])
        if kind is not None:
            queryset = queryset.filter(kind=CertificateKind.coerce(kind).value)
        if subject_code:
            queryset = queryset.filter(subject__code=subject_code.strip().lower())
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [to_entity(program) for program in queryset]

    def get(self, code: str) -> CertificateProgram | None:
        program = self._base().filter(code=(code or "").strip().lower()).first()
        return to_entity(program) if program is not None else None

    def save(self, program: CertificateProgram) -> CertificateProgram:
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
        ] + [
            ScoreBandModel(
                program=row,
                label=level.level,
                min_score=level.min_score,
                max_score=program.max_score,
                description=level.description,
                order=100 + index,
            )
            for index, level in enumerate(program.cefr_levels)
        ])
        for language_code, name in program.translations.items():
            language = _language_model(language_code)
            if language is None:
                continue
            ExamProgramTranslation.objects.update_or_create(
                program=row, language=language, defaults={"name": name}
            )
        return self.get(program.code) or program


def _language_model(code: str):
    from catalog.models import Language as LanguageModel

    return LanguageModel.objects.filter(code=code).first()
