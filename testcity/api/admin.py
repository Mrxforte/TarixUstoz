from django.contrib import admin
from api.models import (
    Category,
    PdfBook,
    DocumentPage,
    HistoricalChapter,
    Topic,
    CurriculumLearningObjective,
    HistoricalChunk,
    HistoricalEntity,
    ExamSourceDomain,
    SourceVerificationRecord,
    ExaminationAuthority,
    ExaminationProgram,
    ExaminationBlueprint,
    BlueprintVersion,
    ExamStylePattern,
    TopicCoverageRecord,
    TopicQuestionBlueprint,
    QuestionFamily,
    Question,
    QuestionEvidence,
    QuestionGenerationJob,
    QuizSession,
    UserLearningProgress,
    WeakTopic,
)

@admin.register(Category)
class CategoryAdmin(admin.ModelAdmin):
    list_display = ('name', 'order')
    search_fields = ('name',)


@admin.register(PdfBook)
class PdfBookAdmin(admin.ModelAdmin):
    list_display = ('title', 'grade', 'subject', 'total_pages', 'is_processed', 'uploaded_at')
    list_filter = ('grade', 'subject')
    search_fields = ('title',)


@admin.register(Topic)
class TopicAdmin(admin.ModelAdmin):
    list_display = ('title', 'document', 'chapter', 'coverage_status', 'extraction_confidence', 'review_status')
    list_filter = ('coverage_status', 'review_status', 'document__grade')
    search_fields = ('title', 'canonical_title')


@admin.register(CurriculumLearningObjective)
class CurriculumLearningObjectiveAdmin(admin.ModelAdmin):
    list_display = ('topic', 'code', 'cognitive_level', 'is_assessed')
    list_filter = ('cognitive_level', 'is_assessed')
    search_fields = ('description', 'code')


@admin.register(SourceVerificationRecord)
class SourceVerificationRecordAdmin(admin.ModelAdmin):
    list_display = ('title', 'responsible_authority', 'exam_period', 'status', 'is_broken', 'retrieval_date')
    list_filter = ('status', 'responsible_authority', 'is_broken')
    search_fields = ('title', 'url')


@admin.register(ExaminationAuthority)
class ExaminationAuthorityAdmin(admin.ModelAdmin):
    list_display = ('name', 'code', 'official_website')


@admin.register(ExaminationProgram)
class ExaminationProgramAdmin(admin.ModelAdmin):
    list_display = ('title', 'code', 'authority')


@admin.register(ExaminationBlueprint)
class ExaminationBlueprintAdmin(admin.ModelAdmin):
    list_display = ('title', 'program', 'subject', 'applicable_year', 'is_active')
    list_filter = ('is_active', 'applicable_year', 'subject')


@admin.register(BlueprintVersion)
class BlueprintVersionAdmin(admin.ModelAdmin):
    list_display = ('blueprint', 'version_number', 'status', 'total_question_count', 'duration_minutes')
    list_filter = ('status',)


@admin.register(ExamStylePattern)
class ExamStylePatternAdmin(admin.ModelAdmin):
    list_display = ('title', 'program', 'question_format', 'reasoning_complexity', 'uses_sources_or_maps')
    list_filter = ('question_format', 'reasoning_complexity', 'uses_sources_or_maps')


@admin.register(TopicCoverageRecord)
class TopicCoverageRecordAdmin(admin.ModelAdmin):
    list_display = ('topic', 'total_usable_chunks', 'verified_question_count', 'coverage_confidence', 'coverage_status')
    list_filter = ('coverage_status',)


@admin.register(TopicQuestionBlueprint)
class TopicQuestionBlueprintAdmin(admin.ModelAdmin):
    list_display = ('topic', 'learning_objective', 'target_question_type', 'target_difficulty')


@admin.register(Question)
class QuestionAdmin(admin.ModelAdmin):
    list_display = ('text', 'topic', 'grade', 'subject', 'difficulty', 'correct_option', 'verification_status', 'is_generated_practice')
    list_filter = ('verification_status', 'difficulty', 'grade', 'is_generated_practice', 'question_type')
    search_fields = ('text', 'explanation')


@admin.register(QuestionGenerationJob)
class QuestionGenerationJobAdmin(admin.ModelAdmin):
    list_display = ('job_id', 'topic', 'target_count', 'generated_count', 'status', 'progress_percentage')
    list_filter = ('status',)


@admin.register(QuizSession)
class QuizSessionAdmin(admin.ModelAdmin):
    list_display = ('session_id', 'user_identifier', 'category_code', 'total_questions', 'score', 'is_completed', 'started_at')
    list_filter = ('is_completed', 'category_code')
