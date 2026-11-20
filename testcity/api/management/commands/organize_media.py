import os
import shutil
import json
from django.core.management.base import BaseCommand
from django.conf import settings
from api.models import Question

class Command(BaseCommand):
    help = "Reorganizes backend media files into clean 'books' and 'tests' folders and consolidates scattered grade test chunks."

    def handle(self, *args, **options):
        media_root = settings.MEDIA_ROOT
        
        # New directories
        books_pdf_dir = os.path.join(media_root, "books", "pdfs")
        books_json_dir = os.path.join(media_root, "books", "json")
        books_txt_dir = os.path.join(media_root, "books", "txt")
        tests_json_dir = os.path.join(media_root, "tests", "json")
        tests_txt_dir = os.path.join(media_root, "tests", "txt")

        for d in [books_pdf_dir, books_json_dir, books_txt_dir, tests_json_dir, tests_txt_dir]:
            os.makedirs(d, exist_ok=True)

        self.stdout.write("1. Reorganizing Books (PDFs, JSONs, TXTs)...")

        # Move PDFs
        old_pdf_dir = os.path.join(media_root, "pdfs")
        if os.path.exists(old_pdf_dir):
            for fname in os.listdir(old_pdf_dir):
                if fname.endswith(".pdf"):
                    src = os.path.join(old_pdf_dir, fname)
                    dst = os.path.join(books_pdf_dir, fname)
                    shutil.move(src, dst)
            self.stdout.write("   [+] PDF books moved to media/books/pdfs/")

        # Move Book JSONs and TXTs from exports
        exports_dir = os.path.join(media_root, "exports")
        old_json_dir = os.path.join(exports_dir, "json")
        old_txt_dir = os.path.join(exports_dir, "txt")

        book_json_files = [
            "6-sinf_Qadimgi_dunyo_tarixi.json", "7-sinf_Jahon_tarixi.json", "7-sinf_Ozbekiston_tarixi.json",
            "8-sinf_Jahon_tarixi.json", "8-sinf_Ozbekiston_tarixi.json", "9-sinf_Jahon_tarixi.json",
            "9-sinf_Ozbekiston_tarixi.json", "10-sinf_Jahon_tarixi.json", "10-sinf_Ozbekiston_tarixi.json",
            "11-sinf_Jahon_tarixi.json", "11-sinf_Ozbekiston_tarixi.json", "all_topics_export.json"
        ]

        if os.path.exists(old_json_dir):
            for fname in book_json_files:
                src = os.path.join(old_json_dir, fname)
                if os.path.exists(src):
                    dst = os.path.join(books_json_dir, fname)
                    shutil.copy2(src, dst)

        if os.path.exists(old_txt_dir):
            for fname in os.listdir(old_txt_dir):
                if not ("test" in fname or "100" in fname or "200" in fname or "600" in fname or "1100" in fname or "1200" in fname or "1500" in fname or "1600" in fname):
                    src = os.path.join(old_txt_dir, fname)
                    if os.path.isfile(src) and fname.endswith(".txt"):
                        dst = os.path.join(books_txt_dir, fname)
                        shutil.copy2(src, dst)

        self.stdout.write("   [+] Book JSON and TXT exports moved to media/books/")

        self.stdout.write("\n2. Consolidating and Reorganizing Tests...")

        # Consolidate JSON tests per grade from DB + JSON chunk files
        for grade in [6, 7, 8, 9, 10, 11]:
            questions_list = []
            seen_texts = set()

            # 1) First load questions from DB
            db_questions = Question.objects.filter(grade=grade)
            for q in db_questions:
                clean_text = q.text.strip()
                if clean_text not in seen_texts:
                    seen_texts.add(clean_text)
                    questions_list.append({
                        "id": str(q.id),
                        "grade_id": q.grade,
                        "grade": q.grade,
                        "subject": q.subject,
                        "subject_id": "uzbekiston" if "zbek" in (q.subject or "").lower() or grade == 6 else "jahon",
                        "type": q.question_type or "SINGLE_CHOICE",
                        "difficulty": q.difficulty or "EXAM",
                        "text": q.text,
                        "options": [
                            {"id": "a", "text": q.option_a, "isCorrect": q.correct_option == "A"},
                            {"id": "b", "text": q.option_b, "isCorrect": q.correct_option == "B"},
                            {"id": "c", "text": q.option_c, "isCorrect": q.correct_option == "C"},
                            {"id": "d", "text": q.option_d, "isCorrect": q.correct_option == "D"},
                        ],
                        "explanation": q.explanation or q.short_explanation or "",
                        "sourceReference": q.source_citation or ""
                    })

            # 2) Next load from grade-specific JSON files if available
            if os.path.exists(old_json_dir):
                for fname in os.listdir(old_json_dir):
                    if fname.endswith(".json"):
                        is_grade_file = False
                        if grade == 6 and ("grade6" in fname or "grade_6" in fname or "6-sinf" in fname):
                            is_grade_file = True
                        elif f"grade_{grade}" in fname or f"grade{grade}" in fname or f"questions_grade_{grade}" in fname or f"{grade}-sinf" in fname:
                            is_grade_file = True

                        if is_grade_file and "Qadimgi_dunyo_tarixi.json" not in fname and "Jahon_tarixi.json" not in fname and "Ozbekiston_tarixi.json" not in fname:
                            fpath = os.path.join(old_json_dir, fname)
                            try:
                                with open(fpath, "r", encoding="utf-8") as f:
                                    data = json.load(f)
                                items = data.get("questions", []) if isinstance(data, dict) else (data if isinstance(data, list) else [])
                                for item in items:
                                    txt = item.get("text", "").strip()
                                    if txt and txt not in seen_texts:
                                        seen_texts.add(txt)
                                        questions_list.append(item)
                            except Exception:
                                pass

            target_file = os.path.join(tests_json_dir, f"grade{grade}_all_tests.json")
            with open(target_file, "w", encoding="utf-8") as f:
                json.dump({
                    "status": "success",
                    "grade": grade,
                    "count": len(questions_list),
                    "questions": questions_list
                }, f, ensure_ascii=False, indent=2)

            self.stdout.write(f"   [+] {grade}-sinf testlari bir joyga jamlandi: {len(questions_list)} ta test -> grade{grade}_all_tests.json")

        # Copy all_questions.json and quadro_logic_tests.json if exist
        for special_fname in ["all_questions.json", "quadro_logic_tests.json"]:
            src = os.path.join(old_json_dir, special_fname)
            if os.path.exists(src):
                dst = os.path.join(tests_json_dir, special_fname)
                shutil.copy2(src, dst)

        # Consolidate TXT tests (especially for 6th grade)
        if os.path.exists(old_txt_dir):
            txt_lines = []
            for fname in sorted(os.listdir(old_txt_dir)):
                if "test" in fname and fname.endswith(".txt"):
                    fpath = os.path.join(old_txt_dir, fname)
                    try:
                        with open(fpath, "r", encoding="utf-8") as f:
                            content = f.read().strip()
                            if content:
                                txt_lines.append(f"=== {fname} ===\n" + content)
                    except Exception:
                        pass
            
            if txt_lines:
                consolidated_txt_file = os.path.join(tests_txt_dir, "grade6_all_tests.txt")
                with open(consolidated_txt_file, "w", encoding="utf-8") as f:
                    f.write("\n\n" + "="*50 + "\n\n".join(txt_lines))
                self.stdout.write(f"   [+] 6-sinf matnli testlari yaxlitlandi -> media/tests/txt/grade6_all_tests.txt")

        self.stdout.write("\n3. Cleaning up old redundant folders...")
        # Remove old pdfs folder if empty or moved
        if os.path.exists(old_pdf_dir):
            shutil.rmtree(old_pdf_dir, ignore_errors=True)

        # Remove old exports folder if moved
        if os.path.exists(exports_dir):
            shutil.rmtree(exports_dir, ignore_errors=True)

        self.stdout.write(self.style.SUCCESS("\n[SUCCESS] Media strukturasi muvaffaqiyatli tartibga solindi va birlashtirildi!"))
