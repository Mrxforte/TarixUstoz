"""Pure domain rules: subjects, certification scoring and international exams."""
from __future__ import annotations

from django.test import SimpleTestCase

from domain.assessment.directions import direction_for_combination, is_war_question, normalize_code
from domain.certification.entities import (
    CertificationKind,
    CertificationProgram,
    ProgramSection,
    ScoreBand,
)
from domain.international.entities import cefr_from_ielts, ielts_overall_band, sat_total
from domain.shared.errors import ValidationError
from domain.subjects.entities import Subject, SubjectCategory
from domain.tarix.subjects import HistoryTrack, canonical_history_subject, track_for_grade


class SubjectTests(SimpleTestCase):
    def test_code_is_normalized_and_grades_sorted(self):
        subject = Subject(code="Ingliz Tili", name="Ingliz tili", grades=[11, 5, 5])
        self.assertEqual(subject.code, "ingliz-tili")
        self.assertEqual(subject.grades, (5, 11))

    def test_history_subject_is_flagged(self):
        self.assertTrue(Subject(code="tarix", name="Tarix").is_history)
        self.assertFalse(Subject(code="matematika", name="Matematika").is_history)

    def test_blank_name_is_rejected(self):
        with self.assertRaises(ValueError):
            Subject(code="tarix", name="  ")

    def test_category_coercion_rejects_unknown_values(self):
        with self.assertRaises(ValueError):
            SubjectCategory.coerce("olimpiada")


class HistorySubjectTests(SimpleTestCase):
    def test_track_mapping(self):
        self.assertEqual(track_for_grade(6), HistoryTrack.ANCIENT_WORLD)
        self.assertEqual(track_for_grade(9), HistoryTrack.UZBEKISTAN)

    def test_aliases_are_canonicalised(self):
        self.assertEqual(canonical_history_subject("jahon", 8), "Jahon tarixi")
        self.assertEqual(canonical_history_subject("qadimgi", 0), "Qadimgi dunyo tarixi")
        self.assertEqual(canonical_history_subject("uzbekiston", 9), "O'zbekiston tarixi")


class DirectionTests(SimpleTestCase):
    def test_aliases_and_combinations(self):
        self.assertEqual(normalize_code("tarixiy_faktlar"), "FACT")
        self.assertEqual(direction_for_combination("MAP_BASED"), "CITY")
        self.assertEqual(direction_for_combination("UNKNOWN"), "")

    def test_keyword_detection(self):
        self.assertTrue(is_war_question("Urush natijasi qanday bo'ldi?"))
        self.assertFalse(is_war_question("Obida qachon qurilgan?"))


def _program(**overrides) -> CertificationProgram:
    defaults = dict(
        code="milliy-sertifikat-tarix",
        name="Milliy sertifikat — Tarix",
        kind=CertificationKind.NATIONAL_CERTIFICATE,
        subject_code="tarix",
        max_score=100,
        passing_score=60,
        sections=(
            ProgramSection(code="test", name="Test", max_score=70),
            ProgramSection(code="amaliy", name="Amaliy", max_score=30),
        ),
        bands=(
            ScoreBand(label="A", min_score=90, max_score=100),
            ScoreBand(label="B", min_score=60, max_score=89.99),
            ScoreBand(label="C", min_score=0, max_score=59.99),
        ),
    )
    defaults.update(overrides)
    return CertificationProgram(**defaults)


class CertificationProgramTests(SimpleTestCase):
    def test_sections_must_add_up_to_max_score(self):
        with self.assertRaises(ValidationError):
            _program(
                max_score=100,
                sections=(ProgramSection(code="test", name="Test", max_score=50),),
            )

    def test_bands_must_not_overlap(self):
        with self.assertRaises(ValidationError):
            _program(bands=(
                ScoreBand(label="A", min_score=50, max_score=100),
                ScoreBand(label="B", min_score=40, max_score=60),
            ))

    def test_scoring_rejects_missing_sections(self):
        with self.assertRaises(ValidationError):
            _program().score({"test": 40})

    def test_scoring_and_band(self):
        program = _program()
        total = program.score({"test": 60, "amaliy": 25})
        self.assertEqual(total, 85)
        self.assertEqual(program.band_for(total).label, "B")
        self.assertTrue(program.is_passing(total))
        self.assertFalse(program.is_passing(30))


class InternationalScoringTests(SimpleTestCase):
    def test_ielts_overall_band_rounds_to_half(self):
        self.assertEqual(ielts_overall_band([6.5, 6.5, 6.0, 6.0]), 6.5)
        self.assertEqual(ielts_overall_band([7.0, 7.5, 6.5, 7.0]), 7.0)

    def test_ielts_band_range_is_enforced(self):
        with self.assertRaises(ValidationError):
            ielts_overall_band([10, 6, 6, 6])

    def test_cefr_mapping(self):
        self.assertEqual(cefr_from_ielts(8.5), "C2")
        self.assertEqual(cefr_from_ielts(6.0), "B2")
        self.assertEqual(cefr_from_ielts(3.5), "A2")

    def test_sat_total(self):
        self.assertEqual(sat_total(700, 650), 1350)
        with self.assertRaises(ValidationError):
            sat_total(100, 650)
