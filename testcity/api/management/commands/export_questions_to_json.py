import os
import re
import json
from django.core.management.base import BaseCommand
from django.conf import settings
from api.models import Question, PdfBook

class Command(BaseCommand):
    help = "Bazadagi barcha testlarni HAR BIR KITOB UCHUN ALOHIDA JSON faylga eksport qilish"

    def handle(self, *args, **options):
        self.stdout.write("Testlarni har bir sinf bo'yicha yaxlitlangan JSON fayllarga eksport qilish boshlandi...")
        
        json_dir = getattr(settings, 'TESTS_JSON_DIR', os.path.join(settings.BASE_DIR, 'media', 'tests', 'json'))
        os.makedirs(json_dir, exist_ok=True)

        questions = Question.objects.filter(verification_status='VERIFIED').select_related('topic', 'exam_category')
        total_count = questions.count()
        self.stdout.write(f"Eksport qilinadigan jami testlar soni: {total_count}")

        by_book = {}

        for q in questions.iterator(chunk_size=2000):
            grade = q.grade
            subj_raw = (q.subject or "").strip()
            
            if "jahon" in subj_raw.lower():
                subj_clean = "Jahon_tarixi"
                subj_id = "jahon"
            elif grade == 6:
                subj_clean = "Qadimgi_dunyo_tarixi"
                subj_id = "uzbekiston"
            else:
                subj_clean = "Ozbekiston_tarixi"
                subj_id = "uzbekiston"

            book_filename = f"{grade}-sinf_{subj_clean}.json"

            q_data = {
                "id": q.id,
                "topic_id": q.topic_id,
                "grade_id": grade,
                "grade": grade,
                "subject": q.subject,
                "subject_id": subj_id,
                "question_type": q.question_type,
                "difficulty": q.difficulty,
                "category_type": q.exam_category.code if q.exam_category else "ALL",
                "text": q.text,
                "option_a": q.option_a,
                "option_b": q.option_b,
                "option_c": q.option_c,
                "option_d": q.option_d,
                "correct_option": q.correct_option,
                "explanation": q.explanation or q.short_explanation or "",
                "short_explanation": q.short_explanation or "",
                "source_citation": q.source_citation or "",
                "is_official": q.is_official_sample
            }

            if book_filename not in by_book:
                by_book[book_filename] = []
            by_book[book_filename].append(q_data)

        # Save each book into its own JSON file
        for filename, items in by_book.items():
            file_path = os.path.join(json_dir, filename)
            with open(file_path, "w", encoding="utf-8") as f:
                json.dump(items, f, ensure_ascii=False, indent=2)
            self.stdout.write(f"  [+] Kitob: {filename} -> {len(items)} ta test saqlandi.")

        self.stdout.write(self.style.SUCCESS(f"\nBarcha kitoblar bo'yicha alohida JSON fayllar {json_dir} papkasida yaratildi!"))
