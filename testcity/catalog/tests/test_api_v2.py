"""HTTP contract tests for the v2 clean-architecture API."""
from __future__ import annotations

from django.core.management import call_command
from django.test import TestCase
from rest_framework.test import APIClient

from api.models import Question


class CatalogApiTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        call_command("seed_catalog", verbosity=0)
        Question.objects.create(
            grade=7,
            subject="O'zbekiston tarixi",
            text="Amir Temur qachon tavallud topgan?",
            option_a="1336",
            option_b="1346",
            option_c="1356",
            option_d="1366",
            correct_option="A",
            difficulty="MEDIUM",
            verification_status="VERIFIED",
            explanation="1336-yilda Shahrisabzda tug'ilgan.",
        )

    def setUp(self):
        self.client = APIClient()

    def test_subjects_list_contains_history(self):
        response = self.client.get("/api/v2/subjects/")
        self.assertEqual(response.status_code, 200)
        codes = [subject["code"] for subject in response.json()]
        self.assertIn("tarix", codes)
        self.assertIn("matematika", codes)

    def test_subject_detail_exposes_certification_programs(self):
        response = self.client.get("/api/v2/subjects/tarix/")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["code"], "tarix")
        self.assertIn("milliy-sertifikat-tarix", [p["code"] for p in payload["programs"]])

    def test_unknown_subject_returns_404(self):
        self.assertEqual(self.client.get("/api/v2/subjects/astronomiya/").status_code, 404)

    def test_history_overview_counts_questions(self):
        response = self.client.get(
            "/api/v2/subjects/tarix/overview/", {"grade": 7, "track": "uzbekiston"}
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["subject"], "tarix")
        self.assertEqual(payload["question_count"], 1)
        self.assertIsInstance(payload["direction_counts"], dict)

    def test_history_ticket_returns_questions(self):
        response = self.client.get(
            "/api/v2/subjects/tarix/tickets/", {"grade": 7, "track": "uzbekiston", "size": 30}
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 1)
        self.assertEqual(len(payload["questions"]), 1)
        self.assertEqual(payload["questions"][0]["correct_option"], "A")

    def test_ticket_size_is_validated(self):
        response = self.client.get("/api/v2/subjects/tarix/tickets/", {"size": 500})
        self.assertEqual(response.status_code, 400)

    def test_daily_question_is_stable(self):
        first = self.client.get("/api/v2/subjects/tarix/daily/", {"grade": 7, "track": "uzbekiston"})
        second = self.client.get("/api/v2/subjects/tarix/daily/", {"grade": 7, "track": "uzbekiston"})
        self.assertEqual(first.status_code, 200)
        self.assertEqual(first.json()["id"], second.json()["id"])


class CertificationApiTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        call_command("seed_catalog", verbosity=0)

    def setUp(self):
        self.client = APIClient()

    def test_lists_national_certificate_and_attestation(self):
        response = self.client.get("/api/v2/certification/")
        self.assertEqual(response.status_code, 200)
        kinds = {program["kind"] for program in response.json()}
        self.assertEqual(kinds, {"national_certificate", "attestation"})

    def test_program_detail_has_sections_and_bands(self):
        response = self.client.get("/api/v2/certification/milliy-sertifikat-tarix/")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual([section["code"] for section in payload["sections"]], ["test", "amaliy"])
        self.assertEqual(payload["max_score"], 100.0)

    def test_evaluate_returns_band_and_pass_flag(self):
        response = self.client.post(
            "/api/v2/certification/milliy-sertifikat-tarix/evaluate/",
            {"sections": {"test": 65, "amaliy": 25}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 90.0)
        self.assertEqual(payload["band_label"], "A toifa")
        self.assertTrue(payload["passed"])

    def test_evaluate_with_missing_section_returns_400(self):
        response = self.client.post(
            "/api/v2/certification/milliy-sertifikat-tarix/evaluate/",
            {"sections": {"test": 65}},
            format="json",
        )
        self.assertEqual(response.status_code, 400)


class InternationalApiTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        call_command("seed_catalog", verbosity=0)

    def setUp(self):
        self.client = APIClient()

    def test_lists_sat_ielts_and_cefr(self):
        response = self.client.get("/api/v2/international/")
        self.assertEqual(response.status_code, 200)
        codes = {exam["code"] for exam in response.json()}
        self.assertEqual(codes, {"sat", "ielts", "cefr"})

    def test_ielts_evaluation_reports_cefr_level(self):
        response = self.client.post(
            "/api/v2/international/ielts/evaluate/",
            {"sections": {"listening": 7, "reading": 7, "writing": 6.5, "speaking": 6.5}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 7.0)
        self.assertEqual(payload["cefr_level"], "C1")

    def test_sat_evaluation_totals_sections(self):
        response = self.client.post(
            "/api/v2/international/sat/evaluate/",
            {"sections": {"math": 700, "reading_writing": 650}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["total"], 1350.0)

    def test_unknown_exam_returns_404(self):
        self.assertEqual(self.client.get("/api/v2/international/toefl/").status_code, 404)
