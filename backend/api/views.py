import os
import json
import hashlib
from datetime import date
from django.conf import settings
from rest_framework import viewsets, permissions, status
from rest_framework.decorators import action
from rest_framework.response import Response
from django.db.models import Q
from api.models import (
    Category, PdfBook, Topic, HistoricalEntity,
    HistoricalRelationship, ExamCategory, Question,
    QuizSession, QuizAnswerSubmission, UserLearningProgress,
    WeakTopic, SavedQuestion
)
from api.serializers import (
    CategorySerializer, PdfBookSerializer, TopicSerializer,
    HistoricalEntitySerializer, HistoricalRelationshipSerializer,
    ExamCategorySerializer, QuestionSerializer, QuizSessionSerializer,
    UserLearningProgressSerializer, WeakTopicSerializer, SavedQuestionSerializer
)
from api.rag_engine import HistoricalRAGEngine
from api.question_engine import QuestionGenerationEngine

class CategoryViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = Category.objects.all().prefetch_related('pdf_books')
    serializer_class = CategorySerializer
    permission_classes = [permissions.AllowAny]

class PdfBookViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = PdfBook.objects.all().order_by('grade', 'subject')
    serializer_class = PdfBookSerializer
    permission_classes = [permissions.AllowAny]

    @action(detail=False, methods=['get'])
    def by_grade(self, request):
        grade = request.query_params.get('grade', 6)
        books = PdfBook.objects.filter(grade=grade)
        serializer = self.get_serializer(books, many=True)
        return Response(serializer.data)

class TopicViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = Topic.objects.all()
    serializer_class = TopicSerializer
    permission_classes = [permissions.AllowAny]

    def get_queryset(self):
        qs = Topic.objects.all().select_related('document', 'chapter')
        
        # Default to only verified topics unless requested otherwise
        include_all = self.request.query_params.get('include_all', 'false').lower() == 'true'
        if not include_all:
            qs = qs.filter(review_status='REVIEWED')

        grade = self.request.query_params.get('grade_id') or self.request.query_params.get('grade')
        if grade and grade != '0':
            qs = qs.filter(document__grade=grade)

        subject = self.request.query_params.get('subject_id') or self.request.query_params.get('subject')
        if subject:
            qs = qs.filter(document__subject__icontains=subject)

        book_id = self.request.query_params.get('textbook_id') or self.request.query_params.get('book_id')
        if book_id:
            clean_book_id = str(book_id).replace('book_', '')
            if clean_book_id.isdigit():
                qs = qs.filter(document_id=clean_book_id)
            else:
                qs = qs.filter(document__id=book_id)

        chapter_id = self.request.query_params.get('chapter_id')
        if chapter_id:
            clean_chap_id = str(chapter_id).replace('ch_', '')
            if clean_chap_id.isdigit():
                qs = qs.filter(chapter__number=clean_chap_id)
            else:
                qs = qs.filter(chapter_id=chapter_id)

        return qs

    @action(detail=False, methods=['get'])
    def by_book(self, request):
        topics = self.get_queryset()
        serializer = self.get_serializer(topics, many=True)
        return Response(serializer.data)

class HistoricalEntityViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = HistoricalEntity.objects.all().prefetch_related('aliases')
    serializer_class = HistoricalEntitySerializer
    permission_classes = [permissions.AllowAny]

    @action(detail=True, methods=['get'])
    def related(self, request, pk=None):
        entity = self.get_object()
        relationships = HistoricalRelationship.objects.filter(
            Q(source_entity=entity) | Q(target_entity=entity)
        )
        serializer = HistoricalRelationshipSerializer(relationships, many=True)
        return Response(serializer.data)

class ExamCategoryViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = ExamCategory.objects.all()
    serializer_class = ExamCategorySerializer
    permission_classes = [permissions.AllowAny]

class QuestionViewSet(viewsets.ReadOnlyModelViewSet):
    queryset = Question.objects.all()
    serializer_class = QuestionSerializer
    permission_classes = [permissions.AllowAny]

    def list(self, request, *args, **kwargs):
        grade = request.query_params.get('grade')
        limit = self._int_param('limit', 5000)
        offset = self._int_param('offset', 0)

        # The database is the primary source: it is paginated and always current.
        db_items = self._filtered_queryset()
        if db_items.exists():
            rows = list(db_items[offset: offset + limit])
            return Response(QuestionSerializer(rows, many=True).data)

        # Fallback for grades that only exist as JSON exports.
        items = self._json_items(grade, request.query_params.get('subject', ''))
        return Response(items[offset: offset + limit])

    def _int_param(self, name, default):
        raw = self.request.query_params.get(name)
        return int(raw) if raw and raw.isdigit() else default

    def _json_items(self, grade, subject):
        if not grade or grade == '0':
            return []
        tests_json_dir = getattr(settings, 'TESTS_JSON_DIR', os.path.join(settings.BASE_DIR, 'media', 'tests', 'json'))
        books_json_dir = getattr(settings, 'BOOKS_JSON_DIR', os.path.join(settings.BASE_DIR, 'media', 'books', 'json'))
        subj_clean = "Jahon_tarixi" if "jahon" in subject.lower() else ("Qadimgi_dunyo_tarixi" if int(grade) == 6 else "Ozbekiston_tarixi")
        candidates = [
            os.path.join(tests_json_dir, f"grade{grade}_all_tests.json"),
            os.path.join(books_json_dir, f"{grade}-sinf_{subj_clean}.json"),
        ]
        for filepath in candidates:
            if not os.path.exists(filepath):
                continue
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
            except Exception:
                continue
            items = data.get("questions", []) if isinstance(data, dict) else (data if isinstance(data, list) else [])
            if not items:
                continue
            topic_id = self.request.query_params.get('topic_id')
            if topic_id:
                items = [item for item in items if str(item.get('topic_id')) == str(topic_id)]
            difficulty = self.request.query_params.get('difficulty')
            if difficulty and difficulty.upper() not in ["ANY", "ALL", "NONE", ""]:
                items = [item for item in items if item.get('difficulty') == difficulty]
            if items:
                return items
        return []

    def _filtered_queryset(self):
        qs = Question.objects.filter(verification_status='VERIFIED')
        grade = self.request.query_params.get('grade')
        if grade and grade != '0':
            qs = qs.filter(grade=grade)

        subject = self.request.query_params.get('subject')
        if subject:
            s_lower = subject.lower()
            # Exact matches can use the (grade, subject, ...) index; icontains cannot.
            if "jahon" in s_lower:
                qs = qs.filter(subject="Jahon tarixi")
            elif "uz" in s_lower or "zbek" in s_lower:
                qs = qs.filter(subject="O'zbekiston tarixi")
            elif "qadimgi" in s_lower:
                qs = qs.filter(subject="Qadimgi dunyo tarixi")
            else:
                qs = qs.filter(subject__icontains=subject)

        topic_id = self.request.query_params.get('topic_id')
        if topic_id:
            qs = qs.filter(topic_id=topic_id)

        difficulty = self.request.query_params.get('difficulty')
        if difficulty and difficulty.upper() not in ["ANY", "ALL", "NONE", ""]:
            qs = qs.filter(difficulty=difficulty)
        return qs.order_by('id')

    def get_queryset(self):
        limit = self._int_param('limit', 200)
        offset = self._int_param('offset', 0)
        return self._filtered_queryset()[offset: offset + limit]

    @action(detail=False, methods=['get'])
    def daily(self, request):
        grade = int(request.query_params.get("grade", 6))
        subject = request.query_params.get("subject", "")
        user_id = request.query_params.get("user_identifier", "guest_user")

        today_str = date.today().isoformat()
        seed_str = f"{today_str}_{user_id}_{grade}_{subject}"
        seed_num = int(hashlib.md5(seed_str.encode('utf-8')).hexdigest(), 16)

        qs = Question.objects.filter(verification_status='VERIFIED')
        if not qs.exists():
            qs = Question.objects.all()

        filtered_qs = qs
        if grade and grade > 0:
            filtered_qs = filtered_qs.filter(grade=grade)
        if subject:
            filtered_qs = filtered_qs.filter(subject__icontains=subject)

        count = filtered_qs.count()
        if count == 0:
            filtered_qs = qs
            count = filtered_qs.count()

        if count > 0:
            question = filtered_qs[seed_num % count]
            serializer = self.get_serializer(question)
            return Response(serializer.data)

        return Response({"error": "No daily question available."}, status=status.HTTP_404_NOT_FOUND)

    @action(detail=True, methods=['get'])
    def explanation(self, request, pk=None):
        question = self.get_object()
        return Response({
            "id": question.id,
            "explanation": question.explanation,
            "short_explanation": question.short_explanation,
            "source_citation": question.source_citation,
            "correct_option": question.correct_option
        })

    @action(detail=True, methods=['post'])
    def save_question(self, request, pk=None):
        question = self.get_object()
        user_id = request.data.get("user_identifier", "guest_user")
        note = request.data.get("note", "")
        saved, _ = SavedQuestion.objects.get_or_create(
            user_identifier=user_id,
            question=question,
            defaults={"note": note}
        )
        return Response({"status": "saved", "id": saved.id})

class RAGSearchViewSet(viewsets.ViewSet):
    permission_classes = [permissions.AllowAny]

    def list(self, request):
        query = request.query_params.get("q", "")
        grade = int(request.query_params.get("grade", 0))
        subject = request.query_params.get("subject", "")

        if not query:
            return Response({"error": "Query parameter 'q' is required."}, status=status.HTTP_400_BAD_REQUEST)

        rag = HistoricalRAGEngine()
        result = rag.answer_question(query, grade=grade, subject=subject)
        return Response(result)

class QuizGenerationViewSet(viewsets.ViewSet):
    permission_classes = [permissions.AllowAny]

    def create(self, request):
        count = int(request.data.get("count", 10))
        category_code = request.data.get("category_code", "PRACTICE")
        grade = int(request.data.get("grade", 0))
        subject = request.data.get("subject", "")
        difficulty = request.data.get("difficulty", "MEDIUM")
        topic_ids = request.data.get("topic_ids", [])

        engine = QuestionGenerationEngine()
        session = engine.generate_quiz_session(
            requested_count=count,
            category_code=category_code,
            grade=grade,
            subject=subject,
            topic_ids=topic_ids,
            difficulty=difficulty
        )

        serializer = QuizSessionSerializer(session)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

    def retrieve(self, request, pk=None):
        try:
            session = QuizSession.objects.get(session_id=pk)
            serializer = QuizSessionSerializer(session)
            return Response(serializer.data)
        except QuizSession.DoesNotExist:
            return Response({"error": "Quiz session not found."}, status=status.HTTP_404_NOT_FOUND)

    @action(detail=True, methods=['post'])
    def submit_answer(self, request, pk=None):
        try:
            session = QuizSession.objects.get(session_id=pk)
        except QuizSession.DoesNotExist:
            return Response({"error": "Quiz session not found."}, status=status.HTTP_404_NOT_FOUND)

        question_id = request.data.get("question_id")
        selected_option = request.data.get("selected_option", "").upper()
        time_taken = int(request.data.get("time_taken_seconds", 0))

        try:
            question = Question.objects.get(id=question_id)
        except Question.DoesNotExist:
            return Response({"error": "Question not found."}, status=status.HTTP_404_NOT_FOUND)

        is_correct = (selected_option == question.correct_option)

        submission, _ = QuizAnswerSubmission.objects.update_or_create(
            quiz_session=session,
            question=question,
            defaults={
                "selected_option": selected_option,
                "is_correct": is_correct,
                "time_taken_seconds": time_taken
            }
        )

        # Track weak topics if user failed
        user_id = session.user_identifier
        if not is_correct and question.topic:
            wt, _ = WeakTopic.objects.get_or_create(
                user_identifier=user_id,
                topic=question.topic
            )
            wt.error_count += 1
            wt.save()

        return Response({
            "is_correct": is_correct,
            "correct_option": question.correct_option,
            "explanation": question.explanation,
            "short_explanation": question.short_explanation,
            "source_citation": question.source_citation
        })

    @action(detail=True, methods=['post'])
    def finish(self, request, pk=None):
        try:
            session = QuizSession.objects.get(session_id=pk)
        except QuizSession.DoesNotExist:
            return Response({"error": "Quiz session not found."}, status=status.HTTP_404_NOT_FOUND)

        submissions = session.submissions.all()
        correct_count = submissions.filter(is_correct=True).count()
        total = session.total_questions or 1

        score_percent = round((correct_count / total) * 100, 2)
        session.score = score_percent
        session.is_completed = True
        session.save()

        return Response({
            "session_id": session.session_id,
            "total_questions": total,
            "correct_answers": correct_count,
            "score_percentage": score_percent,
            "is_completed": True
        })

class UserProgressViewSet(viewsets.ViewSet):
    permission_classes = [permissions.AllowAny]

    def list(self, request):
        user_id = request.query_params.get("user_identifier", "guest_user")
        progress = UserLearningProgress.objects.filter(user_identifier=user_id)
        weak_topics = WeakTopic.objects.filter(user_identifier=user_id)
        saved_questions = SavedQuestion.objects.filter(user_identifier=user_id)

        return Response({
            "user_identifier": user_id,
            "progress": UserLearningProgressSerializer(progress, many=True).data,
            "weak_topics": WeakTopicSerializer(weak_topics, many=True).data,
            "saved_questions": SavedQuestionSerializer(saved_questions, many=True).data
        })
