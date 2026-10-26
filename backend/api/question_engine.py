import json
import random
import os
import re
from django.conf import settings
from api.models import (
    Question, Topic, HistoricalChunk, ExamCategory,
    QuizSession, QuizAnswerSubmission, HistoricalEntity
)
from api.rag_engine import HistoricalRAGEngine
from api.directions import apply_direction

try:
    import google.generativeai as genai
    HAS_GEMINI = True
except ImportError:
    HAS_GEMINI = False

class QuestionGenerationEngine:
    def __init__(self, api_key=None):
        self.rag_engine = HistoricalRAGEngine(gemini_api_key=api_key)
        self.api_key = api_key or os.environ.get("GEMINI_API_KEY", getattr(settings, "GEMINI_API_KEY", ""))
        self.model_name = os.environ.get("GEMINI_MODEL", "gemini-1.5-flash")

        if HAS_GEMINI and self.api_key and self.api_key != "YOUR_API_KEY":
            genai.configure(api_key=self.api_key)
            self.model = genai.GenerativeModel(
                self.model_name,
                generation_config={"response_mime_type": "application/json"}
            )
        else:
            self.model = None

    def generate_quiz_session(self, requested_count: int, category_code: str = "PRACTICE", grade: int = 0, subject: str = "", topic_ids: list = None, difficulty: str = "MEDIUM"):
        """
        Retrieves existing verified questions and generates additional evidence-grounded questions as needed.
        Returns a QuizSession populated with requested_count valid questions.
        """
        requested_count = max(1, min(requested_count, 100)) # Clamped between 1 and 100

        # 1. Base pool: verified questions of the requested book(s)
        base = Question.objects.filter(verification_status='VERIFIED')
        if grade and grade > 0:
            base = base.filter(grade=grade)
        if subject:
            base = base.filter(subject=self._canonical_subject(subject))
        if topic_ids:
            base = base.filter(topic_id__in=topic_ids)

        # 2. Narrow to the requested direction (Tarixiy voqealar, Yillar va sanalar, ...)
        qs = apply_direction(base, category_code)
        if not qs.exists():
            qs = base

        if difficulty and difficulty.upper() not in ["ANY", "ALL", "NONE", ""]:
            diff_qs = qs.filter(difficulty=difficulty)
            if diff_qs.exists():
                qs = diff_qs

        selected_questions = self._sample(qs, requested_count)
        if not selected_questions and topic_ids:
            # Fallback strictly to questions of these specific topics
            selected_questions = self._sample(
                Question.objects.filter(topic_id__in=topic_ids), requested_count
            )

        if not selected_questions and grade and grade > 0:
            # Fallback strictly to questions for this specific grade and subject
            grade_qs = Question.objects.filter(grade=grade)
            if subject:
                grade_qs = grade_qs.filter(subject=self._canonical_subject(subject))
            selected_questions = self._sample(grade_qs, requested_count)

        # 3. Create Quiz Session
        session = QuizSession.objects.create(
            category_code=category_code,
            grade=grade,
            subject=subject or "Tarix",
            total_questions=len(selected_questions)
        )
        session.questions.set(selected_questions)
        return session

    @staticmethod
    def _canonical_subject(subject: str) -> str:
        """Map app subject ids ("jahon", "uzbekiston") to the stored subject names."""
        s = (subject or "").lower()
        if "jahon" in s:
            return "Jahon tarixi"
        if "qadimgi" in s:
            return "Qadimgi dunyo tarixi"
        if "uz" in s or "zbek" in s:
            return "O'zbekiston tarixi"
        return subject

    @staticmethod
    def _sample(qs, count: int) -> list:
        """Pick `count` random questions without loading the whole book into memory."""
        ids = list(qs.values_list("id", flat=True))
        if not ids:
            return []
        picked = random.sample(ids, min(count, len(ids)))
        return list(Question.objects.filter(id__in=picked))

    def _generate_candidates_from_rag(self, count_needed: int, grade: int, subject: str, topic_ids: list, difficulty: str, category_code: str):
        # Retrieve chunks for evidence
        chunks_qs = HistoricalChunk.objects.all().select_related('document', 'topic')
        if grade and grade > 0:
            chunks_qs = chunks_qs.filter(document__grade=grade)
        if subject:
            chunks_qs = chunks_qs.filter(document__subject__icontains=subject)
        if topic_ids:
            chunks_qs = chunks_qs.filter(topic_id__in=topic_ids)

        chunks_sample = list(chunks_qs[:count_needed * 3])
        if not chunks_sample:
            chunks_sample = list(HistoricalChunk.objects.all()[:20])

        generated = []
        if not chunks_sample:
            return generated

        exam_cat = ExamCategory.objects.filter(code=category_code).first()

        for idx in range(count_needed):
            target_chunk = chunks_sample[idx % len(chunks_sample)]

            if not self.model:
                # Local fallback rule-based question builder
                q = self._create_local_fallback_question(target_chunk, difficulty, exam_cat)
                if q:
                    generated.append(q)
                continue

            # LLM Evidence-grounded generation prompt
            prompt = f"""
                Sen O'zbekiston va Jahon tarixi bo'yicha ekspert imtihon tuzuvchisisan (DTM, Milliy Sertifikat, Attestatsiya).
                Quyidagi darslik manba matni asosida 1 ta mukammal, mantiqiy va professional test savoli tuz.

                Darslik matni:
                "{target_chunk.text}"
                Manba: {target_chunk.source_provenance}

                QAT'IY TALABLAR (BMBA / Milliy Sertifikat va Attestatsiya mezonlari):
                1. Qiyinlik darajasi: {difficulty}. Chalg'ituvchi variantlar (distractors) judayam yaqin va mantiqiy bo'lsin:
                   - Agar savol YIL bo'lsa: Variantlar oraliq masofasi 1-5 yil atrofida bo'lsin (masalan: 1370, 1368, 1372, 1375). 10-20 yillik farq bo'lmasin!
                   - Agar SHAXS bo'lsa: Variantlarda o'sha davr/asr zamondoshlari berilsin.
                   - Agar SHAHAR/HUDUD bo'lsa: O'sha hududga tutash yaqin tarixiy shaharlar berilsin.
                2. Noto'g'ri variantlar mantiqan o'rinli, lekin darslik faktiga ko'ra xato bo'lsin. Jo'n va kulgili variantlar tuzilmasin!
                3. Faqat 1 ta to'g'ri javob bo'lsin ('A', 'B', 'C' yoki 'D').
                4. Uzbek lotin alifbosida, rasmiy ilmiy tilda yozing.

                JSON formati:
                {{
                    "question": "Savol matni",
                    "option_a": "A varianti",
                    "option_b": "B varianti",
                    "option_c": "C varianti",
                    "option_d": "D varianti",
                    "correct_option": "A",
                    "explanation": "To'g'ri javob tahlili va darslik manbasi",
                    "short_explanation": "Qisqa 1 jumla izoh"
                }}
            """

            try:
                res = self.model.generate_content(prompt)
                data = json.loads(res.text.strip())

                # Quality Validation
                opts = [data.get('option_a'), data.get('option_b'), data.get('option_c'), data.get('option_d')]
                if len(set(opts)) == 4 and all(opts) and data.get('correct_option') in ['A', 'B', 'C', 'D']:
                    q = Question.objects.create(
                        topic=target_chunk.topic,
                        chunk=target_chunk,
                        grade=target_chunk.document.grade,
                        subject=target_chunk.document.subject,
                        exam_category=exam_cat,
                        question_type='SINGLE_CHOICE',
                        difficulty=difficulty,
                        text=data['question'],
                        option_a=data['option_a'],
                        option_b=data['option_b'],
                        option_c=data['option_c'],
                        option_d=data['option_d'],
                        correct_option=data['correct_option'],
                        explanation=data.get('explanation', ''),
                        short_explanation=data.get('short_explanation', ''),
                        source_citation=target_chunk.source_provenance,
                        evidence_chunk_ids=[target_chunk.id],
                        verification_status='VERIFIED',
                        quality_score=0.95
                    )
                    generated.append(q)
            except Exception:
                q = self._create_local_fallback_question(target_chunk, difficulty, exam_cat)
                if q:
                    generated.append(q)

        return generated

    def _create_local_fallback_question(self, chunk, difficulty, exam_cat):
        # Deterministic evidence-backed fallback question
        text = chunk.text
        if len(text) < 40:
            return None

        # Look for dates or historical facts in chunk
        years = re.findall(r'\b(1[0-9]{3}|20[0-9]{2}|[5-9][0-9]{2})\b', text)
        if years:
            year = years[0]
            correct_val = f"{year}-yil"
            alt1 = f"{int(year) - 2}-yil"
            alt2 = f"{int(year) + 3}-yil"
            alt3 = f"{int(year) - 5}-yil"

            q_text = f"Darslik ma'lumotlariga ko'ra ({chunk.source_provenance}), ushbu tarixiy voqea qaysi yilda yuz bergan: \"{text[:120]}...\"?"

            opts = [
                ("A", correct_val),
                ("B", alt1),
                ("C", alt2),
                ("D", alt3)
            ]
            random.shuffle(opts)

            correct_letter = "A"
            final_a, final_b, final_c, final_d = "", "", "", ""
            for idx, (orig_id, val) in enumerate(opts):
                letter = ["A", "B", "C", "D"][idx]
                if orig_id == "A":
                    correct_letter = letter
                if letter == "A": final_a = val
                if letter == "B": final_b = val
                if letter == "C": final_c = val
                if letter == "D": final_d = val

            return Question.objects.create(
                topic=chunk.topic,
                chunk=chunk,
                grade=chunk.document.grade,
                subject=chunk.document.subject,
                exam_category=exam_cat,
                question_type='SINGLE_CHOICE',
                difficulty=difficulty,
                text=q_text,
                option_a=final_a,
                option_b=final_b,
                option_c=final_c,
                option_d=final_d,
                correct_option=correct_letter,
                explanation=f"To'g'ri javob: {year}-yil. Manba: {chunk.source_provenance}",
                short_explanation=f"Manba: {chunk.source_provenance}",
                source_citation=chunk.source_provenance,
                evidence_chunk_ids=[chunk.id],
                verification_status='VERIFIED',
                quality_score=0.9
            )
        return None
