from django.core.management.base import BaseCommand
from api.models import TopicCoverageRecord, Topic
from api.services.question_generator import QuestionGenerationPipeline
from api.services.coverage_engine import TopicCoverageEngine

class Command(BaseCommand):
    help = "Generates missing practice questions for uncovered or partially covered topics"

    def add_arguments(self, parser):
        parser.add_argument('--batch-size', type=str, default='10', help='Target questions per topic (e.g. 10 or 100k)')
        parser.add_argument('--limit', type=int, default=395, help='Maximum topics to process')

    def handle(self, *args, **options):
        raw_size = str(options['batch_size']).lower().replace('k', '000')
        batch_size = int(raw_size) if raw_size.isdigit() else 10
        limit = options['limit']
        self.stdout.write(f"Generating missing questions for topics (batch size: {batch_size}, limit: {limit})...")

        topics = Topic.objects.all()[:limit]

        total_generated = 0
        for topic in topics:
            created = QuestionGenerationPipeline.generate_questions_for_topic(topic.id, target_count=batch_size, exhaustive=True)
            try:
                TopicCoverageEngine.evaluate_topic_coverage(topic.id)
            except Exception:
                pass
            total_generated += created
            self.stdout.write(f"Topic '{topic.title[:50]}': +{created} questions")

        self.stdout.write(self.style.SUCCESS(f"Generated {total_generated} missing questions across topics."))
