from django.core.management.base import BaseCommand
from api.services.curriculum_inventory import CurriculumInventoryBuilder
from api.services.coverage_engine import TopicCoverageEngine

class Command(BaseCommand):
    help = "Builds curriculum topic inventory from uploaded textbook PDFs"

    def handle(self, *args, **options):
        self.stdout.write("Building curriculum inventory from textbooks...")
        res = CurriculumInventoryBuilder.build_complete_inventory()
        TopicCoverageEngine.audit_all_topics()
        self.stdout.write(self.style.SUCCESS(
            f"Curriculum inventory built: {res['created_topics']} topics, {res['created_objectives']} objectives across {res['total_books']} books."
        ))
