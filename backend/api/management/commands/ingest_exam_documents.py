from django.core.management.base import BaseCommand
from api.models import SourceVerificationRecord
from api.services.sample_analyzer import SampleTestAnalyzerService

class Command(BaseCommand):
    help = "Ingests and parses discovered exam documents into sample test style patterns"

    def handle(self, *args, **options):
        self.stdout.write("Ingesting exam documents...")
        records = SourceVerificationRecord.objects.filter(status__in=['discovered', 'parsed'])
        ingested = 0
        for record in records:
            pattern = SampleTestAnalyzerService.analyze_sample_source(record.id)
            if pattern:
                record.status = 'parsed'
                record.save()
                ingested += 1
                self.stdout.write(f"Parsed pattern from {record.title}")
        self.stdout.write(self.style.SUCCESS(f"Successfully ingested {ingested} exam documents."))
