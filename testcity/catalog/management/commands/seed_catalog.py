"""``manage.py seed_catalog`` — loads languages, fanlar and certificate blueprints."""
from __future__ import annotations

from django.core.management.base import BaseCommand

from application.seeds import (
    default_certificate_programs,
    default_languages,
    default_subjects,
)
from infrastructure.container import container


class Command(BaseCommand):
    help = "Tillar, fanlar va sertifikat dasturlarini (milliy + xalqaro) yuklaydi. Idempotent."

    def handle(self, *args, **options) -> None:
        languages = container.language_repository
        for language in default_languages():
            languages.save(language)

        subjects = container.subject_repository
        for subject in default_subjects():
            subjects.save(subject)

        certificates = container.certificate_repository
        programs = default_certificate_programs()
        for program in programs:
            certificates.save(program)

        national = sum(1 for program in programs if program.is_national)
        international = sum(1 for program in programs if program.is_international)
        self.stdout.write(self.style.SUCCESS(
            "Catalog seeded: "
            f"{len(default_languages())} til, "
            f"{len(default_subjects())} fan, "
            f"{national} milliy + {international} xalqaro sertifikat dasturi."
        ))
