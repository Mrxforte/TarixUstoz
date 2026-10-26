import os
import sys
import shutil
import re
from django.core.management.base import BaseCommand
from django.conf import settings
from api.models import Category, PdfBook
from api.ingestion_engine import TextbookIngestionPipeline

DEFAULT_PDF_DIRS = [
    os.path.join(settings.BASE_DIR, 'media', 'books', 'pdfs'),
    os.path.join(settings.BASE_DIR, 'media', 'pdfs'),
    r"C:\Users\Azamat\Desktop\Tarix darsliklari",
]

def normalize_textbook_info(filename: str):
    fname = filename.lower()

    # Determine grade
    grade = 6
    for g in range(6, 12):
        if f"{g}-sinf" in fname or f"{g} sinf" in fname or f"{g}_sinf" in fname:
            grade = g
            break

    # Determine subject
    if "jahon" in fname:
        subject = "Jahon tarixi"
    elif grade == 6:
        subject = "Qadimgi dunyo tarixi"
    else:
        subject = "O'zbekiston tarixi"

    # Canonical title
    if grade == 6:
        canonical_title = "6-sinf Tarix (Qadimgi dunyo tarixi)"
    else:
        canonical_title = f"{grade}-sinf {subject}"

    return grade, subject, canonical_title


class Command(BaseCommand):
    help = 'Ingest grade 6-11 history textbooks, perform page text extraction, chunking, and PostgreSQL indexing.'

    def add_arguments(self, parser):
        parser.add_argument('--book-id', type=int, help='Process specific PdfBook ID')
        parser.add_argument('--grade', type=int, help='Process all textbooks for a specific grade (6-11)')
        parser.add_argument('--dir', type=str, help='Custom directory containing PDF files')
        parser.add_argument('--force', action='store_true', help='Force reprocess existing books')
        parser.add_argument('--no-ocr', action='store_true', help='Disable OCR fallback')

    def handle(self, *args, **options):
        book_id = options.get('book_id')
        grade_filter = options.get('grade')
        custom_dir = options.get('dir')
        force = options.get('force', False)
        use_ocr = not options.get('no_ocr', False)

        self.stdout.write(self.style.MIGRATE_HEADING("=== Tarix Ustoz - Textbook Ingestion Pipeline ==="))

        if book_id:
            try:
                book = PdfBook.objects.get(id=book_id)
                self.stdout.write(f"Processing single textbook ID={book.id}: {book.title} ({book.grade}-sinf)")
                pipeline = TextbookIngestionPipeline(book.id)
                success = pipeline.process_pdf(force_reprocess=force, use_ocr=use_ocr)
                if success:
                    self.stdout.write(self.style.SUCCESS(f"Successfully processed ID={book.id}: {book.title}"))
                    sys.exit(0)
                else:
                    self.stderr.write(f"Failed processing ID={book.id}: {book.title}")
                    sys.exit(1)
            except PdfBook.DoesNotExist:
                self.stderr.write(f"PdfBook with ID={book_id} does not exist.")
                sys.exit(1)

        search_dirs = [custom_dir] if custom_dir else DEFAULT_PDF_DIRS
        pdf_dir = None
        for d in search_dirs:
            if d and os.path.exists(d) and len([f for f in os.listdir(d) if f.endswith('.pdf')]) > 0:
                pdf_dir = d
                break

        if not pdf_dir:
            self.stderr.write(f"ERROR: No PDF files found in directories: {search_dirs}")
            sys.exit(1)

        media_pdf_dir = os.path.join(settings.BASE_DIR, 'media', 'pdfs')
        os.makedirs(media_pdf_dir, exist_ok=True)

        pdf_files = [f for f in os.listdir(pdf_dir) if f.endswith('.pdf') and not f.startswith('~$')]
        self.stdout.write(f"Reading PDF textbooks from: {pdf_dir}")
        self.stdout.write(f"Found {len(pdf_files)} PDF files.\n")

        processed_books = set()
        processed_count = 0
        failed_count = 0

        for pdf_name in pdf_files:
            src_path = os.path.join(pdf_dir, pdf_name)
            grade, subject, canonical_title = normalize_textbook_info(pdf_name)

            if grade_filter and grade != grade_filter:
                continue

            # Deduplicate processing in single run
            spec_key = (grade, subject)
            if spec_key in processed_books:
                continue

            dest_filename = f"{grade}-sinf_{subject.replace(' ', '_').replace('\'', '')}.pdf"
            dest_path = os.path.join(media_pdf_dir, dest_filename)

            if src_path != dest_path:
                shutil.copy2(src_path, dest_path)

            cat_name = f"{grade}-sinf Tarix"
            category, _ = Category.objects.get_or_create(
                name=cat_name,
                defaults={'order': grade, 'description': f"{grade}-sinf tarix darsliklari va mavzulari"}
            )

            rel_pdf_path = f"pdfs/{dest_filename}"

            # Get or update unique book by grade & subject
            book = PdfBook.objects.filter(grade=grade, subject=subject).first()
            if not book:
                book = PdfBook.objects.create(
                    title=canonical_title,
                    category=category,
                    grade=grade,
                    subject=subject,
                    pdf_file=rel_pdf_path,
                    original_filename=pdf_name,
                    file_size=os.path.getsize(dest_path)
                )
            else:
                book.title = canonical_title
                book.category = category
                book.pdf_file = rel_pdf_path
                book.original_filename = pdf_name
                book.file_size = os.path.getsize(dest_path)
                book.save()

            processed_books.add(spec_key)

            self.stdout.write(f"Processing [{grade}-sinf | {subject}] {canonical_title}...")
            pipeline = TextbookIngestionPipeline(book.id)
            success = pipeline.process_pdf(force_reprocess=force, use_ocr=use_ocr)

            if success:
                processed_count += 1
                self.stdout.write(self.style.SUCCESS(f"  -> OK: {canonical_title} ({book.total_pages} pages)"))
            else:
                failed_count += 1
                self.stdout.write(self.style.ERROR(f"  -> FAILED: {canonical_title}"))

        # Clean up any leftover duplicate PdfBook records in DB
        valid_ids = [b.id for b in PdfBook.objects.filter(grade__in=range(6, 12))]
        unique_spec_ids = set()
        for b in PdfBook.objects.filter(id__in=valid_ids):
            key = (b.grade, b.subject)
            if key in unique_spec_ids:
                b.delete()
            else:
                unique_spec_ids.add(key)

        self.stdout.write(self.style.SUCCESS(
            f"\nIngestion finished! Processed: {processed_count}, Failed: {failed_count}"
        ))
