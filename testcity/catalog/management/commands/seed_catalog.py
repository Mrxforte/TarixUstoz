"""``manage.py seed_catalog`` — loads the shipped fanlar and exam blueprints."""
from __future__ import annotations

from django.core.management.base import BaseCommand

from application.seeds import (
    default_certification_programs,
    default_international_exams,
    default_subjects,
)
from infrastructure.container import container


class Command(BaseCommand):
    help = "Fanlar katalogi, milliy sertifikat/attestatsiya va SAT/IELTS/CEFR dasturlarini yuklaydi."

    def handle(self, *args, **options) -> None:
        subjects = container.subject_repository
        for subject in default_subjects():
            subjects.save(subject)

        certification = container.certification_repository
        for program in default_certification_programs():
            certification.save(program)

        international = container.international_repository
        for exam in default_international_exams():
            international.save(exam)

        self.stdout.write(self.style.SUCCESS(
            "Catalog seeded: "
            f"{len(default_subjects())} fan, "
            f"{len(default_certification_programs())} sertifikat dasturi, "
            f"{len(default_international_exams())} xalqaro test."
        ))
