"""Pure domain rules: subjects, certificates (milliy + xalqaro) and assessment helpers."""
from __future__ import annotations

from django.test import SimpleTestCase

from domain.assessment.directions import direction_for_combination, is_war_question, normalize_code
from domain.certificates.entities import (
    CefrLevel,
    CertificateBand,
    CertificateFamily,
    CertificateKind,
    CertificateProgram,
    CertificateSection,
)
from domain.certificates.scoring import cefr_from_ielts, ielts_overall_band, sat_total, toefl_total
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


def _program(**overrides) -> CertificateProgram:
    defaults = dict(
        code="milliy-sertifikat-tarix",
        name="Milliy sertifikat — Tarix",
        kind=CertificateKind.MILLIY_SERTIFIKAT,
        subject_code="tarix",
        max_score=100,
        passing_score=60,
        sections=(
            CertificateSection(code="test", name="Test", max_score=70),
            CertificateSection(code="amaliy", name="Amaliy", max_score=30),
        ),
        bands=(
            CertificateBand(label="A", min_score=90, max_score=100),
            CertificateBand(label="B", min_score=60, max_score=89.99),
            CertificateBand(label="C", min_score=0, max_score=59.99),
        ),
    )
    defaults.update(overrides)
    return CertificateProgram(**defaults)


class CertificateFamilyTests(SimpleTestCase):
    def test_families_split_national_and_international(self):
        self.assertIs(CertificateKind.MILLIY_SERTIFIKAT.family, CertificateFamily.NATIONAL)
        self.assertIs(CertificateKind.ATTESTATSIYA.family, CertificateFamily.NATIONAL)
        self.assertIs(CertificateKind.IELTS.family, CertificateFamily.INTERNATIONAL)
        self.assertEqual(
            {kind.value for kind in CertificateKind.for_family(CertificateFamily.INTERNATIONAL)},
            {"sat", "ielts", "toefl", "cefr"},
        )

    def test_national_program_requires_subject(self):
        with self.assertRaises(ValidationError):
            _program(subject_code="")


class CertificateProgramTests(SimpleTestCase):
    def test_sections_must_add_up_to_max_score(self):
        with self.assertRaises(ValidationError):
            _program(max_score=100, sections=(CertificateSection(code="test", name="Test", max_score=50),))

    def test_bands_must_not_overlap(self):
        with self.assertRaises(ValidationError):
            _program(bands=(
                CertificateBand(label="A", min_score=50, max_score=100),
                CertificateBand(label="B", min_score=40, max_score=60),
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

    def test_toefl_total(self):
        self.assertEqual(toefl_total({"reading": 28, "writing": 27}), 55)
        with self.assertRaises(ValidationError):
            toefl_total({"reading": 40})

    def _ielts_program(self) -> CertificateProgram:
        return CertificateProgram(
            code="ielts",
            name="IELTS",
            kind=CertificateKind.IELTS,
            max_score=9,
            sections=tuple(
                CertificateSection(code=code, name=code.title(), max_score=9)
                for code in ("listening", "reading", "writing", "speaking")
            ),
            cefr_levels=(CefrLevel(level="B2", min_score=5.5),),
        )

    def test_ielts_program_total_uses_band_average(self):
        program = self._ielts_program()
        self.assertEqual(
            program.total({"listening": 7, "reading": 7, "writing": 6.5, "speaking": 6.5}),
            7.0,
        )
        self.assertEqual(program.cefr_for(7.0), "C1")

    def _toefl_program(self) -> CertificateProgram:
        return CertificateProgram(
            code="toefl",
            name="TOEFL",
            kind=CertificateKind.TOEFL,
            max_score=120,
            sections=tuple(
                CertificateSection(code=code, name=code.title(), max_score=30)
                for code in ("reading", "listening", "speaking", "writing")
            ),
            cefr_levels=(
                CefrLevel(level="C1", min_score=95),
                CefrLevel(level="B2", min_score=72),
            ),
        )

    def test_toefl_program_total_and_cefr(self):
        program = self._toefl_program()
        total = program.total({"reading": 28, "listening": 26, "speaking": 24, "writing": 25})
        self.assertEqual(total, 103)
        self.assertEqual(program.cefr_for(total), "C1")
