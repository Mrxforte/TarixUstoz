"""Default catalogue: languages, fanlar and certificate blueprints."""
from __future__ import annotations

from domain.certificates.entities import (
    CefrLevel,
    CertificateBand,
    CertificateKind,
    CertificateProgram,
    CertificateSection,
)
from domain.subjects.entities import Language, Subject, SubjectCategory

SCHOOL_GRADES = (5, 6, 7, 8, 9, 10, 11)

SUBJECT_DEFINITIONS = [
    ("tarix", "Tarix", "History", "🏛️"),
    ("matematika", "Matematika", "Mathematics", "➗"),
    ("ona-tili", "Ona tili", "Native language", "📗"),
    ("adabiyot", "Adabiyot", "Literature", "📚"),
    ("ingliz-tili", "Ingliz tili", "English", "🇬🇧"),
    ("rus-tili", "Rus tili", "Russian", "🇷🇺"),
    ("fizika", "Fizika", "Physics", "🔭"),
    ("kimyo", "Kimyo", "Chemistry", "⚗️"),
    ("biologiya", "Biologiya", "Biology", "🧬"),
    ("geografiya", "Geografiya", "Geography", "🌍"),
    ("informatika", "Informatika", "Computer science", "💻"),
]

SUBJECT_TRANSLATIONS: dict[str, dict[str, str]] = {
    "tarix": {"ru": "История", "en": "History", "kz": "Тарих", "tj": "Таърих"},
    "matematika": {"ru": "Математика", "en": "Mathematics", "kz": "Математика", "tj": "Математика"},
    "ona-tili": {"ru": "Родной язык", "en": "Native language"},
    "adabiyot": {"ru": "Литература", "en": "Literature"},
    "ingliz-tili": {"ru": "Английский язык", "en": "English"},
    "rus-tili": {"ru": "Русский язык", "en": "Russian"},
    "fizika": {"ru": "Физика", "en": "Physics"},
    "kimyo": {"ru": "Химия", "en": "Chemistry"},
    "biologiya": {"ru": "Биология", "en": "Biology"},
    "geografiya": {"ru": "География", "en": "Geography"},
    "informatika": {"ru": "Информатика", "en": "Computer science"},
}

PROGRAM_TRANSLATIONS: dict[str, dict[str, str]] = {
    "milliy-sertifikat-tarix": {
        "ru": "Национальный сертификат — История",
        "en": "National certificate — History",
    },
    "attestatsiya-tarix": {"ru": "Аттестация — История", "en": "Attestation — History"},
    "milliy-sertifikat-matematika": {
        "ru": "Национальный сертификат — Математика",
        "en": "National certificate — Mathematics",
    },
    "cefr": {"ru": "Экзамен CEFR", "en": "CEFR exam"},
}


def default_languages() -> list[Language]:
    """Manageable content languages. New ones are added from the admin panel."""
    return [
        Language(code="uz", name="Uzbek", native_name="O'zbekcha", is_default=True, order=1),
        Language(code="ru", name="Russian", native_name="Русский", order=2),
        Language(code="en", name="English", native_name="English", order=3),
        Language(code="kz", name="Kazakh", native_name="Қазақша", order=4),
        Language(code="tj", name="Tajik", native_name="Тоҷикӣ", order=5),
    ]


def default_subjects() -> list[Subject]:
    """Every fan the platform ships with. Tarix stays first — it is the original subject."""
    return [
        Subject(
            code=code,
            name=name,
            name_en=name_en,
            category=SubjectCategory.SCHOOL,
            grades=SCHOOL_GRADES,
            icon=icon,
            order=index,
            translations=dict(SUBJECT_TRANSLATIONS.get(code, {})),
        )
        for index, (code, name, name_en, icon) in enumerate(SUBJECT_DEFINITIONS, start=1)
    ]


def _national_programs() -> list[CertificateProgram]:
    national_bands = (
        CertificateBand(label="A toifa", min_score=90, max_score=100, description="Yuqori natija", order=1),
        CertificateBand(label="B toifa", min_score=80, max_score=89.99, description="Yaxshi natija", order=2),
        CertificateBand(label="C toifa", min_score=60, max_score=79.99, description="Qoniqarli natija", order=3),
        CertificateBand(label="Sertifikatsiz", min_score=0, max_score=59.99,
                        description="Qayta topshirish", order=4),
    )
    return [
        CertificateProgram(
            code="milliy-sertifikat-tarix",
            name="Milliy sertifikat — Tarix",
            kind=CertificateKind.MILLIY_SERTIFIKAT,
            subject_code="tarix",
            max_score=100,
            passing_score=60,
            duration_minutes=120,
            sections=(
                CertificateSection(code="test", name="Test savollari", max_score=70, question_count=35,
                                   time_limit_minutes=70, order=1),
                CertificateSection(code="amaliy", name="Amaliy topshiriqlar", max_score=30, question_count=3,
                                   time_limit_minutes=50, order=2),
            ),
            bands=national_bands,
            translations=dict(PROGRAM_TRANSLATIONS["milliy-sertifikat-tarix"]),
        ),
        CertificateProgram(
            code="attestatsiya-tarix",
            name="Attestatsiya — Tarix",
            kind=CertificateKind.ATTESTATSIYA,
            subject_code="tarix",
            max_score=100,
            passing_score=60,
            duration_minutes=90,
            sections=(
                CertificateSection(code="test", name="Test savollari", max_score=80, question_count=40,
                                   time_limit_minutes=60, order=1),
                CertificateSection(code="amaliy", name="Amaliy topshiriq", max_score=20, question_count=2,
                                   time_limit_minutes=30, order=2),
            ),
            bands=(
                CertificateBand(label="Yuqori", min_score=86, max_score=100, order=1),
                CertificateBand(label="O'rta", min_score=66, max_score=85.99, order=2),
                CertificateBand(label="Past", min_score=0, max_score=65.99, order=3),
            ),
            translations=dict(PROGRAM_TRANSLATIONS["attestatsiya-tarix"]),
        ),
        CertificateProgram(
            code="milliy-sertifikat-matematika",
            name="Milliy sertifikat — Matematika",
            kind=CertificateKind.MILLIY_SERTIFIKAT,
            subject_code="matematika",
            max_score=100,
            passing_score=60,
            duration_minutes=120,
            sections=(
                CertificateSection(code="test", name="Test savollari", max_score=70, question_count=35,
                                   time_limit_minutes=70, order=1),
                CertificateSection(code="amaliy", name="Amaliy topshiriqlar", max_score=30, question_count=3,
                                   time_limit_minutes=50, order=2),
            ),
            bands=national_bands,
            translations=dict(PROGRAM_TRANSLATIONS["milliy-sertifikat-matematika"]),
        ),
    ]


def _international_programs() -> list[CertificateProgram]:
    return [
        CertificateProgram(
            code="sat",
            name="SAT",
            kind=CertificateKind.SAT,
            max_score=1600,
            duration_minutes=134,
            sections=(
                CertificateSection(code="reading_writing", name="Reading & Writing", max_score=800,
                                   question_count=44, time_limit_minutes=64, order=1),
                CertificateSection(code="math", name="Math", max_score=800,
                                   question_count=44, time_limit_minutes=70, order=2),
            ),
        ),
        CertificateProgram(
            code="ielts",
            name="IELTS Academic",
            kind=CertificateKind.IELTS,
            max_score=9,
            duration_minutes=165,
            sections=(
                CertificateSection(code="listening", name="Listening", max_score=9, question_count=40,
                                   time_limit_minutes=30, order=1),
                CertificateSection(code="reading", name="Reading", max_score=9, question_count=40,
                                   time_limit_minutes=60, order=2),
                CertificateSection(code="writing", name="Writing", max_score=9, question_count=2,
                                   time_limit_minutes=60, order=3),
                CertificateSection(code="speaking", name="Speaking", max_score=9, question_count=3,
                                   time_limit_minutes=15, order=4),
            ),
            cefr_levels=(
                CefrLevel(level="C2", min_score=8.5, description="Expert user"),
                CefrLevel(level="C1", min_score=7.0, description="Very good user"),
                CefrLevel(level="B2", min_score=5.5, description="Good user"),
                CefrLevel(level="B1", min_score=4.0, description="Competent user"),
                CefrLevel(level="A2", min_score=0.0, description="Modest user"),
            ),
        ),
        CertificateProgram(
            code="toefl",
            name="TOEFL iBT",
            kind=CertificateKind.TOEFL,
            max_score=120,
            duration_minutes=180,
            sections=(
                CertificateSection(code="reading", name="Reading", max_score=30, question_count=30,
                                   time_limit_minutes=54, order=1),
                CertificateSection(code="listening", name="Listening", max_score=30, question_count=28,
                                   time_limit_minutes=41, order=2),
                CertificateSection(code="speaking", name="Speaking", max_score=30, question_count=4,
                                   time_limit_minutes=17, order=3),
                CertificateSection(code="writing", name="Writing", max_score=30, question_count=2,
                                   time_limit_minutes=50, order=4),
            ),
            cefr_levels=(
                CefrLevel(level="C1", min_score=95, description="Advanced"),
                CefrLevel(level="B2", min_score=72, description="Upper intermediate"),
                CefrLevel(level="B1", min_score=42, description="Intermediate"),
                CefrLevel(level="A2", min_score=0, description="Elementary"),
            ),
        ),
        CertificateProgram(
            code="cefr",
            name="CEFR daraja imtihoni",
            kind=CertificateKind.CEFR,
            max_score=100,
            duration_minutes=120,
            sections=(
                CertificateSection(code="reading", name="Reading", max_score=25, question_count=25,
                                   time_limit_minutes=30, order=1),
                CertificateSection(code="listening", name="Listening", max_score=25, question_count=25,
                                   time_limit_minutes=30, order=2),
                CertificateSection(code="writing", name="Writing", max_score=25, question_count=2,
                                   time_limit_minutes=30, order=3),
                CertificateSection(code="speaking", name="Speaking", max_score=25, question_count=3,
                                   time_limit_minutes=30, order=4),
            ),
            cefr_levels=(
                CefrLevel(level="C2", min_score=90, description="Proficient"),
                CefrLevel(level="C1", min_score=80, description="Advanced"),
                CefrLevel(level="B2", min_score=65, description="Upper intermediate"),
                CefrLevel(level="B1", min_score=50, description="Intermediate"),
                CefrLevel(level="A2", min_score=35, description="Elementary"),
                CefrLevel(level="A1", min_score=0, description="Beginner"),
            ),
            translations=dict(PROGRAM_TRANSLATIONS["cefr"]),
        ),
    ]


def default_certificate_programs() -> list[CertificateProgram]:
    """Milliy (sertifikat, attestatsiya) va xalqaro (SAT, IELTS, TOEFL, CEFR) dasturlar."""
    return _national_programs() + _international_programs()
