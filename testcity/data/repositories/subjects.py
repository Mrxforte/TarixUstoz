"""Django ORM adapter for the subject catalogue, languages and translations."""
from __future__ import annotations

from collections.abc import Sequence

from catalog.models import Language as LanguageModel
from catalog.models import Subject as SubjectModel
from catalog.models import SubjectTranslation
from domain.subjects.entities import Language, Subject, SubjectCategory


def _translations(row: SubjectModel) -> dict[str, str]:
    return {translation.language.code: translation.name for translation in row.translations.all()}


def to_entity(row: SubjectModel) -> Subject:
    return Subject(
        code=row.code,
        name=row.name,
        category=row.category,
        name_en=row.name_en,
        grades=tuple(row.grades or ()),
        icon=row.icon,
        order=row.order,
        is_active=row.is_active,
        translations=_translations(row),
    )


def language_to_entity(row: LanguageModel) -> Language:
    return Language(
        code=row.code,
        name=row.name,
        native_name=row.native_name,
        is_active=row.is_active,
        is_default=row.is_default,
        order=row.order,
    )


class DjangoSubjectRepository:
    """Implements :class:`domain.subjects.ports.SubjectRepository`."""

    def list(
        self,
        *,
        category: SubjectCategory | None = None,
        active_only: bool = True,
    ) -> Sequence[Subject]:
        queryset = SubjectModel.objects.prefetch_related("translations__language")
        if category is not None:
            queryset = queryset.filter(category=SubjectCategory.coerce(category).value)
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [to_entity(row) for row in queryset]

    def get(self, code: str) -> Subject | None:
        row = (
            SubjectModel.objects.prefetch_related("translations__language")
            .filter(code=(code or "").strip().lower())
            .first()
        )
        return to_entity(row) if row is not None else None

    def save(self, subject: Subject) -> Subject:
        row, _ = SubjectModel.objects.update_or_create(
            code=subject.code,
            defaults={
                "name": subject.name,
                "name_en": subject.name_en,
                "category": subject.category.value,
                "grades": list(subject.grades),
                "icon": subject.icon,
                "order": subject.order,
                "is_active": subject.is_active,
            },
        )
        for language_code, name in subject.translations.items():
            language = LanguageModel.objects.filter(code=language_code).first()
            if language is None:
                continue
            SubjectTranslation.objects.update_or_create(
                subject=row, language=language, defaults={"name": name}
            )
        return self.get(subject.code) or subject


class DjangoLanguageRepository:
    """Implements :class:`domain.subjects.ports.LanguageRepository`."""

    def list(self, *, active_only: bool = True) -> Sequence[Language]:
        queryset = LanguageModel.objects.all()
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [language_to_entity(row) for row in queryset]

    def get(self, code: str) -> Language | None:
        row = LanguageModel.objects.filter(code=(code or "").strip().lower()).first()
        return language_to_entity(row) if row is not None else None

    def default(self) -> Language | None:
        row = LanguageModel.objects.filter(is_default=True).first() or LanguageModel.objects.first()
        return language_to_entity(row) if row is not None else None

    def save(self, language: Language) -> Language:
        row, _ = LanguageModel.objects.update_or_create(
            code=language.code,
            defaults={
                "name": language.name,
                "native_name": language.native_name,
                "is_active": language.is_active,
                "is_default": language.is_default,
                "order": language.order,
            },
        )
        return language_to_entity(row)
