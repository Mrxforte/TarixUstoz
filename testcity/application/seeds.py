"""Default catalogue: fanlar and exam blueprints seeded into a fresh database."""
from __future__ import annotations

from domain.certification.entities import (
    CertificationKind,
    CertificationProgram,
    ProgramSection,
    ScoreBand,
)
from domain.international.entities import CefrLevel, ExamSection, InternationalExam, InternationalExamKind
from domain.subjects.entities import Subject, SubjectCategory

SCHOOL_GRADES = (5, 6, 7, 8, 9, 10, 11)


def default_subjects() -> list[Subject]:
    """Every fan the platform ships with. Tarix stays first — it is the original subject."""
    definitions = [
        ("tarix", "Tarix", "History", "🏛️"),
        ("matematika", "Matematika", "Mathematics", "➗"),
        ("ona-tili", "Ona tili", "Uzbek language", "📗"),
        ("adabiyot", "Adabiyot", "Literature", "📚"),
        ("ingliz-tili", "Ingliz tili", "English", "🇬🇧"),
        ("rus-tili", "Rus tili", "Russian", "🇷🇺"),
        ("fizika", "Fizika", "Physics", "🔭"),
        ("kimyo", "Kimyo", "Chemistry", "⚗️"),
        ("biologiya", "Biologiya", "Biology", "🧬"),
        ("geografiya", "Geografiya", "Geography", "🌍"),
        ("informatika", "Informatika", "Computer science", "💻"),
    ]
    return [
        Subject(
            code=code,
            name=name,
            name_en=name_en,
            category=SubjectCategory.SCHOOL,
            grades=SCHOOL_GRADES,
            icon=icon,
            order=index,
        )
        for index, (code, name, name_en, icon) in enumerate(definitions, start=1)
    ]


def default_certification_programs() -> list[CertificationProgram]:
    return [
        CertificationProgram(
            code="milliy-sertifikat-tarix",
            name="Milliy sertifikat — Tarix",
            kind=CertificationKind.NATIONAL_CERTIFICATE,
            subject_code="tarix",
            max_score=100,
            passing_score=60,
            duration_minutes=120,
            sections=(
                ProgramSection(code="test", name="Test savollari", max_score=70, question_count=35,
                               time_limit_minutes=70, order=1),
                ProgramSection(code="amaliy", name="Amaliy topshiriqlar", max_score=30, question_count=3,
                               time_limit_minutes=50, order=2),
            ),
            bands=(
                ScoreBand(label="A toifa", min_score=90, max_score=100, description="Yuqori natija", order=1),
                ScoreBand(label="B toifa", min_score=80, max_score=89.99, description="Yaxshi natija", order=2),
                ScoreBand(label="C toifa", min_score=60, max_score=79.99, description="Qoniqarli natija", order=3),
                ScoreBand(label="Sertifikatsiz", min_score=0, max_score=59.99, description="Qayta topshirish",
                          order=4),
            ),
        ),
        CertificationProgram(
            code="attestatsiya-tarix",
            name="Attestatsiya — Tarix",
            kind=CertificationKind.ATTESTATION,
            subject_code="tarix",
            max_score=100,
            passing_score=60,
            duration_minutes=90,
            sections=(
                ProgramSection(code="test", name="Test savollari", max_score=80, question_count=40,
                               time_limit_minutes=60, order=1),
                ProgramSection(code="amaliy", name="Amaliy topshiriq", max_score=20, question_count=2,
                               time_limit_minutes=30, order=2),
            ),
            bands=(
                ScoreBand(label="Yuqori", min_score=86, max_score=100, order=1),
                ScoreBand(label="O'rta", min_score=66, max_score=85.99, order=2),
                ScoreBand(label="Past", min_score=0, max_score=65.99, order=3),
            ),
        ),
    ]


def default_international_exams() -> list[InternationalExam]:
    return [
        InternationalExam(
            code="sat",
            name="SAT",
            kind=InternationalExamKind.SAT,
            max_score=1600,
            duration_minutes=134,
            sections=(
                ExamSection(code="reading_writing", name="Reading & Writing", max_score=800,
                            question_count=44, time_limit_minutes=64, order=1),
                ExamSection(code="math", name="Math", max_score=800,
                            question_count=44, time_limit_minutes=70, order=2),
            ),
        ),
        InternationalExam(
            code="ielts",
            name="IELTS Academic",
            kind=InternationalExamKind.IELTS,
            max_score=9,
            duration_minutes=165,
            sections=(
                ExamSection(code="listening", name="Listening", max_score=9, question_count=40,
                            time_limit_minutes=30, order=1),
                ExamSection(code="reading", name="Reading", max_score=9, question_count=40,
                            time_limit_minutes=60, order=2),
                ExamSection(code="writing", name="Writing", max_score=9, question_count=2,
                            time_limit_minutes=60, order=3),
                ExamSection(code="speaking", name="Speaking", max_score=9, question_count=3,
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
        InternationalExam(
            code="cefr",
            name="CEFR daraja imtihoni",
            kind=InternationalExamKind.CEFR,
            max_score=100,
            duration_minutes=120,
            sections=(
                ExamSection(code="reading", name="Reading", max_score=25, question_count=25, time_limit_minutes=30,
                            order=1),
                ExamSection(code="listening", name="Listening", max_score=25, question_count=25,
                            time_limit_minutes=30, order=2),
                ExamSection(code="writing", name="Writing", max_score=25, question_count=2, time_limit_minutes=30,
                            order=3),
                ExamSection(code="speaking", name="Speaking", max_score=25, question_count=3, time_limit_minutes=30,
                            order=4),
            ),
            cefr_levels=(
                CefrLevel(level="C2", min_score=90, description="Proficient"),
                CefrLevel(level="C1", min_score=80, description="Advanced"),
                CefrLevel(level="B2", min_score=65, description="Upper intermediate"),
                CefrLevel(level="B1", min_score=50, description="Intermediate"),
                CefrLevel(level="A2", min_score=35, description="Elementary"),
                CefrLevel(level="A1", min_score=0, description="Beginner"),
            ),
        ),
    ]
