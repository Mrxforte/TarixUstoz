"""Refresh the materialised per-direction question counts (api.DirectionCount).

The app reads these numbers to render the "Yo'nalishlar bo'yicha testlar" cards;
running this command after importing questions keeps them exact.

Usage (from backend/):
  python manage.py refresh_direction_counts
  python manage.py refresh_direction_counts --grade 7 --subject "Jahon tarixi"
"""
from django.core.management.base import BaseCommand
from django.db.models import Sum

from api.directions import DIRECTION_TITLES, book_counts, refresh_counts
from api.models import DirectionCount, Question


class Command(BaseCommand):
    help = "Yo'nalishlar bo'yicha savollar sonini qayta hisoblaydi va keshlaydi"

    def add_arguments(self, parser):
        parser.add_argument("--grade", type=int, default=0)
        parser.add_argument("--subject", default="")

    def handle(self, *args, **options):
        grade, subject = options["grade"], options["subject"]
        if grade or subject:
            counts = book_counts(grade, subject)
            for code, count in counts.items():
                DirectionCount.objects.update_or_create(
                    grade=grade, subject=subject, code=code, defaults={"count": count}
                )
            written = len(counts)
        else:
            written = refresh_counts()

        self.stdout.write(f"rows written: {written}")
        totals = dict(
            DirectionCount.objects.values_list("code").annotate(total=Sum("count"))
        )
        for code, title in DIRECTION_TITLES.items():
            self.stdout.write(f"  {title}: {int(totals.get(code, 0))}")
        self.stdout.write(self.style.SUCCESS(f"savollar bazada: {Question.objects.count()}"))
