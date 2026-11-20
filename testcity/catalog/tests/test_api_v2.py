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

    def test_subjects_list_contains_history_and_other_fanlar(self):
        response = self.client.get("/api/v2/subjects/")
        self.assertEqual(response.status_code, 200)
        codes = [subject["code"] for subject in response.json()]
        self.assertIn("tarix", codes)
        self.assertIn("matematika", codes)

    def test_subject_detail_exposes_its_certificates(self):
        response = self.client.get("/api/v2/subjects/tarix/")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["code"], "tarix")
        codes = [program["code"] for program in payload["certificates"]]
        self.assertIn("milliy-sertifikat-tarix", codes)
        self.assertIn("attestatsiya-tarix", codes)

    def test_unknown_subject_returns_404(self):
        self.assertEqual(self.client.get("/api/v2/subjects/astronomiya/").status_code, 404)

    def test_languages_are_dynamic(self):
        response = self.client.get("/api/v2/languages/")
        self.assertEqual(response.status_code, 200)
        codes = [item["code"] for item in response.json()]
        self.assertEqual(codes[:3], ["uz", "ru", "en"])
        self.assertIn("kz", codes)

    def test_subject_names_follow_lang_parameter(self):
        ru = self.client.get("/api/v2/subjects/", {"lang": "ru"}).json()
        names = {item["code"]: item["name"] for item in ru}
        self.assertEqual(names["tarix"], "История")

        default = self.client.get("/api/v2/subjects/").json()
        default_names = {item["code"]: item["name"] for item in default}
        self.assertEqual(default_names["tarix"], "Tarix")

        unknown = self.client.get("/api/v2/subjects/", {"lang": "xx"}).json()
        unknown_names = {item["code"]: item["name"] for item in unknown}
        self.assertEqual(unknown_names["tarix"], "Tarix")

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


class CertificateApiTests(TestCase):
    @classmethod
    def setUpTestData(cls):
        call_command("seed_catalog", verbosity=0)

    def setUp(self):
        self.client = APIClient()

    def test_certificates_split_into_two_families(self):
        response = self.client.get("/api/v2/certificates/")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        families = {program["family"] for program in payload}
        self.assertEqual(families, {"national", "international"})

        national = self.client.get("/api/v2/certificates/", {"family": "national"}).json()
        self.assertEqual({p["kind"] for p in national}, {"national_certificate", "attestation"})

        international = self.client.get("/api/v2/certificates/", {"family": "international"}).json()
        self.assertEqual({p["code"] for p in international}, {"sat", "ielts", "toefl", "cefr"})

    def test_families_metadata_lists_kinds(self):
        response = self.client.get("/api/v2/certificates/families/")
        self.assertEqual(response.status_code, 200)
        families = {item["code"]: item for item in response.json()}
        self.assertIn("national", families)
        self.assertIn("toefl", [kind["code"] for kind in families["international"]["kinds"]])

    def test_national_endpoint_only_returns_national_programs(self):
        payload = self.client.get("/api/v2/certification/").json()
        self.assertTrue(all(program["family"] == "national" for program in payload))

    def test_international_endpoint_only_returns_international_programs(self):
        payload = self.client.get("/api/v2/international/").json()
        self.assertEqual({program["code"] for program in payload}, {"sat", "ielts", "toefl", "cefr"})

    def test_program_detail_has_sections(self):
        response = self.client.get("/api/v2/certificates/milliy-sertifikat-tarix/")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual([section["code"] for section in payload["sections"]], ["test", "amaliy"])
        self.assertEqual(payload["max_score"], 100.0)

    def test_national_evaluate_returns_band_and_pass_flag(self):
        response = self.client.post(
            "/api/v2/certificates/milliy-sertifikat-tarix/evaluate/",
            {"sections": {"test": 65, "amaliy": 25}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 90.0)
        self.assertEqual(payload["band_label"], "A toifa")
        self.assertTrue(payload["passed"])
        self.assertIsNone(payload["cefr_level"])

    def test_evaluate_with_missing_section_returns_400(self):
        response = self.client.post(
            "/api/v2/certificates/milliy-sertifikat-tarix/evaluate/",
            {"sections": {"test": 65}},
            format="json",
        )
        self.assertEqual(response.status_code, 400)

    def test_ielts_evaluation_reports_cefr_level(self):
        response = self.client.post(
            "/api/v2/certificates/ielts/evaluate/",
            {"sections": {"listening": 7, "reading": 7, "writing": 6.5, "speaking": 6.5}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 7.0)
        self.assertEqual(payload["cefr_level"], "C1")

    def test_toefl_evaluation_totals_sections(self):
        response = self.client.post(
            "/api/v2/certificates/toefl/evaluate/",
            {"sections": {"reading": 28, "listening": 26, "speaking": 24, "writing": 25}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["total"], 103.0)
        self.assertEqual(payload["cefr_level"], "C1")

    def test_sat_evaluation_totals_sections(self):
        response = self.client.post(
            "/api/v2/certificates/sat/evaluate/",
            {"sections": {"math": 700, "reading_writing": 650}},
            format="json",
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["total"], 1350.0)

    def test_unknown_certificate_returns_404(self):
        self.assertEqual(self.client.get("/api/v2/certificates/dtm/").status_code, 404)

    def test_certificate_names_follow_lang_parameter(self):
        payload = self.client.get("/api/v2/certificates/", {"lang": "ru"}).json()
        names = {program["code"]: program["name"] for program in payload}
        self.assertEqual(names["milliy-sertifikat-tarix"], "Национальный сертификат — История")
        self.assertEqual(names["sat"], "SAT")


class TicketPackApiTests(TestCase):
    """10/20/30 talik global biletlar: mavzu, yo'nalish, kitob, aralash, global."""

    @classmethod
    def setUpTestData(cls):
        call_command("seed_catalog", verbosity=0)
        difficulties = ("EASY", "MEDIUM", "HARD")
        for index in range(12):
            Question.objects.create(
                grade=7,
                subject="O'zbekiston tarixi",
                text=f"Savol {index}",
                option_a="A",
                option_b="B",
                option_c="C",
                option_d="D",
                correct_option="A",
                difficulty=difficulties[index % 3],
                verification_status="VERIFIED",
            )

    def setUp(self):
        self.client = APIClient()

    def test_global_pack_of_10(self):
        response = self.client.get("/api/v2/subjects/tarix/packs/", {"kind": "global", "size": 10})
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["kind"], "global")
        self.assertEqual(payload["size"], 10)
        self.assertGreater(len(payload["questions"]), 0)
        self.assertLessEqual(len(payload["questions"]), 10)

    def test_size_must_be_10_20_or_30(self):
        response = self.client.get("/api/v2/subjects/tarix/packs/", {"kind": "global", "size": 15})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["field"], "size")

    def test_direction_kind_requires_direction(self):
        response = self.client.get("/api/v2/subjects/tarix/packs/", {"kind": "direction", "size": 10})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["field"], "direction")

    def test_book_pack_for_one_grade(self):
        response = self.client.get(
            "/api/v2/subjects/tarix/packs/",
            {"kind": "book", "size": 20, "grade": 7, "track": "uzbekiston"},
        )
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["total"], 12)

    def test_mixed_pack_balances_difficulties(self):
        response = self.client.get(
            "/api/v2/subjects/tarix/packs/",
            {"kind": "mixed", "size": 10, "grade": 7, "track": "uzbekiston"},
        )
        self.assertEqual(response.status_code, 200)
        questions = response.json()["questions"]
        self.assertGreater(len(questions), 0)
        self.assertGreaterEqual(len({q["difficulty"] for q in questions}), 2)

    def test_seed_produces_fresh_packs(self):
        first = self.client.get(
            "/api/v2/subjects/tarix/packs/", {"kind": "global", "size": 10, "seed": 1}
        ).json()
        second = self.client.get(
            "/api/v2/subjects/tarix/packs/", {"kind": "global", "size": 10, "seed": 2}
        ).json()
        self.assertNotEqual(
            [q["id"] for q in first["questions"]],
            [q["id"] for q in second["questions"]],
        )
