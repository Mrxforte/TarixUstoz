import re
from api.models import (
    ExamStylePattern,
    SourceVerificationRecord,
    ExaminationProgram,
    ExaminationAuthority
)

COMMAND_VERB_PATTERNS = [
    (r'\baniqlang\b', 'ANIQLANG'),
    (r'\bmoslang\b', 'MOSLANG'),
    (r'\btoping\b', 'TOPING'),
    (r'\btartiblang\b', 'TARTIBLANG'),
    (r'\bko\'rsating\b', 'KORSATING'),
    (r'\bsolishtiring\b', 'SOLISHTIRING'),
    (r'\bseching\b', 'TANGALANG'),
]

REASONING_LEVEL_KEYWORDS = {
    'RECALL': ['qachon', 'kim', 'qayerda', 'nechanchi yilda', 'qaysi darslik'],
    'UNDERSTANDING': ['sabab', 'oqibat', 'mano', 'tavsif', 'mazmun'],
    'ANALYSIS': ['solishtiring', 'farqlang', 'xulosa', 'tahlil', 'moslik'],
    'SYNTHESIS': ['sistemalashtiring', 'xronologik tartib'],
}

class SampleTestAnalyzerService:
    @staticmethod
    def analyze_sample_source(source_record_id):
        try:
            source = SourceVerificationRecord.objects.get(id=source_record_id)
        except SourceVerificationRecord.DoesNotExist:
            return None

        text = source.extracted_text or ""
        if not text:
            return None

        detected_verbs = []
        for regex, tag in COMMAND_VERB_PATTERNS:
            if re.search(regex, text, re.IGNORECASE):
                detected_verbs.append(tag)

        complexity = 'ANALYSIS'
        for level, keywords in REASONING_LEVEL_KEYWORDS.items():
            if any(kw in text.lower() for kw in keywords):
                complexity = level
                break

        # Check for authority / program
        authority, _ = ExaminationAuthority.objects.get_or_create(
            code="UZBMB",
            defaults={
                'name': "O'zbekiston Respublikasi Bilim va malakalarni baholash agentligi (BMBA)",
                'official_website': "https://uzbmb.uz"
            }
        )

        program, _ = ExaminationProgram.objects.get_or_create(
            code="MILLIYTEST",
            defaults={
                'authority': authority,
                'title': "Tarix fanidan Milliy Sertifikat imtihoni",
                'description': "Milliy sertifikat bo'yicha namuna test materiallari va spetsifikatsiyasi"
            }
        )

        pattern, created = ExamStylePattern.objects.get_or_create(
            title=f"Sample Pattern from {source.title[:100]}",
            defaults={
                'program': program,
                'question_format': 'SINGLE_CHOICE',
                'option_count': 4,
                'command_verbs': list(set(detected_verbs)),
                'reasoning_complexity': complexity,
                'knowledge_requirements': ["Tarixiy xronologiya", "Tarixiy shaxslar va davlatlar", "Sabab-oqibat bog'liqligi"],
                'distractor_patterns': ["Yondosh sanalar va chalg'ituvchi shaxslar", "Hukmdorlar va sulolalarning chalkashishi"],
                'uses_sources_or_maps': "xarita" in text.lower() or "manba" in text.lower(),
                'sample_reference': f"Source URL: {source.url} (Analiz qilingan matn qirqimida aniqlangan uslubiy model)",
            }
        )

        return pattern
