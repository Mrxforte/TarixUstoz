"""Import one textbook question bank (100 000 items) into this backend.

Source: a bank archive (backend/content/banks/<book>.ndjson.xz) or a plain
.ndjson produced by the content pipeline; facts are validated against the
textbook. Use `manage.py restore_content` to import every book at once.

Usage:
  python manage.py import_jahon7_bank --replace
  python manage.py import_jahon7_bank --ndjson <path> --topics <path> --grade N --subject S
"""
from __future__ import annotations

import json
import lzma
import re
from pathlib import Path

from django.core.management.base import BaseCommand
from django.db import transaction

from api.models import Category, PdfBook, Question, Topic
from api.directions import is_war_question, direction_for_combination

CONTENT = Path(__file__).resolve().parents[3] / "content"
DEFAULT_NDJSON = CONTENT / "banks" / "7-sinf_Jahon_tarixi.ndjson.xz"
DEFAULT_TOPICS = CONTENT / "books" / "7-sinf_Jahon_tarixi" / "source" / "topics.json"

GRADE = 7
SUBJECT = "Jahon tarixi"
BATCH = 2000

TYPE_MAP = {
    "TRUE_FALSE": "TRUE_FALSE",
    "CHRONOLOGY": "CHRONOLOGICAL",
    "MATCHING": "MATCHING",
}
DIFFICULTY_MAP = {"EASY": "EASY", "MEDIUM": "MEDIUM", "HARD": "HARD", "EXPERT": "HARD"}

CATEGORY_MAP = {
    "year": {"DATE_TO_EVENT", "EVENT_TO_DATE", "CHRONOLOGY", "NUMBER_TO_MEANING"},
    "person": {"PERSON_TO_ACTIVITY", "ACTIVITY_TO_PERSON", "WHO_WAS_FIRST"},
    "city": {"MAP_BASED", "PLACE_TO_EVENT"},
}


def app_category(combination: str) -> str:
    """Map a combination type to the Android QuestionCategory bucket."""
    for name, values in CATEGORY_MAP.items():
        if combination in values:
            return name.upper()
    return "EVENT"


class Command(BaseCommand):
    help = "Import the generated 7-sinf Jahon tarixi bank (idempotent by content_hash)"

    def add_arguments(self, parser):
        parser.add_argument("--ndjson", default=str(DEFAULT_NDJSON))
        parser.add_argument("--topics", default=str(DEFAULT_TOPICS))
        parser.add_argument("--grade", type=int, default=GRADE)
        parser.add_argument("--subject", default=SUBJECT)
        parser.add_argument("--prefix", default="jh7")
        parser.add_argument("--replace", action="store_true")

    def handle(self, *args, **options):
        global GRADE, SUBJECT
        GRADE = options["grade"]
        SUBJECT = options["subject"]
        ndjson = Path(options["ndjson"])
        if not ndjson.exists():
            self.stderr.write(f"missing {ndjson}")
            return

        book = PdfBook.objects.filter(grade=GRADE, subject=SUBJECT).order_by("id").first()
        if not book:
            book = PdfBook.objects.create(
                title=f"{GRADE}-sinf {SUBJECT}", grade=GRADE, subject=SUBJECT,
            )
        category, _ = Category.objects.get_or_create(
            name=f"{GRADE}-sinf {SUBJECT}", defaults={"order": GRADE}
        )

        topics_by_number = self._sync_topics(book, category, Path(options["topics"]))
        self.stdout.write(f"topics: {len(topics_by_number)} (book {book.id})")

        if options["replace"]:
            deleted, _ = Question.objects.filter(grade=GRADE, subject=SUBJECT).delete()
            self.stdout.write(f"deleted {deleted} existing grade-7 Jahon questions")

        existing = set(
            Question.objects.filter(grade=GRADE, subject=SUBJECT)
            .exclude(content_hash="")
            .values_list("content_hash", flat=True)
        )
        self.stdout.write(f"existing hashes: {len(existing)}")

        batch: list[Question] = []
        inserted, skipped = 0, 0

        def flush():
            nonlocal inserted
            if batch:
                Question.objects.bulk_create(batch, batch_size=1000)
                inserted += len(batch)
                batch.clear()

        with transaction.atomic():
            with (lzma.open(ndjson, "rt", encoding="utf-8") if ndjson.suffix == ".xz"
                  else ndjson.open(encoding="utf-8")) as fh:
                for line in fh:
                    if not line.strip():
                        continue
                    row = json.loads(line)
                    ext = row["external_id"]
                    if ext in existing:
                        skipped += 1
                        continue
                    existing.add(ext)
                    batch.append(self._to_question(row, topics_by_number.get(row["topic_number"])))
                    if len(batch) >= BATCH:
                        flush()
            flush()

        total = Question.objects.filter(grade=GRADE, subject=SUBJECT).count()
        self.stdout.write(self.style.SUCCESS(
            f"inserted {inserted}, skipped {skipped}, grade-7 Jahon total {total}"
        ))

    # ------------------------------------------------------------------ helpers
    def _sync_topics(self, book: PdfBook, category: Category, topics_file: Path) -> dict[int, Topic]:
        """Attach the 44 printed paragraphs of the textbook to clean Topic rows."""
        toc = json.loads(topics_file.read_text(encoding="utf-8"))
        existing: dict[int, Topic] = {}
        for topic in Topic.objects.filter(document=book).order_by("id"):
            if topic.order and 1 <= topic.order <= 60 and topic.order not in existing:
                existing[topic.order] = topic
                continue
            match = re.match(r"\s*(\d{1,2})\s*-\s*[§S]", topic.title or "")
            if match and 1 <= int(match.group(1)) <= 60:
                existing.setdefault(int(match.group(1)), topic)

        result: dict[int, Topic] = {}
        for item in toc:
            number = int(item["number"])
            title = f"{number}-§. {item['title']}"
            topic = existing.get(number)
            if topic is None:
                topic = Topic.objects.create(
                    category=category,
                    document=book,
                    title=title,
                    canonical_title=item["title"],
                    order=number,
                    start_page=item.get("book_page", 1),
                    end_page=item.get("book_page", 1),
                    review_status="REVIEWED",
                    coverage_status="covered",
                )
            else:
                topic.title = title
                topic.canonical_title = item["title"]
                topic.order = number
                topic.start_page = item.get("book_page", topic.start_page)
                topic.end_page = item.get("book_page", topic.end_page)
                topic.category = topic.category or category
                topic.save(update_fields=[
                    "title", "canonical_title", "order", "start_page", "end_page", "category",
                ])
            result[number] = topic
        return result

    def _to_question(self, row: dict, topic: Topic | None) -> Question:
        combination = row["question_type"]
        explanation = row.get("explanation", "")
        return Question(
            topic=topic,
            grade=GRADE,
            subject=SUBJECT,
            question_type=TYPE_MAP.get(combination, "SINGLE_CHOICE"),
            difficulty=DIFFICULTY_MAP.get(row.get("difficulty", "MEDIUM"), "MEDIUM"),
            text=row["text"],
            option_a=row["option_a"],
            option_b=row["option_b"],
            option_c=row["option_c"],
            option_d=row["option_d"],
            correct_option=row["correct_option"],
            explanation=explanation,
            short_explanation=explanation[:255],
            source_citation=f"{SUBJECT}, {row.get('source_page', 0)}-bet",
            is_generated_practice=True,
            generation_model="TarixUstoz-FactBank",
            generation_config={"combination": combination, "external_id": row["external_id"]},
            verification_status="VERIFIED",
            quality_score=1.0,
            is_war=is_war_question(
                row["text"], row["option_a"], row["option_b"], row["option_c"], row["option_d"]
            ),
            direction_code=direction_for_combination(combination),
            tags=[combination, "bank", f"g{GRADE}"],
            content_hash=row["external_id"],
        )
