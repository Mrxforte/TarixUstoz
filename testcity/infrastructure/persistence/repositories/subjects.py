"""Django ORM adapter for the subject catalogue."""
from __future__ import annotations

from collections.abc import Sequence

from catalog.models import Subject as SubjectModel
from domain.subjects.entities import Subject, SubjectCategory


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
    )


class DjangoSubjectRepository:
    """Implements :class:`domain.subjects.ports.SubjectRepository`."""

    def list(
        self,
        *,
        category: SubjectCategory | None = None,
        active_only: bool = True,
    ) -> Sequence[Subject]:
        queryset = SubjectModel.objects.all()
        if category is not None:
            queryset = queryset.filter(category=SubjectCategory.coerce(category).value)
        if active_only:
            queryset = queryset.filter(is_active=True)
        return [to_entity(row) for row in queryset]

    def get(self, code: str) -> Subject | None:
        row = SubjectModel.objects.filter(code=(code or "").strip().lower()).first()
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
        return to_entity(row)
