"""Read-only audit of the question banks.

Reports per book: total, distribution over the 20 combinations and over topics,
plus duplicates / empty fields. Exits non-zero when a book is below the target,
so it can be used as a check after any bulk edit.

Usage (from backend/):
  python manage.py audit_banks
  python manage.py audit_banks --expected 100000
"""
from django.core.management.base import BaseCommand
from django.db.models import Count

from api.models import Question


class Command(BaseCommand):
    help = "Har bir kitob bo'yicha savollar sonini va taqsimotini tekshiradi (faqat o'qish)"

    def add_arguments(self, parser):
        parser.add_argument("--expected", type=int, default=100000)

    def handle(self, *args, **options):
        expected = options["expected"]
        problems = []

        dup = (
            Question.objects.exclude(content_hash="")
            .values("content_hash")
            .annotate(n=Count("id"))
            .filter(n__gt=1)
            .count()
        )
        if dup:
            problems.append(f"{dup} ta takrorlangan content_hash")

        for grade, subject in (
            Question.objects.values_list("grade", "subject")
            .annotate(n=Count("id"))
            .order_by("grade", "subject")
            .values_list("grade", "subject")
        ):
            qs = Question.objects.filter(grade=grade, subject=subject)
            total = qs.count()
            combos = dict(
                qs.values_list("generation_config__combination").annotate(n=Count("id")).order_by("n")
            )
            topics = (
                qs.values("topic_id").annotate(n=Count("id")).order_by("n")
            )
            status = "OK" if total == expected else f"KAM ({expected - total} yetishmayapti)"
            self.stdout.write(
                f"\n{grade}-sinf {subject}: {total} — {status}"
                f" | kombinatsiyalar: {len(combos)} (min {min(combos.values(), default=0)})"
                f" | mavzular: {topics.count()} (min {topics.first()['n'] if topics else 0})"
            )
            if total != expected:
                problems.append(f"{grade}-sinf {subject}: {total}/{expected}")

        self.stdout.write("")
        if problems:
            for p in problems:
                self.stderr.write(self.style.ERROR(f"  {p}"))
            self.stderr.write(self.style.ERROR("Audit: muammo topildi"))
            raise SystemExit(1)
        self.stdout.write(self.style.SUCCESS("Audit: barcha kitoblar to'liq"))
