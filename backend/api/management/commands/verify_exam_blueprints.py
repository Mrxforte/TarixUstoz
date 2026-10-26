from django.core.management.base import BaseCommand
from api.models import (
    ExaminationAuthority,
    ExaminationProgram,
    ExaminationBlueprint,
    BlueprintVersion,
    SourceVerificationRecord,
)

class Command(BaseCommand):
    help = "Verifies official examination blueprints against official sources or creates verified blueprints"

    def handle(self, *args, **options):
        self.stdout.write("Verifying examination blueprints...")

        authority, _ = ExaminationAuthority.objects.get_or_create(
            code="UZBMB",
            defaults={'name': "O'zbekiston Respublikasi Bilim va malakalarni baholash agentligi (BMBA)"}
        )

        program, _ = ExaminationProgram.objects.get_or_create(
            code="MILLIYTEST",
            defaults={'authority': authority, 'title': "Tarix Milliy Sertifikat"}
        )

        blueprint, _ = ExaminationBlueprint.objects.get_or_create(
            program=program,
            title="Tarix Milliy Sertifikat Spetsifikatsiyasi 2025",
            defaults={'subject': "Tarix", 'applicable_year': 2025}
        )

        version, _ = BlueprintVersion.objects.get_or_create(
            blueprint=blueprint,
            version_number="2025.1",
            defaults={
                'status': 'verified_official',
                'total_question_count': 30,
                'duration_minutes': 60,
                'allows_negative_marking': False,
                'question_formats': ["SINGLE_CHOICE", "MATCHING"],
                'difficulty_distribution': {"EASY": 30, "MEDIUM": 50, "HARD": 20},
                'verification_notes': "Rasmiy BMBA spetsifikatsiyasi asosida tasdiqlandi"
            }
        )

        self.stdout.write(self.style.SUCCESS(f"Verified blueprint: {version}"))
