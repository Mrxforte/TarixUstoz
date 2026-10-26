from django.core.management.base import BaseCommand
from api.models import (
    ExamCategory, ExaminationAuthority,
    ExaminationProgram, ExaminationBlueprint, BlueprintVersion
)

class Command(BaseCommand):
    help = 'Seed official/practice examination blueprints for Attestation, National Certificate, and DTM.'

    def handle(self, *args, **options):
        self.stdout.write("Seeding examination blueprints...")

        categories_data = [
            {
                "code": "ATTESTATION",
                "title": "O'qituvchilar attestatsiyasi imtihoni",
                "description": "Tarix fani o'qituvchilari uchun rasmiy attestatsiya va toifa sinovlari formati.",
                "question_count_default": 35,
                "time_limit_minutes": 60,
                "difficulty_distribution": {"EASY": 10, "MEDIUM": 50, "HARD": 40},
                "is_official": True
            },
            {
                "code": "NATIONAL_CERTIFICATE",
                "title": "Tarix fanidan Milliy sertifikat imtihoni",
                "description": "BMBA (DTM) binosida o'tkaziladigan umumta'lim va tarix fanidan Milliy Sertifikat imtihon bobi.",
                "question_count_default": 45,
                "time_limit_minutes": 90,
                "difficulty_distribution": {"EASY": 15, "MEDIUM": 45, "HARD": 40},
                "is_official": True
            },
            {
                "code": "DTM_PRACTICE",
                "title": "OTM kirish DTM attestatsiya formati",
                "description": "Oliy ta'lim muassasalariga kirish (BMBA / DTM) 30 ta majburiy va mutaxassislik testlari mashqi.",
                "question_count_default": 30,
                "time_limit_minutes": 60,
                "difficulty_distribution": {"EASY": 20, "MEDIUM": 60, "HARD": 20},
                "is_official": True
            },
            {
                "code": "PRACTICE",
                "title": "Mavzulashtirilgan amaliyot testlari",
                "description": "Darsliklar, sinflar va mavzular bo'yicha moslashuvchan amaliyot va bilimni sinash testlari.",
                "question_count_default": 10,
                "time_limit_minutes": 20,
                "difficulty_distribution": {"EASY": 30, "MEDIUM": 50, "HARD": 20},
                "is_official": False
            }
        ]

        for cat in categories_data:
            obj, created = ExamCategory.objects.get_or_create(
                code=cat["code"],
                defaults=cat
            )
            if created:
                self.stdout.write(self.style.SUCCESS(f"Created ExamCategory: {obj.title}"))

        auth, _ = ExaminationAuthority.objects.get_or_create(
            code="BMBA",
            defaults={
                "name": "O'zbekiston Respublikasi Bilim va malakalarni baholash agentligi (BMBA / DTM)",
                "official_website": "https://uzbmb.uz"
            }
        )

        prog_att, _ = ExaminationProgram.objects.get_or_create(
            code="ATTESTATION_PROG",
            defaults={"authority": auth, "title": "Pedagog kadrlar attestatsiyasi", "description": "Tarix fani o'qituvchilari attestatsiyasi"}
        )

        prog_cert, _ = ExaminationProgram.objects.get_or_create(
            code="CERTIFICATE_PROG",
            defaults={"authority": auth, "title": "Milliy sertifikat sinovi", "description": "Tarix fanidan bilim darajasini baholash milliy sertifikati"}
        )

        bp_att, _ = ExaminationBlueprint.objects.get_or_create(
            program=prog_att,
            title="Tarix O'qituvchilar Attestatsiya Blueprinti 2025",
            defaults={"applicable_year": 2025, "is_active": True}
        )

        BlueprintVersion.objects.get_or_create(
            blueprint=bp_att,
            version_number="1.0",
            defaults={
                "total_question_count": 35,
                "duration_minutes": 60,
                "difficulty_distribution": {"EASY": 10, "MEDIUM": 50, "HARD": 40}
            }
        )

        self.stdout.write(self.style.SUCCESS("Exam blueprints successfully seeded!"))
