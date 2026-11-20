from django.core.management.base import BaseCommand
from api.models import Topic, TopicCoverageRecord, Question

class Command(BaseCommand):
    help = "Displays a summary report of curriculum topic coverage"

    def handle(self, *args, **options):
        total_topics = Topic.objects.count()
        records = TopicCoverageRecord.objects.all()

        covered = records.filter(coverage_status='covered').count()
        partially = records.filter(coverage_status='partially_covered').count()
        uncovered = records.filter(coverage_status='not_covered').count()
        incomplete = records.filter(coverage_status='extraction_incomplete').count()
        total_questions = Question.objects.filter(verification_status='VERIFIED').count()

        coverage_pct = round((covered / max(total_topics, 1)) * 100, 1)

        self.stdout.write("===============================================")
        self.stdout.write("       TARIX USTOZ - CURRICULUM COVERAGE       ")
        self.stdout.write("===============================================")
        self.stdout.write(f"Total Detected Topics: {total_topics}")
        self.stdout.write(f"Covered Topics:         {covered} ({coverage_pct}%)")
        self.stdout.write(f"Partially Covered:      {partially}")
        self.stdout.write(f"Uncovered Topics:       {uncovered}")
        self.stdout.write(f"Extraction Incomplete:  {incomplete}")
        self.stdout.write(f"Verified Questions:     {total_questions}")
        self.stdout.write("===============================================")
