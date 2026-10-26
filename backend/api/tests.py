import os
import json
import tempfile
from django.test import TestCase, Client
from django.core.management import call_command
from django.urls import reverse
from rest_framework import status
from api.models import (
    Category, PdfBook, DocumentPage, HistoricalChapter, Topic,
    HistoricalChunk, HistoricalEntity, HistoricalEntityAlias,
    EntityMention, HistoricalRelationship, ExamCategory, Question,
    QuizSession, QuizAnswerSubmission, UserLearningProgress,
    WeakTopic, SavedQuestion
)
from api.serializers import PdfBookSerializer, CategorySerializer, QuestionSerializer


class ModelTests(TestCase):
    """Tests for Django database models."""

    def setUp(self):
        self.category = Category.objects.create(name="6-sinf Tarix", order=6)
        self.book = PdfBook.objects.create(
            category=self.category,
            title="6-sinf Tarix (Qadimgi dunyo)",
            grade=6,
            subject="Qadimgi dunyo tarixi",
            pdf_file="pdfs/test.pdf",
            total_pages=10,
            is_processed=True,
            processing_status="COMPLETED"
        )
        self.chapter = HistoricalChapter.objects.create(
            document=self.book,
            number=1,
            title="1-bob: Qadimgi Dunyo",
            start_page=1,
            end_page=5
        )
        self.topic = Topic.objects.create(
            category=self.category,
            document=self.book,
            chapter=self.chapter,
            title="1-mavzu: Qadimgi Odamiylar",
            order=1,
            start_page=1,
            end_page=2,
            content="Qadimgi odamlar haqida ma'lumot.",
            review_status='REVIEWED'
        )

    def test_category_and_book_str(self):
        self.assertEqual(str(self.category), "6-sinf Tarix")
        self.assertIn("6-sinf Tarix", str(self.book))

    def test_document_page_creation(self):
        page = DocumentPage.objects.create(
            document=self.book,
            page_number=1,
            raw_text="Qadimgi Misr madaniyati.",
            normalized_text="Qadimgi Misr madaniyati.",
            char_count=23,
            word_count=3
        )
        self.assertEqual(page.page_number, 1)
        self.assertEqual(str(page), f"{self.book.title} - 1-bet")

    def test_chunk_and_entity_models(self):
        page = DocumentPage.objects.create(
            document=self.book,
            page_number=1,
            raw_text="Amir Temur 1336-yilda tug'ilgan.",
            normalized_text="Amir Temur 1336-yilda tug'ilgan."
        )
        chunk = HistoricalChunk.objects.create(
            chunk_id="chunk_test_1",
            document=self.book,
            page=page,
            topic=self.topic,
            chunk_index=0,
            text="Amir Temur 1336-yilda tug'ilgan.",
            source_provenance="Test, 1-bet"
        )
        entity = HistoricalEntity.objects.create(
            canonical_name="Amir Temur",
            entity_type="PERSON",
            description="Buyuk sarkarda"
        )
        alias = HistoricalEntityAlias.objects.create(
            entity=entity,
            alias_name="Temur",
            language="UZ_LATIN"
        )
        mention = EntityMention.objects.create(
            entity=entity,
            chunk=chunk,
            mention_text="Amir Temur",
            confidence=0.95
        )
        self.assertEqual(str(entity), "Amir Temur (Tarixiy shaxs)")
        self.assertEqual(mention.entity, entity)


class SerializerTests(TestCase):
    """Tests for REST Framework Serializers."""

    def setUp(self):
        self.category = Category.objects.create(name="7-sinf", order=7)
        self.book = PdfBook.objects.create(
            category=self.category,
            title="7-sinf Jahon tarixi",
            grade=7,
            subject="Jahon tarixi",
            pdf_file="pdfs/7_jahon.pdf"
        )

    def test_pdf_book_serializer(self):
        serializer = PdfBookSerializer(self.book)
        data = serializer.data
        self.assertEqual(data['title'], "7-sinf Jahon tarixi")
        self.assertEqual(data['grade_id'], 7)
        self.assertIn("pdf_url", data)

    def test_category_serializer(self):
        serializer = CategorySerializer(self.category)
        data = serializer.data
        self.assertEqual(data['name'], "7-sinf")
        self.assertEqual(len(data['pdf_books']), 1)


class ApiEndpointTests(TestCase):
    """Integration tests for all REST API endpoints."""

    def setUp(self):
        self.client = Client()
        self.category = Category.objects.create(name="8-sinf Tarix", order=8)
        self.book = PdfBook.objects.create(
            category=self.category,
            title="8-sinf O'zbekiston tarixi",
            grade=8,
            subject="O'zbekiston tarixi",
            pdf_file="pdfs/8_uzb.pdf"
        )
        self.topic = Topic.objects.create(
            category=self.category,
            document=self.book,
            title="8-sinf 1-mavzu: Buxoro amirligi",
            order=1,
            start_page=1,
            end_page=5,
            content="Buxoro amirligi tarixi",
            review_status='REVIEWED'
        )
        self.question = Question.objects.create(
            topic=self.topic,
            grade=8,
            subject="O'zbekiston tarixi",
            text="Buxoro amirligiga kim asos solgan?",
            option_a="Amir Shohmurod",
            option_b="Amir Nasrullo",
            option_c="Amir Olimxon",
            option_d="Amir Muzaffar",
            correct_option="A",
            explanation="Amir Shohmurod 1785-yilda Buxoro amirligiga asos solgan.",
            difficulty="MEDIUM"
        )

    def test_get_categories(self):
        response = self.client.get('/api/categories/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    def test_get_books_endpoints(self):
        response_books = self.client.get('/api/books/')
        self.assertEqual(response_books.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response_books.data), 1)

        response_pdf_books = self.client.get('/api/pdf-books/')
        self.assertEqual(response_pdf_books.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response_pdf_books.data), 1)

    def test_get_topics_and_by_book(self):
        response = self.client.get('/api/topics/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        response_by_book = self.client.get(f'/api/topics/by_book/?book_id={self.book.id}')
        self.assertEqual(response_by_book.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response_by_book.data), 1)

    def test_get_questions_and_explanation(self):
        response = self.client.get('/api/questions/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

        exp_response = self.client.get(f'/api/questions/{self.question.id}/explanation/')
        self.assertEqual(exp_response.status_code, status.HTTP_200_OK)
        self.assertIn("explanation", exp_response.data)

    def test_save_question(self):
        response = self.client.post(
            f'/api/questions/{self.question.id}/save_question/',
            data=json.dumps({"user_identifier": "test_user_1", "note": "Klassik savol"}),
            content_type='application/json'
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(SavedQuestion.objects.filter(user_identifier="test_user_1").exists())

    def test_quiz_generation_and_submission_workflow(self):
        # 1. Generate Quiz Session
        gen_payload = {
            "count": 5,
            "category_code": "PRACTICE",
            "grade": 8,
            "subject": "O'zbekiston tarixi",
            "difficulty": "MEDIUM"
        }
        gen_resp = self.client.post(
            '/api/quizzes/generate/',
            data=json.dumps(gen_payload),
            content_type='application/json'
        )
        self.assertEqual(gen_resp.status_code, status.HTTP_201_CREATED)
        session_id = gen_resp.data['session_id']

        # 2. Retrieve Quiz Session
        get_resp = self.client.get(f'/api/quizzes/{session_id}/')
        self.assertEqual(get_resp.status_code, status.HTTP_200_OK)

        # 3. Submit Answer
        sub_payload = {
            "question_id": self.question.id,
            "selected_option": "A",
            "time_taken_seconds": 12
        }
        sub_resp = self.client.post(
            f'/api/quizzes/{session_id}/answers/',
            data=json.dumps(sub_payload),
            content_type='application/json'
        )
        self.assertEqual(sub_resp.status_code, status.HTTP_200_OK)
        self.assertTrue(sub_resp.data['is_correct'])

        # 4. Finish Quiz
        finish_resp = self.client.post(f'/api/quizzes/{session_id}/finish/')
        self.assertEqual(finish_resp.status_code, status.HTTP_200_OK)
        self.assertTrue(finish_resp.data['is_completed'])

    def test_user_progress_endpoint(self):
        response = self.client.get('/api/users/me/progress/?user_identifier=guest_user')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("progress", response.data)
        self.assertIn("weak_topics", response.data)

    def test_rag_search_endpoint(self):
        response = self.client.get('/api/search/?q=Buxoro&grade=8')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("answer", response.data)


class ManagementCommandsTests(TestCase):
    """Tests for custom Django management commands."""

    def test_audit_textbooks_command(self):
        with tempfile.NamedTemporaryFile(suffix='.json', delete=False) as tmp:
            tmp_path = tmp.name

        try:
            call_command('audit_textbooks', json_output=tmp_path)
            self.assertTrue(os.path.exists(tmp_path))
            with open(tmp_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.assertIn("audit_completion_percentage", data)
                self.assertIn("database_counts", data)
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_backup_textbook_data_command(self):
        with tempfile.NamedTemporaryFile(suffix='.sql', delete=False) as tmp:
            tmp_path = tmp.name

        try:
            call_command('backup_textbook_data', output=tmp_path, scope='textbooks', test_restore=True)
            self.assertTrue(os.path.exists(tmp_path))
            self.assertGreater(os.path.getsize(tmp_path), 0)
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)

    def test_export_textbooks_command(self):
        book = PdfBook.objects.create(
            title="9-sinf O'zbekiston tarixi",
            grade=9,
            subject="O'zbekiston tarixi",
            pdf_file="pdfs/9_uzb.pdf"
        )
        call_command('export_textbooks', book_id=book.id)
        book.refresh_from_db()
        self.assertEqual(book.export_status, 'COMPLETED')
        self.assertTrue(os.path.exists(book.txt_export_path))
        self.assertTrue(os.path.exists(book.json_export_path))
        self.assertTrue(os.path.exists(book.jsonl_export_path))
