from django.core.management.base import BaseCommand
from api.services.exam_research import ExamSourceResearchService

class Command(BaseCommand):
    help = "Discovers official history exam specifications and sample tests from entry URLs"

    def handle(self, *args, **options):
        self.stdout.write("Starting discovery of official examination sources...")
        results = ExamSourceResearchService.discover_official_sources()
        for res in results:
            self.stdout.write(f"- {res.get('url')}: {res.get('status')} ({res.get('title', '')})")
        self.stdout.write(self.style.SUCCESS("Discovery complete."))
