from django.core.management.base import BaseCommand
from api.services.coverage_engine import TopicCoverageEngine

class Command(BaseCommand):
    help = "Audits and evaluates coverage for all detected textbook topics"

    def handle(self, *args, **options):
        self.stdout.write("Auditing textbook topic coverage...")
        evaluated = TopicCoverageEngine.audit_all_topics()
        self.stdout.write(self.style.SUCCESS(f"Audited coverage for {evaluated} topics."))
