"""Rebuild the question database and textbook PDFs from the tracked archives.

The 1.1 million questions are stored in git as compressed per-book banks
(backend/content/banks/*.ndjson.xz + manifest.json) and the PDFs as optimized
files (backend/content/pdfs/). This command turns them back into a working
database, one book at a time (idempotent: up-to-date books are skipped).

Usage (after `python manage.py migrate`):
  python manage.py restore_content
  python manage.py restore_content --only 9-sinf_Jahon
  python manage.py restore_content --force        # re-import even if counts match
  python manage.py restore_content --no-pdfs
"""
from __future__ import annotations

import json
import shutil
from pathlib import Path

from django.conf import settings
from django.core.management import call_command
from django.core.management.base import BaseCommand, CommandError

from api.models import PdfBook, Question

CONTENT = Path(__file__).resolve().parents[3] / "content"


class Command(BaseCommand):
    help = "Restore all textbook question banks and PDFs from backend/content"

    def add_arguments(self, parser):
        parser.add_argument("--only", default="", help="substring of the book folder name")
        parser.add_argument("--force", action="store_true")
        parser.add_argument("--no-pdfs", action="store_true")

    def handle(self, *args, **options):
        manifest_path = CONTENT / "banks" / "manifest.json"
        if not manifest_path.exists():
            raise CommandError(f"missing {manifest_path}")
        books = json.loads(manifest_path.read_text(encoding="utf-8"))["books"]
        books = [b for b in books if options["only"] in b["folder"]]
        if not books:
            raise CommandError("no matching books in manifest")

        for entry in books:
            self._restore_questions(entry, options["force"])
            if not options["no_pdfs"]:
                self._restore_pdf(entry)

        total = Question.objects.count()
        self.stdout.write(self.style.SUCCESS(f"done. questions in database: {total}"))

        call_command("refresh_direction_counts")

    def _restore_questions(self, entry: dict, force: bool) -> None:
        grade, subject = entry["grade"], entry["subject"]
        current = Question.objects.filter(grade=grade, subject=subject).count()
        if current == entry["count"] and not force:
            self.stdout.write(f"{entry['folder']}: up to date ({current})")
            return
        self.stdout.write(f"{entry['folder']}: importing {entry['count']} (had {current})")
        call_command(
            "import_jahon7_bank",
            ndjson=str(CONTENT / "banks" / entry["archive"]),
            topics=str(CONTENT / "books" / entry["folder"] / "source" / "topics.json"),
            grade=grade,
            subject=subject,
            prefix=entry["prefix"],
            replace=True,
        )
        restored = Question.objects.filter(grade=grade, subject=subject).count()
        if restored != entry["count"]:
            raise CommandError(f"{entry['folder']}: expected {entry['count']}, got {restored}")

    def _restore_pdf(self, entry: dict) -> None:
        src = CONTENT / "pdfs" / f"{entry['folder']}.pdf"
        if not src.exists():
            self.stdout.write(self.style.WARNING(f"{entry['folder']}: no PDF in content/pdfs"))
            return
        dest_dir = Path(settings.MEDIA_ROOT) / "pdfs"
        dest_dir.mkdir(parents=True, exist_ok=True)
        dest = dest_dir / src.name
        if not dest.exists() or dest.stat().st_size != src.stat().st_size:
            shutil.copyfile(src, dest)
        book = PdfBook.objects.filter(grade=entry["grade"], subject=entry["subject"]).order_by("id").first()
        if book:
            book.pdf_file = f"pdfs/{src.name}"
            book.file_size = src.stat().st_size
            book.original_filename = book.original_filename or src.name
            book.save(update_fields=["pdf_file", "file_size", "original_filename"])
