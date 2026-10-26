from django.db import models
import uuid

# =====================================================================
# 1. BASE TEXTBOOK & CURRICULUM INVENTORY MODELS
# =====================================================================

class Category(models.Model):
    name = models.CharField(max_length=200, verbose_name="Bo'lim nomi (Masalan: 6-sinf Tarix)")
    description = models.TextField(blank=True, null=True, verbose_name="Ta'rifi")
    order = models.IntegerField(default=0, verbose_name="Tartib raqami")

    class Meta:
        verbose_name = "Bo'lim"
        verbose_name_plural = "1. Bo'limlar"
        ordering = ['order']

    def __str__(self):
        return self.name


class PdfBook(models.Model):
    category = models.ForeignKey(Category, on_delete=models.SET_NULL, null=True, blank=True, related_name='pdf_books', verbose_name="Bo'lim (Ixtiyotiy)")
    title = models.CharField(max_length=255, verbose_name="Kitob/Darslik nomi")
    grade = models.IntegerField(default=6, verbose_name="Sinf (6-11)")
    subject = models.CharField(max_length=100, default="O'zbekiston tarixi", verbose_name="Fan (O'zbekiston tarixi / Jahon tarixi)")
    pdf_file = models.FileField(upload_to='pdfs/', verbose_name="PDF Fayl")
    cover_image = models.ImageField(upload_to='pdf_covers/', null=True, blank=True, verbose_name="Muqova rasmi (Ixtiyoriy)")
    original_filename = models.CharField(max_length=255, blank=True, default="", verbose_name="Asl fayl nomi")
    file_hash = models.CharField(max_length=64, blank=True, null=True, verbose_name="SHA256 Hash")
    file_size = models.BigIntegerField(default=0, verbose_name="Fayl hajmi (bayt)")
    total_pages = models.IntegerField(default=0, verbose_name="Jami betlar")

    # Processing state
    is_processed = models.BooleanField(default=False, verbose_name="Qayta ishlanganmi")
    processing_status = models.CharField(
        max_length=30,
        choices=(
            ('PENDING', 'Kutilmoqda'),
            ('PROCESSING', 'Jarayonda'),
            ('COMPLETED', 'Muvaffaqiyatli'),
            ('PARTIAL', 'Qisman bajarilgan'),
            ('FAILED', 'Xatolik'),
        ),
        default='PENDING',
        verbose_name="Qayta ishlash holati"
    )
    processing_started_at = models.DateTimeField(null=True, blank=True)
    processing_completed_at = models.DateTimeField(null=True, blank=True)
    error_log = models.TextField(blank=True, default="")

    # Export state & tracking
    txt_export_path = models.CharField(max_length=500, blank=True, default="", verbose_name="TXT eksport yo'li")
    txt_export_hash = models.CharField(max_length=64, blank=True, default="", verbose_name="TXT hash")
    txt_export_size = models.BigIntegerField(default=0, verbose_name="TXT hajmi")

    json_export_path = models.CharField(max_length=500, blank=True, default="", verbose_name="JSON eksport yo'li")
    json_export_hash = models.CharField(max_length=64, blank=True, default="", verbose_name="JSON hash")
    json_export_size = models.BigIntegerField(default=0, verbose_name="JSON hajmi")

    jsonl_export_path = models.CharField(max_length=500, blank=True, default="", verbose_name="JSONL eksport yo'li")
    jsonl_export_hash = models.CharField(max_length=64, blank=True, default="", verbose_name="JSONL hash")
    jsonl_export_size = models.BigIntegerField(default=0, verbose_name="JSONL hajmi")

    export_status = models.CharField(
        max_length=30,
        choices=(
            ('PENDING', 'Kutilmoqda'),
            ('COMPLETED', 'Muvaffaqiyatli'),
            ('PARTIAL', 'Qisman'),
            ('FAILED', 'Xatolik'),
        ),
        default='PENDING',
        verbose_name="Eksport holati"
    )
    last_exported_at = models.DateTimeField(null=True, blank=True)

    uploaded_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "PDF Darslik"
        verbose_name_plural = "3. PDF Darsliklar"

    def __str__(self):
        return f"{self.title} ({self.grade}-sinf)"


class DocumentPage(models.Model):
    EXTRACTION_METHODS = (
        ('NATIVE', 'Native PyMuPDF Text'),
        ('OCR', 'OCR Fallback'),
        ('MIXED', 'Mixed Extraction'),
    )

    document = models.ForeignKey(PdfBook, on_delete=models.CASCADE, related_name='pages', verbose_name="Darslik")
    page_number = models.IntegerField(verbose_name="Bet raqami (1-based)")
    raw_text = models.TextField(blank=True, verbose_name="Xom matn")
    normalized_text = models.TextField(blank=True, verbose_name="Normalizatsiya qilingan matn")
    extraction_method = models.CharField(max_length=10, choices=EXTRACTION_METHODS, default='NATIVE')
    is_ocr_used = models.BooleanField(default=False)
    ocr_confidence = models.FloatField(default=0.0)
    char_count = models.IntegerField(default=0)
    word_count = models.IntegerField(default=0)
    has_error = models.BooleanField(default=False)
    error_message = models.TextField(blank=True, null=True)
    detected_headings = models.JSONField(default=list, blank=True)

    class Meta:
        verbose_name = "Darslik beti"
        verbose_name_plural = "Darslik betlari"
        unique_together = ('document', 'page_number')
        ordering = ['document', 'page_number']

    def __str__(self):
        return f"{self.document.title} - {self.page_number}-bet"


class HistoricalChapter(models.Model):
    document = models.ForeignKey(PdfBook, on_delete=models.CASCADE, related_name='chapters', verbose_name="Darslik")
    number = models.IntegerField(default=1, verbose_name="Bob raqami")
    title = models.CharField(max_length=255, verbose_name="Bob nomi")
    start_page = models.IntegerField(default=1)
    end_page = models.IntegerField(default=1)

    class Meta:
        ordering = ['document', 'number']

    def __str__(self):
        return f"{self.document.title} - {self.number}-bob: {self.title}"


class Topic(models.Model):
    REVIEW_STATUSES = (
        ('UNREVIEWED', 'Ko\'rib chiqilmagan'),
        ('REVIEWED', 'Tasdiqlangan'),
        ('NEEDS_CORRECTION', 'Tuzatish kerak'),
        ('REJECTED', 'Rad etilgan'),
    )
    COVERAGE_STATUSES = (
        ('not_processed', 'Qayta ishlanmagan'),
        ('extraction_incomplete', 'Chala ajratilgan'),
        ('awaiting_topic_review', 'Ko\'rib chiqish kutilmoqda'),
        ('no_eligible_evidence', 'Manba yetarsiz'),
        ('not_covered', 'Qamrab olinmagan'),
        ('partially_covered', 'Qisman qamrab olingan'),
        ('covered', 'To\'liq qamrab olingan'),
        ('needs_more_variety', 'Xilma-xillik yetishmaydi'),
        ('needs_revalidation', 'Qayta tekshirish kerak'),
    )

    category = models.ForeignKey(Category, on_delete=models.CASCADE, related_name='topics', verbose_name="Bo'lim", null=True, blank=True)
    document = models.ForeignKey(PdfBook, on_delete=models.CASCADE, related_name='topics', null=True, blank=True, verbose_name="Darslik")
    chapter = models.ForeignKey(HistoricalChapter, on_delete=models.SET_NULL, null=True, blank=True, related_name='topics')
    parent_topic = models.ForeignKey('self', on_delete=models.SET_NULL, null=True, blank=True, related_name='subtopics')

    title = models.CharField(max_length=255, verbose_name="Mavzu nomi")
    canonical_title = models.CharField(max_length=255, blank=True, verbose_name="Kanonik nomi")
    content = models.TextField(verbose_name="Mavzu matni", blank=True, default="")
    order = models.IntegerField(default=0, verbose_name="Tartib raqami")
    start_page = models.IntegerField(default=1)
    end_page = models.IntegerField(default=1)
    summary = models.TextField(blank=True, null=True)

    content_fingerprint = models.CharField(max_length=64, blank=True, null=True)
    extraction_confidence = models.FloatField(default=1.0)
    review_status = models.CharField(max_length=20, choices=REVIEW_STATUSES, default='UNREVIEWED')
    coverage_status = models.CharField(max_length=30, choices=COVERAGE_STATUSES, default='not_processed')
    last_coverage_evaluation = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = "Mavzu"
        verbose_name_plural = "2. Mavzular"
        ordering = ['category', 'order']

    def __str__(self):
        return f"{self.title}"


class CurriculumLearningObjective(models.Model):
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='learning_objectives')
    code = models.CharField(max_length=50, blank=True)
    description = models.TextField(verbose_name="O'quv maqsadi mazmuni")
    cognitive_level = models.CharField(max_length=30, default='RECALL') # RECALL, UNDERSTANDING, APPLICATION, ANALYSIS
    key_entities = models.JSONField(default=list, blank=True)
    key_dates = models.JSONField(default=list, blank=True)
    is_assessed = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"[{self.topic.title}] {self.description[:50]}"


class HistoricalChunk(models.Model):
    chunk_id = models.CharField(max_length=100, blank=True, default="")
    document = models.ForeignKey(PdfBook, on_delete=models.CASCADE, related_name='chunks')
    page = models.ForeignKey(DocumentPage, on_delete=models.CASCADE, related_name='chunks', null=True, blank=True)
    topic = models.ForeignKey(Topic, on_delete=models.SET_NULL, related_name='chunks', null=True, blank=True)
    chunk_index = models.IntegerField(default=0)
    text = models.TextField()
    normalized_text = models.TextField(blank=True)
    token_count = models.IntegerField(default=0)
    embedding = models.JSONField(null=True, blank=True, verbose_name="Vector Embedding (List of floats)")
    source_provenance = models.CharField(max_length=255, blank=True, verbose_name="Manba ko'rsatkich")

    class Meta:
        ordering = ['document', 'chunk_index']

    def __str__(self):
        return f"Chunk #{self.chunk_index} ({self.source_provenance})"


class HistoricalEntity(models.Model):
    ENTITY_TYPES = (
        ('PERSON', 'Tarixiy shaxs'),
        ('PLACE', 'Tarixiy joy / Shahar'),
        ('STATE', 'Davlat'),
        ('EMPIRE', 'Imperiya'),
        ('DYNASTY', 'Sulola'),
        ('WAR', 'Urush / Jang'),
        ('EVENT', 'Tarixiy voqea'),
        ('DATE', 'Sana / Davr'),
        ('CONCEPT', 'Tushuncha / Atama'),
        ('CULTURE', 'Madaniyat / Sivilizatsiya'),
        ('DOCUMENT', 'Hujjat / Sulh / Manba'),
        ('ARCHAEOLOGICAL_SITE', 'Arxeologik yodgorlik'),
    )

    canonical_name = models.CharField(max_length=255, unique=True, verbose_name="Asosiy nomi")
    entity_type = models.CharField(max_length=30, choices=ENTITY_TYPES, default='PERSON')
    description = models.TextField(blank=True, verbose_name="Tavsifi")
    grade_relevance = models.JSONField(default=list, blank=True, verbose_name="Sinflar (6, 7, 8, etc.)")
    subject_relevance = models.JSONField(default=list, blank=True, verbose_name="Fanlar")
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "Tarixiy Subyekt"
        verbose_name_plural = "Tarixiy Subyektlar"

    def __str__(self):
        return f"{self.canonical_name} ({self.get_entity_type_display()})"


class HistoricalEntityAlias(models.Model):
    entity = models.ForeignKey(HistoricalEntity, on_delete=models.CASCADE, related_name='aliases')
    alias_name = models.CharField(max_length=255, verbose_name="Muqobil nomi")
    language = models.CharField(max_length=20, default='UZ_LATIN')

    class Meta:
        unique_together = ('entity', 'alias_name')


class EntityMention(models.Model):
    entity = models.ForeignKey(HistoricalEntity, on_delete=models.CASCADE, related_name='mentions')
    chunk = models.ForeignKey(HistoricalChunk, on_delete=models.CASCADE, related_name='entity_mentions')
    mention_text = models.CharField(max_length=255)
    confidence = models.FloatField(default=1.0)


class HistoricalRelationship(models.Model):
    REL_TYPES = (
        ('RULED', 'Boshqargan'),
        ('BORN_IN', 'Tug\'ilgan joyi'),
        ('FOUNDED', 'Asos solgan'),
        ('PARTICIPATED_IN', 'Ishtirok etgan'),
        ('COMMANDED', 'Boshchilik qilgan'),
        ('OCCURRED_IN', 'Yuz bergan joy/sana'),
        ('CAUSED', 'Sabab bo\'lgan'),
        ('RESULTED_IN', 'Oqibati bo\'lgan'),
        ('SUCCEEDED', 'O\'rnini egallagan'),
        ('CAPITAL_OF', 'Poytaxti bo\'lgan'),
        ('RELATED_TO', 'Boshqa aloqadorlik'),
    )

    source_entity = models.ForeignKey(HistoricalEntity, on_delete=models.CASCADE, related_name='source_relationships')
    target_entity = models.ForeignKey(HistoricalEntity, on_delete=models.CASCADE, related_name='target_relationships')
    relationship_type = models.CharField(max_length=30, choices=REL_TYPES, default='RELATED_TO')
    description = models.TextField(blank=True)
    evidence_chunk = models.ForeignKey(HistoricalChunk, on_delete=models.SET_NULL, null=True, blank=True)


# =====================================================================
# 2. OFFICIAL EXAM RESEARCH & BLUEPRINT MODELS
# =====================================================================

class ExamSourceDomain(models.Model):
    domain = models.CharField(max_length=255, unique=True)
    title = models.CharField(max_length=255, blank=True)
    is_official_authority = models.BooleanField(default=True)
    is_approved = models.BooleanField(default=True)
    rate_limit_per_minute = models.IntegerField(default=30)

    def __str__(self):
        return f"{self.domain} (Approved: {self.is_approved})"


class SourceVerificationRecord(models.Model):
    VERIFICATION_STATUSES = (
        ('discovered', 'Aniqlangan'),
        ('downloaded', 'Yuklab olingan'),
        ('parsed', 'Tahlil qilingan'),
        ('needs_review', 'Ko\'rib chiqish kerak'),
        ('verified_official', 'Rasmiy tasdiqlangan'),
        ('superseded', 'Eski nashr (O\'zgartirilgan)'),
        ('unavailable', 'Mavjud emas / Qulflangan'),
    )

    url = models.URLField(max_length=1000, unique=True)
    title = models.CharField(max_length=500, blank=True)
    responsible_authority = models.CharField(max_length=255, default="BMBA / DTM")
    exam_period = models.CharField(max_length=100, blank=True) # e.g. "2024-2025"
    retrieval_date = models.DateTimeField(auto_now_add=True)
    publication_date = models.DateField(null=True, blank=True)
    effective_period = models.CharField(max_length=100, blank=True)
    document_version = models.CharField(max_length=50, default="1.0")

    extracted_text = models.TextField(blank=True)
    content_hash = models.CharField(max_length=64, blank=True)
    status = models.CharField(max_length=25, choices=VERIFICATION_STATUSES, default='discovered')
    superseded_by = models.ForeignKey('self', on_delete=models.SET_NULL, null=True, blank=True, related_name='supersedes')
    is_broken = models.BooleanField(default=False)
    notes = models.TextField(blank=True)

    def __str__(self):
        return f"[{self.status}] {self.title or self.url[:60]}"


class ExaminationAuthority(models.Model):
    name = models.CharField(max_length=255, unique=True) # e.g. "O'zbekiston Respublikasi Bilim va malakalarni baholash agentligi (BMBA)"
    code = models.CharField(max_length=50, unique=True) # e.g. "UZBMB", "DTM", "UZEDU"
    official_website = models.URLField(blank=True)

    def __str__(self):
        return self.name


class ExaminationProgram(models.Model):
    authority = models.ForeignKey(ExaminationAuthority, on_delete=models.CASCADE, related_name='programs')
    title = models.CharField(max_length=255) # e.g. "Milliy sertifikat", "O'qituvchilar attestatsiyasi", "OOTMga kirish testlari"
    code = models.CharField(max_length=50, unique=True) # e.g. "MILLIYTEST", "ATTESTATION", "DTM_ENTRANCE"
    description = models.TextField(blank=True)

    def __str__(self):
        return f"{self.authority.code} - {self.title}"


class ExaminationBlueprint(models.Model):
    program = models.ForeignKey(ExaminationProgram, on_delete=models.CASCADE, related_name='blueprints')
    title = models.CharField(max_length=255)
    subject = models.CharField(max_length=100, default="Tarix")
    language = models.CharField(max_length=20, default="UZ_LATIN")
    applicable_year = models.IntegerField(default=2025)
    is_active = models.BooleanField(default=True)

    def __str__(self):
        return f"{self.program.title} ({self.applicable_year}) - {self.title}"


class BlueprintVersion(models.Model):
    blueprint = models.ForeignKey(ExaminationBlueprint, on_delete=models.CASCADE, related_name='versions')
    version_number = models.CharField(max_length=20, default="1.0")
    status = models.CharField(max_length=25, choices=SourceVerificationRecord.VERIFICATION_STATUSES, default='discovered')

    total_question_count = models.IntegerField(default=30)
    duration_minutes = models.IntegerField(default=60)
    allows_negative_marking = models.BooleanField(default=False)
    negative_marking_ratio = models.FloatField(default=0.0)

    question_formats = models.JSONField(default=list, blank=True) # e.g. ["SINGLE_CHOICE", "MATCHING"]
    topic_distribution = models.JSONField(default=dict, blank=True)
    competency_distribution = models.JSONField(default=dict, blank=True)
    difficulty_distribution = models.JSONField(default=dict, blank=True) # {"EASY": 20, "MEDIUM": 50, "HARD": 30}
    scoring_rules = models.JSONField(default=dict, blank=True)

    publication_date = models.DateField(null=True, blank=True)
    verification_notes = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.blueprint.title} v{self.version_number} [{self.status}]"


class BlueprintSource(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='source_links')
    source_record = models.ForeignKey(SourceVerificationRecord, on_delete=models.CASCADE, related_name='blueprint_links')
    is_primary = models.BooleanField(default=True)


class ExamContentDomain(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='domains')
    title = models.CharField(max_length=255)
    weight_percentage = models.FloatField(default=0.0)
    expected_question_count = models.IntegerField(default=0)


class ExamCompetency(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='competencies')
    title = models.CharField(max_length=255)
    code = models.CharField(max_length=50, blank=True)
    weight_percentage = models.FloatField(default=0.0)


class ExamQuestionFormat(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='formats')
    format_type = models.CharField(max_length=50) # e.g. SINGLE_CHOICE, MATCHING, CHRONOLOGICAL
    count = models.IntegerField(default=0)


class ExamDifficultyDistribution(models.Model):
    blueprint_version = models.OneToOneField(BlueprintVersion, on_delete=models.CASCADE, related_name='difficulty_spec')
    easy_percent = models.FloatField(default=30.0)
    medium_percent = models.FloatField(default=50.0)
    hard_percent = models.FloatField(default=20.0)


class ExamScoringRule(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='scoring_rules_list')
    rule_name = models.CharField(max_length=100)
    points_per_question = models.FloatField(default=1.1)


class ExamTimeRule(models.Model):
    blueprint_version = models.OneToOneField(BlueprintVersion, on_delete=models.CASCADE, related_name='time_rule')
    total_minutes = models.IntegerField(default=60)
    warning_minutes = models.IntegerField(default=5)


class ExamSyllabusTopic(models.Model):
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.CASCADE, related_name='syllabus_topics')
    code = models.CharField(max_length=50, blank=True)
    title = models.CharField(max_length=255)
    grade = models.IntegerField(default=6)
    subject = models.CharField(max_length=100, default="O'zbekiston tarixi")


class BlueprintTopicMapping(models.Model):
    syllabus_topic = models.ForeignKey(ExamSyllabusTopic, on_delete=models.CASCADE, related_name='topic_mappings')
    curriculum_topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='blueprint_mappings')
    confidence = models.FloatField(default=1.0)


# =====================================================================
# 3. SAMPLE TEST PATTERN MODEL
# =====================================================================

class ExamStylePattern(models.Model):
    title = models.CharField(max_length=255)
    program = models.ForeignKey(ExaminationProgram, on_delete=models.CASCADE, related_name='style_patterns', null=True, blank=True)
    question_format = models.CharField(max_length=50, default='SINGLE_CHOICE')
    option_count = models.IntegerField(default=4)

    command_verbs = models.JSONField(default=list, blank=True) # e.g. ["aniqlang", "moslang", "toping"]
    reasoning_complexity = models.CharField(max_length=50, default='ANALYSIS')
    knowledge_requirements = models.JSONField(default=list, blank=True)
    distractor_patterns = models.JSONField(default=list, blank=True)
    uses_sources_or_maps = models.BooleanField(default=False)
    sample_reference = models.TextField(blank=True) # Reference to official sample without copying protected text

    def __str__(self):
        return self.title


# =====================================================================
# 4. TOPIC QUESTION BLUEPRINT & COVERAGE TRACKING
# =====================================================================

class TopicCoverageRecord(models.Model):
    topic = models.OneToOneField(Topic, on_delete=models.CASCADE, related_name='coverage_record')
    total_source_pages = models.IntegerField(default=0)
    total_usable_chunks = models.IntegerField(default=0)
    total_extracted_objectives = models.IntegerField(default=0)

    verified_question_count = models.IntegerField(default=0)
    distinct_facts_tested = models.IntegerField(default=0)
    question_formats_represented = models.JSONField(default=list, blank=True)
    difficulty_distribution = models.JSONField(default=dict, blank=True)

    coverage_confidence = models.FloatField(default=0.0)
    coverage_status = models.CharField(max_length=30, choices=Topic.COVERAGE_STATUSES, default='not_processed')

    missing_question_formats = models.JSONField(default=list, blank=True)
    missing_subtopics = models.JSONField(default=list, blank=True)
    uncovered_entities = models.JSONField(default=list, blank=True)
    uncovered_dates = models.JSONField(default=list, blank=True)
    duplicate_question_ratio = models.FloatField(default=0.0)

    last_generation_date = models.DateTimeField(null=True, blank=True)
    last_review_date = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"Coverage for {self.topic.title}: {self.verified_question_count} Qs [{self.coverage_status}]"


class TopicQuestionBlueprint(models.Model):
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='question_blueprints')
    learning_objective = models.ForeignKey(CurriculumLearningObjective, on_delete=models.SET_NULL, null=True, blank=True)

    testable_facts = models.JSONField(default=list, blank=True)
    testable_dates = models.JSONField(default=list, blank=True)
    testable_entities = models.JSONField(default=list, blank=True)
    cause_effect_relations = models.JSONField(default=list, blank=True)
    misconceptions = models.JSONField(default=list, blank=True)

    target_question_type = models.CharField(max_length=30, default='SINGLE_CHOICE')
    target_difficulty = models.CharField(max_length=15, default='MEDIUM')
    expected_coverage_count = models.IntegerField(default=5)

    def __str__(self):
        return f"Blueprint: {self.topic.title} - {self.target_question_type} ({self.target_difficulty})"


# =====================================================================
# 5. QUESTION BANK & DIVERSITY / PROVENANCE
# =====================================================================

class QuestionFamily(models.Model):
    title = models.CharField(max_length=255)
    core_fact = models.TextField()
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='question_families')
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return self.title


class ExamCategory(models.Model):
    code = models.CharField(max_length=50, unique=True, verbose_name="Kategoriya kodi")
    title = models.CharField(max_length=255, verbose_name="Kategoriya nomi")
    description = models.TextField(blank=True, verbose_name="Tavsifi")
    question_count_default = models.IntegerField(default=30)
    time_limit_minutes = models.IntegerField(default=60)
    difficulty_distribution = models.JSONField(default=dict, blank=True)
    is_official = models.BooleanField(default=True)

    class Meta:
        verbose_name = "Imtihon Kategoriyasi"
        verbose_name_plural = "Imtihon Kategoriyalari"

    def __str__(self):
        return self.title


class Question(models.Model):
    CORRECT_OPTIONS = (
        ('A', 'A'),
        ('B', 'B'),
        ('C', 'C'),
        ('D', 'D'),
    )
    QUESTION_TYPES = (
        ('SINGLE_CHOICE', 'Bitta to\'g\'ri javobli MCQ'),
        ('TRUE_FALSE', 'Rost/Yolg\'on'),
        ('CHRONOLOGICAL', 'Xronologik tartiblash'),
        ('MATCHING', 'Moslikni topish'),
        ('SHORT_ANSWER', 'Qisqa javob'),
    )
    DIFFICULTIES = (
        ('EASY', 'Oson'),
        ('MEDIUM', 'O\'rta (Mantiqiy chalg\'ituvchilar bilan)'),
        ('HARD', 'Qiyin (Chalg\'ituvchi sanalar va shaxslar bilan)'),
        ('EXAM', 'DTM / Attestatsiya darajasi'),
    )
    VERIFICATION_STATUS = (
        ('DRAFT', 'Loyiha'),
        ('CANDIDATE', 'Kandidat'),
        ('VERIFIED', 'Tasdiqlangan'),
        ('REJECTED', 'Radd etilgan'),
    )

    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='questions', verbose_name="Mavzu", null=True, blank=True)
    chunk = models.ForeignKey(HistoricalChunk, on_delete=models.SET_NULL, null=True, blank=True, related_name='questions')
    question_family = models.ForeignKey(QuestionFamily, on_delete=models.SET_NULL, null=True, blank=True, related_name='variants')
    topic_blueprint = models.ForeignKey(TopicQuestionBlueprint, on_delete=models.SET_NULL, null=True, blank=True, related_name='generated_questions')
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.SET_NULL, null=True, blank=True, related_name='questions')

    grade = models.IntegerField(default=6)
    subject = models.CharField(max_length=100, default="O'zbekiston tarixi")
    exam_category = models.ForeignKey(ExamCategory, on_delete=models.SET_NULL, null=True, blank=True, related_name='questions')

    question_type = models.CharField(max_length=20, choices=QUESTION_TYPES, default='SINGLE_CHOICE')
    difficulty = models.CharField(max_length=10, choices=DIFFICULTIES, default='MEDIUM')

    text = models.TextField(verbose_name="Savol matni")
    option_a = models.CharField(max_length=255, verbose_name="A variant")
    option_b = models.CharField(max_length=255, verbose_name="B variant")
    option_c = models.CharField(max_length=255, verbose_name="C variant")
    option_d = models.CharField(max_length=255, verbose_name="D variant")
    correct_option = models.CharField(max_length=1, choices=CORRECT_OPTIONS, verbose_name="To'g'ri javob")

    explanation = models.TextField(blank=True, verbose_name="Batafsil tushuntirish va manba")
    short_explanation = models.CharField(max_length=255, blank=True, verbose_name="Qisqa izoh")
    distractor_analysis = models.JSONField(default=dict, blank=True)

    source_citation = models.CharField(max_length=255, blank=True, verbose_name="Manba (darslik va bet)")
    evidence_chunk_ids = models.JSONField(default=list, blank=True)

    is_official_sample = models.BooleanField(default=False)
    is_generated_practice = models.BooleanField(default=True)

    generation_model = models.CharField(max_length=100, default="Rule-RAG-Engine")
    generation_config = models.JSONField(default=dict, blank=True)

    verification_status = models.CharField(max_length=15, choices=VERIFICATION_STATUS, default='VERIFIED')
    quality_score = models.FloatField(default=1.0)
    tags = models.JSONField(default=list, blank=True)
    content_hash = models.CharField(max_length=64, blank=True)
    created_at = models.DateTimeField(auto_now_add=True, null=True, blank=True)

    class Meta:
        verbose_name = "Test savoli"
        verbose_name_plural = "4. Test savollari"

    def __str__(self):
        return f"[{self.difficulty}] {self.text[:50]}"


class QuestionEvidence(models.Model):
    question = models.ForeignKey(Question, on_delete=models.CASCADE, related_name='evidence_links')
    chunk = models.ForeignKey(HistoricalChunk, on_delete=models.CASCADE, related_name='question_links')
    relevance_score = models.FloatField(default=1.0)


# =====================================================================
# 6. GENERATION JOB QUEUE
# =====================================================================

class QuestionGenerationJob(models.Model):
    STATUSES = (
        ('PENDING', 'Kutilmoqda'),
        ('RUNNING', 'Bajarilmoqda'),
        ('COMPLETED', 'Tugallandi'),
        ('FAILED', 'Xatolik yuz berdi'),
        ('PAUSED', 'Vaqtincha to\'xtatildi'),
    )

    job_id = models.CharField(max_length=100, unique=True, default=uuid.uuid4)
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, related_name='generation_jobs', null=True, blank=True)
    grade = models.IntegerField(null=True, blank=True)
    subject = models.CharField(max_length=100, blank=True)

    target_count = models.IntegerField(default=10)
    generated_count = models.IntegerField(default=0)
    batch_size = models.IntegerField(default=5)

    status = models.CharField(max_length=20, choices=STATUSES, default='PENDING')
    progress_percentage = models.FloatField(default=0.0)
    idempotency_key = models.CharField(max_length=100, unique=True)
    error_log = models.TextField(blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"Job {self.job_id[:8]} [{self.status}] ({self.generated_count}/{self.target_count})"


# =====================================================================
# 7. USER QUIZ & PROGRESS MODELS
# =====================================================================

class QuizSession(models.Model):
    session_id = models.CharField(max_length=100, unique=True, default=uuid.uuid4)
    user_identifier = models.CharField(max_length=100, default="guest_user")
    category_code = models.CharField(max_length=50, default="PRACTICE")
    blueprint_version = models.ForeignKey(BlueprintVersion, on_delete=models.SET_NULL, null=True, blank=True)
    grade = models.IntegerField(default=0)
    subject = models.CharField(max_length=100, blank=True)
    total_questions = models.IntegerField(default=10)
    questions = models.ManyToManyField(Question, related_name='quiz_sessions')
    started_at = models.DateTimeField(auto_now_add=True)
    finished_at = models.DateTimeField(null=True, blank=True)
    score = models.FloatField(default=0.0)
    is_completed = models.BooleanField(default=False)

    def __str__(self):
        return f"QuizSession {self.session_id} - Score: {self.score}"


class QuizAnswerSubmission(models.Model):
    quiz_session = models.ForeignKey(QuizSession, on_delete=models.CASCADE, related_name='submissions')
    question = models.ForeignKey(Question, on_delete=models.CASCADE)
    selected_option = models.CharField(max_length=1)
    is_correct = models.BooleanField(default=False)
    time_taken_seconds = models.IntegerField(default=0)
    submitted_at = models.DateTimeField(auto_now_add=True)


class UserLearningProgress(models.Model):
    user_identifier = models.CharField(max_length=100)
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE, null=True, blank=True)
    total_attempted = models.IntegerField(default=0)
    total_correct = models.IntegerField(default=0)
    mastery_percentage = models.FloatField(default=0.0)
    last_studied_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('user_identifier', 'topic')


class WeakTopic(models.Model):
    user_identifier = models.CharField(max_length=100)
    topic = models.ForeignKey(Topic, on_delete=models.CASCADE)
    error_count = models.IntegerField(default=1)
    last_failed_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('user_identifier', 'topic')


class SavedQuestion(models.Model):
    user_identifier = models.CharField(max_length=100)
    question = models.ForeignKey(Question, on_delete=models.CASCADE)
    saved_at = models.DateTimeField(auto_now_add=True)
    note = models.TextField(blank=True)

    class Meta:
        unique_together = ('user_identifier', 'question')


class IngestionAuditLog(models.Model):
    document = models.ForeignKey(PdfBook, on_delete=models.CASCADE)
    step = models.CharField(max_length=100)
    status = models.CharField(max_length=20)
    message = models.TextField()
    timestamp = models.DateTimeField(auto_now_add=True)
