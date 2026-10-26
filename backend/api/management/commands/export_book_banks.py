"""Export each book's question bank into its own JSON file.

Files land in backend/media/tests/json/:
    book_{grade}_{slug}.json     e.g. book_7_jahon.json, book_7_ozbekiston.json

Keeping one file per book keeps the download small: the Android client asks the
API only for the book the user opened (grade + subject + offset).
"""
from __future__ import annotations

import json
from pathlib import Path

from django.conf import settings
from django.core.management.base import BaseCommand

from api.models import Question
from api.serializers import QuestionSerializer

OUT_DIR = Path(settings.BASE_DIR) / "media" / "tests" / "json"


def slug(grade: int, subject: str) -> str:
    low = (subject or "").lower()
    if grade == 6:
        return f"book_{grade}_qadimgi"
    return f"book_{grade}_{'jahon' if 'jahon' in low else 'ozbekiston'}"


class Command(BaseCommand):
    help = "Write one JSON export per book (grade + subject)"

    def add_arguments(self, parser):
        parser.add_argument("--grade", type=int, default=0)
        parser.add_argument("--subject", default="")

    def handle(self, *args, **options):
        OUT_DIR.mkdir(parents=True, exist_ok=True)
        qs = Question.objects.filter(verification_status="VERIFIED")
        if options["grade"]:
            qs = qs.filter(grade=options["grade"])
        if options["subject"]:
            qs = qs.filter(subject__icontains=options["subject"])

        groups: dict[str, list[Question]] = {}
        for question in qs.order_by("id").iterator(chunk_size=2000):
            groups.setdefault(slug(question.grade, question.subject), []).append(question)

        for name, items in groups.items():
            payload = {
                "grade": items[0].grade,
                "subject": items[0].subject,
                "count": len(items),
                "questions": QuestionSerializer(items, many=True).data,
            }
            path = OUT_DIR / f"{name}.json"
            path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            self.stdout.write(f"{path.name}: {len(items)} questions ({path.stat().st_size // 1024} KB)")
