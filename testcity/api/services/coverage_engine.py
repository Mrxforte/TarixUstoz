from django.utils import timezone
from api.models import (
    Topic,
    TopicCoverageRecord,
    TopicQuestionBlueprint,
    Question,
    HistoricalChunk,
    CurriculumLearningObjective,
)

class TopicCoverageEngine:
    @staticmethod
    def evaluate_topic_coverage(topic_id):
        try:
            topic = Topic.objects.get(id=topic_id)
        except Topic.DoesNotExist:
            return None

        record, _ = TopicCoverageRecord.objects.get_or_create(topic=topic)

        # 1. Source pages & evidence chunks
        total_pages = max((topic.end_page - topic.start_page + 1), 1)
        chunks = HistoricalChunk.objects.filter(topic=topic)
        usable_chunks_count = chunks.count()
        objectives = CurriculumLearningObjective.objects.filter(topic=topic)
        total_objectives = objectives.count()

        # Check extraction completeness
        if topic.extraction_confidence < 0.8:
            record.coverage_status = 'extraction_incomplete'
            record.save()
            topic.coverage_status = 'extraction_incomplete'
            topic.last_coverage_evaluation = timezone.now()
            topic.save()
            return record

        if usable_chunks_count == 0:
            record.coverage_status = 'no_eligible_evidence'
            record.save()
            topic.coverage_status = 'no_eligible_evidence'
            topic.last_coverage_evaluation = timezone.now()
            topic.save()
            return record

        # 2. Verified Questions
        verified_questions = Question.objects.filter(topic=topic, verification_status='VERIFIED')
        verified_count = verified_questions.count()

        formats = list(verified_questions.values_list('question_type', flat=True).distinct())
        difficulties = list(verified_questions.values_list('difficulty', flat=True))

        diff_dist = {
            'EASY': difficulties.count('EASY'),
            'MEDIUM': difficulties.count('MEDIUM'),
            'HARD': difficulties.count('HARD'),
            'EXAM': difficulties.count('EXAM'),
        }

        # Determine target question policy
        if usable_chunks_count <= 3:
            target_qs = 5
        elif usable_chunks_count <= 8:
            target_qs = 10
        else:
            target_qs = 20

        record.total_source_pages = total_pages
        record.total_usable_chunks = usable_chunks_count
        record.total_extracted_objectives = total_objectives
        record.verified_question_count = verified_count
        record.question_formats_represented = formats
        record.difficulty_distribution = diff_dist

        # Determine coverage status
        if verified_count == 0:
            status = 'not_covered'
        elif verified_count < target_qs:
            status = 'partially_covered'
        else:
            # Check for variety (at least 2 formats and 2 difficulties)
            if len(formats) < 2 or (diff_dist['HARD'] + diff_dist['EXAM'] == 0):
                status = 'needs_more_variety'
            else:
                status = 'covered'

        record.coverage_status = status
        record.coverage_confidence = round(min(verified_count / target_qs, 1.0), 2)
        record.last_generation_date = timezone.now()
        record.save()

        topic.coverage_status = status
        topic.last_coverage_evaluation = timezone.now()
        topic.save()

        # Build TopicQuestionBlueprint for gaps
        if verified_count < target_qs:
            missing_count = target_qs - verified_count
            for obj in objectives:
                TopicQuestionBlueprint.objects.get_or_create(
                    topic=topic,
                    learning_objective=obj,
                    target_question_type='SINGLE_CHOICE',
                    target_difficulty='MEDIUM' if verified_count % 2 == 0 else 'HARD',
                    defaults={
                        'testable_facts': [f"Mavzu bo'yicha muhim fakt #{i+1}" for i in range(missing_count)],
                        'expected_coverage_count': missing_count,
                    }
                )

        return record

    @staticmethod
    def audit_all_topics():
        topics = Topic.objects.all()
        evaluated = 0
        for topic in topics:
            TopicCoverageEngine.evaluate_topic_coverage(topic.id)
            evaluated += 1
        return evaluated
