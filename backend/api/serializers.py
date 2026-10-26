from rest_framework import serializers
from api.models import (
    Category, PdfBook, DocumentPage, HistoricalChapter, Topic,
    HistoricalChunk, HistoricalEntity, HistoricalEntityAlias,
    EntityMention, HistoricalRelationship, ExamCategory, Question,
    QuizSession, QuizAnswerSubmission, UserLearningProgress,
    WeakTopic, SavedQuestion, ExaminationBlueprint
)

class PdfBookSerializer(serializers.ModelSerializer):
    grade_id = serializers.IntegerField(source='grade', read_only=True)
    subject_id = serializers.CharField(source='subject', read_only=True)
    pdf_url = serializers.SerializerMethodField()
    cover_url = serializers.SerializerMethodField()
    total_topics = serializers.SerializerMethodField()
    total_questions = serializers.SerializerMethodField()

    class Meta:
        model = PdfBook
        fields = [
            'id', 'category', 'title', 'grade', 'grade_id', 'subject', 'subject_id',
            'pdf_file', 'cover_image', 'pdf_url', 'cover_url', 'total_pages',
            'total_topics', 'total_questions',
            'is_processed', 'uploaded_at'
        ]

    def get_pdf_url(self, obj):
        if obj.pdf_file:
            request = self.context.get('request')
            if request:
                return request.build_absolute_uri(obj.pdf_file.url)
            return f"http://127.0.0.1:8000/media/{obj.pdf_file}"
        return None

    def get_cover_url(self, obj):
        if obj.cover_image:
            request = self.context.get('request')
            if request:
                return request.build_absolute_uri(obj.cover_image.url)
            return f"http://127.0.0.1:8000/media/{obj.cover_image}"
        return None

    def get_total_topics(self, obj):
        return obj.topics.count()

    def get_total_questions(self, obj):
        from api.models import Question
        return Question.objects.filter(grade=obj.grade, subject__icontains=obj.subject).count()

class CategorySerializer(serializers.ModelSerializer):
    pdf_books = PdfBookSerializer(many=True, read_only=True)
    total_questions = serializers.SerializerMethodField()

    class Meta:
        model = Category
        fields = ['id', 'name', 'description', 'order', 'pdf_books', 'total_questions']

    def get_total_questions(self, obj):
        from api.models import Question
        return Question.objects.filter(grade=obj.order).count()

class HistoricalChapterSerializer(serializers.ModelSerializer):
    class Meta:
        model = HistoricalChapter
        fields = ['id', 'number', 'title', 'start_page', 'end_page']

class TopicSerializer(serializers.ModelSerializer):
    chapter = HistoricalChapterSerializer(read_only=True)
    grade = serializers.IntegerField(source='document.grade', read_only=True, default=None)
    subject = serializers.CharField(source='document.subject', read_only=True, default=None)
    textbook_id = serializers.SerializerMethodField()

    class Meta:
        model = Topic
        fields = [
            'id', 'document', 'chapter', 'grade', 'subject', 'textbook_id',
            'title', 'canonical_title', 'content', 'order', 'start_page', 'end_page',
            'summary', 'review_status', 'coverage_status', 'extraction_confidence'
        ]

    def get_textbook_id(self, obj):
        if not obj.document:
            return "uz_6"
        g = obj.document.grade
        subj = (obj.document.subject or "").lower()
        if g == 6:
            return "uz_6"
        prefix = "jahon" if "jahon" in subj else "uz"
        return f"{prefix}_{g}"

class HistoricalChunkSerializer(serializers.ModelSerializer):
    class Meta:
        model = HistoricalChunk
        fields = ['id', 'chunk_id', 'document', 'page', 'topic', 'chunk_index', 'text', 'source_provenance']

class HistoricalEntityAliasSerializer(serializers.ModelSerializer):
    class Meta:
        model = HistoricalEntityAlias
        fields = ['id', 'alias_name', 'language']

class HistoricalEntitySerializer(serializers.ModelSerializer):
    aliases = HistoricalEntityAliasSerializer(many=True, read_only=True)

    class Meta:
        model = HistoricalEntity
        fields = ['id', 'canonical_name', 'entity_type', 'description', 'grade_relevance', 'subject_relevance', 'aliases']

class HistoricalRelationshipSerializer(serializers.ModelSerializer):
    source_entity = HistoricalEntitySerializer(read_only=True)
    target_entity = HistoricalEntitySerializer(read_only=True)

    class Meta:
        model = HistoricalRelationship
        fields = ['id', 'source_entity', 'target_entity', 'relationship_type', 'description']

class ExamCategorySerializer(serializers.ModelSerializer):
    class Meta:
        model = ExamCategory
        fields = ['id', 'code', 'title', 'description', 'question_count_default', 'time_limit_minutes', 'difficulty_distribution', 'is_official']

class QuestionSerializer(serializers.ModelSerializer):
    grade_id = serializers.IntegerField(source='grade', read_only=True)
    subject_id = serializers.SerializerMethodField()
    category_type = serializers.SerializerMethodField()

    class Meta:
        model = Question
        fields = [
            'id', 'topic', 'grade', 'grade_id', 'subject', 'subject_id', 'question_type', 'difficulty',
            'category_type', 'text', 'option_a', 'option_b', 'option_c', 'option_d', 'correct_option',
            'explanation', 'short_explanation', 'source_citation', 'quality_score', 'tags'
        ]

    def get_subject_id(self, obj):
        s = (obj.subject or "").lower()
        if "jahon" in s:
            return "jahon"
        return "uzbekiston"

    def get_category_type(self, obj):
        if obj.exam_category:
            return obj.exam_category.code
        tags = obj.tags or []
        if "bank" in tags or "jahon7" in tags:
            mapping = {
                "DATE_TO_EVENT": "YEAR", "EVENT_TO_DATE": "YEAR",
                "CHRONOLOGY": "YEAR", "NUMBER_TO_MEANING": "YEAR",
                "PERSON_TO_ACTIVITY": "PERSON", "ACTIVITY_TO_PERSON": "PERSON",
                "WHO_WAS_FIRST": "PERSON",
                "MAP_BASED": "CITY", "PLACE_TO_EVENT": "CITY",
            }
            for tag in tags:
                if tag in mapping:
                    return mapping[tag]
            return "EVENT"
        return "ALL"

class QuizAnswerSubmissionSerializer(serializers.ModelSerializer):
    class Meta:
        model = QuizAnswerSubmission
        fields = ['id', 'question', 'selected_option', 'is_correct', 'time_taken_seconds', 'submitted_at']

class QuizSessionSerializer(serializers.ModelSerializer):
    questions = QuestionSerializer(many=True, read_only=True)
    submissions = QuizAnswerSubmissionSerializer(many=True, read_only=True)

    class Meta:
        model = QuizSession
        fields = [
            'session_id', 'user_identifier', 'category_code', 'grade', 'subject',
            'total_questions', 'started_at', 'finished_at', 'score', 'is_completed',
            'questions', 'submissions'
        ]

class UserLearningProgressSerializer(serializers.ModelSerializer):
    topic = TopicSerializer(read_only=True)

    class Meta:
        model = UserLearningProgress
        fields = ['id', 'user_identifier', 'topic', 'total_attempted', 'total_correct', 'mastery_percentage', 'last_studied_at']

class WeakTopicSerializer(serializers.ModelSerializer):
    topic = TopicSerializer(read_only=True)

    class Meta:
        model = WeakTopic
        fields = ['id', 'user_identifier', 'topic', 'error_count', 'last_failed_at']

class SavedQuestionSerializer(serializers.ModelSerializer):
    question = QuestionSerializer(read_only=True)

    class Meta:
        model = SavedQuestion
        fields = ['id', 'user_identifier', 'question', 'saved_at', 'note']
