import os
import sys
import json
from django.core.management.base import BaseCommand
from django.conf import settings
from django.utils import timezone
from api.models import (
    PdfBook, DocumentPage, HistoricalChapter, Topic,
    HistoricalChunk, HistoricalEntity, EntityMention, HistoricalRelationship, Question
)

EXPECTED_TEXTBOOK_SPECS = [
    {"grade": 6, "subject": "Qadimgi dunyo tarixi", "title": "6-Sinf Tarix"},
    {"grade": 7, "subject": "O'zbekiston tarixi", "title": "7-Sinf O'zbekiston tarixi"},
    {"grade": 7, "subject": "Jahon tarixi", "title": "7-Sinf Jahon tarixi"},
    {"grade": 8, "subject": "O'zbekiston tarixi", "title": "8-Sinf O'zbekiston tarixi"},
    {"grade": 8, "subject": "Jahon tarixi", "title": "8-Sinf Jahon tarixi"},
    {"grade": 9, "subject": "O'zbekiston tarixi", "title": "9-Sinf O'zbekiston tarixi"},
    {"grade": 9, "subject": "Jahon tarixi", "title": "9-Sinf Jahon tarixi"},
    {"grade": 10, "subject": "O'zbekiston tarixi", "title": "10-Sinf O'zbekiston tarixi"},
    {"grade": 10, "subject": "Jahon tarixi", "title": "10-Sinf Jahon tarixi"},
    {"grade": 11, "subject": "O'zbekiston tarixi", "title": "11-Sinf O'zbekiston tarixi"},
    {"grade": 11, "subject": "Jahon tarixi", "title": "11-Sinf Jahon tarixi"},
]

class Command(BaseCommand):
    help = 'Comprehensive audit and coverage verification of textbook ingestion, exports, DB records, and backup status.'

    def add_arguments(self, parser):
        parser.add_argument('--json-output', type=str, help='Output path for machine-readable JSON audit report')

    def handle(self, *args, **options):
        json_output_path = options.get('json_output')
        if not json_output_path:
            exports_dir = getattr(settings, 'TEXTBOOK_EXPORTS_DIR', os.path.join(settings.MEDIA_ROOT, 'exports'))
            os.makedirs(exports_dir, exist_ok=True)
            json_output_path = os.path.join(exports_dir, 'audit_report.json')

        self.stdout.write(self.style.MIGRATE_HEADING("=== Tarix Ustoz - Textbook Comprehensive Audit Report ==="))

        all_books = list(PdfBook.objects.all())
        total_discovered = len(all_books)
        expected_total = len(EXPECTED_TEXTBOOK_SPECS)

        # PDF processing breakdown
        processed_books = [b for b in all_books if b.is_processed or b.processing_status == 'COMPLETED']
        partial_books = [b for b in all_books if b.processing_status == 'PARTIAL']
        failed_books = [b for b in all_books if b.processing_status == 'FAILED']
        pending_books = [b for b in all_books if b.processing_status == 'PENDING']

        # Page statistics
        total_pages = DocumentPage.objects.count()
        native_pages = DocumentPage.objects.filter(extraction_method='NATIVE').count()
        ocr_pages = DocumentPage.objects.filter(is_ocr_used=True).count()
        failed_pages = DocumentPage.objects.filter(has_error=True).count()

        # Export audit
        txt_exported = sum(1 for b in all_books if b.txt_export_path and os.path.exists(b.txt_export_path) and b.txt_export_size > 0)
        json_exported = sum(1 for b in all_books if b.json_export_path and os.path.exists(b.json_export_path) and b.json_export_size > 0)
        jsonl_exported = sum(1 for b in all_books if b.jsonl_export_path and os.path.exists(b.jsonl_export_path) and b.jsonl_export_size > 0)

        # Database record counts
        db_counts = {
            'PdfBook': total_discovered,
            'DocumentPage': total_pages,
            'HistoricalChapter': HistoricalChapter.objects.count(),
            'Topic': Topic.objects.count(),
            'HistoricalChunk': HistoricalChunk.objects.count(),
            'HistoricalEntity': HistoricalEntity.objects.count(),
            'EntityMention': EntityMention.objects.count(),
            'HistoricalRelationship': HistoricalRelationship.objects.count(),
            'Question': Question.objects.count()
        }

        # Duplicate detection
        hashes = [b.file_hash for b in all_books if b.file_hash]
        duplicate_hashes_count = len(hashes) - len(set(hashes))

        # Missing coverage inspection
        missing_grades_subjects = []
        for spec in EXPECTED_TEXTBOOK_SPECS:
            found = False
            for b in all_books:
                if b.grade == spec['grade'] and spec['subject'].lower() in b.subject.lower():
                    found = True
                    break
            if not found:
                missing_grades_subjects.append(f"Grade {spec['grade']} {spec['subject']}")

        # Page gaps detection
        missing_page_ranges = []
        for b in all_books:
            pages = list(DocumentPage.objects.filter(document=b).values_list('page_number', flat=True))
            if pages:
                expected_set = set(range(1, b.total_pages + 1))
                actual_set = set(pages)
                missing = expected_set - actual_set
                if missing:
                    missing_page_ranges.append({
                        'book_id': b.id,
                        'title': b.title,
                        'missing_page_numbers': sorted(list(missing))
                    })

        # Check SQL backups directory
        backup_dir = getattr(settings, 'SQL_BACKUPS_DIR', os.path.join(settings.MEDIA_ROOT, 'backups', 'sql'))
        sql_backups = []
        if os.path.exists(backup_dir):
            sql_backups = [f for f in os.listdir(backup_dir) if f.endswith('.sql')]

        has_sql_backup = len(sql_backups) > 0

        # Calculate audit completion score
        score_checks = [
            total_discovered >= expected_total,
            len(processed_books) >= expected_total,
            failed_books == 0,
            failed_pages == 0,
            txt_exported >= expected_total,
            json_exported >= expected_total,
            jsonl_exported >= expected_total,
            duplicate_hashes_count == 0,
            len(missing_grades_subjects) == 0,
            has_sql_backup
        ]
        completion_percentage = round((sum(1 for c in score_checks if c) / len(score_checks)) * 100.0, 1)

        per_book_report = []
        for b in all_books:
            per_book_report.append({
                'id': b.id,
                'title': b.title,
                'grade': b.grade,
                'subject': b.subject,
                'original_filename': b.original_filename,
                'file_hash': b.file_hash,
                'total_pages': b.total_pages,
                'processing_status': b.processing_status,
                'is_processed': b.is_processed,
                'exports': {
                    'txt_path': b.txt_export_path,
                    'txt_exists': bool(b.txt_export_path and os.path.exists(b.txt_export_path)),
                    'txt_size_bytes': b.txt_export_size,
                    'json_path': b.json_export_path,
                    'json_exists': bool(b.json_export_path and os.path.exists(b.json_export_path)),
                    'json_size_bytes': b.json_export_size,
                    'jsonl_path': b.jsonl_export_path,
                    'jsonl_exists': bool(b.jsonl_export_path and os.path.exists(b.jsonl_export_path)),
                    'jsonl_size_bytes': b.jsonl_export_size,
                }
            })

        audit_summary = {
            'timestamp': timezone.now().isoformat(),
            'audit_completion_percentage': completion_percentage,
            'is_fully_compliant': completion_percentage == 100.0,
            'textbooks_summary': {
                'expected_total': expected_total,
                'total_discovered': total_discovered,
                'fully_processed': len(processed_books),
                'partially_processed': len(partial_books),
                'failed': len(failed_books),
                'pending': len(pending_books),
            },
            'page_extraction_summary': {
                'total_pages': total_pages,
                'native_extracted_pages': native_pages,
                'ocr_extracted_pages': ocr_pages,
                'failed_pages': failed_pages,
            },
            'exports_summary': {
                'txt_exports_count': txt_exported,
                'json_exports_count': json_exported,
                'jsonl_chunk_exports_count': jsonl_exported,
            },
            'database_counts': db_counts,
            'data_integrity': {
                'duplicate_file_hashes': duplicate_hashes_count,
                'missing_expected_grades_subjects': missing_grades_subjects,
                'missing_page_ranges': missing_page_ranges,
            },
            'sql_backup': {
                'backup_files_count': len(sql_backups),
                'backup_files': sql_backups,
                'status': 'AVAILABLE' if has_sql_backup else 'MISSING'
            },
            'textbooks': per_book_report
        }

        # Write machine-readable JSON
        with open(json_output_path, 'w', encoding='utf-8') as f:
            json.dump(audit_summary, f, ensure_ascii=False, indent=2)

        # Console Summary
        self.stdout.write(f"\nDiscovered Textbooks: {total_discovered}/{expected_total}")
        self.stdout.write(f"Processed: {len(processed_books)} | Partial: {len(partial_books)} | Failed: {len(failed_books)}")
        self.stdout.write(f"Total Pages: {total_pages} (Native: {native_pages}, OCR: {ocr_pages}, Failed: {failed_pages})")
        self.stdout.write(f"Exports Status: TXT={txt_exported}/{total_discovered}, JSON={json_exported}/{total_discovered}, JSONL={jsonl_exported}/{total_discovered}")
        self.stdout.write(f"Database Chunks: {db_counts['HistoricalChunk']} | Entities: {db_counts['HistoricalEntity']} | Topics: {db_counts['Topic']}")
        self.stdout.write(f"SQL Backups Available: {len(sql_backups)}")

        if missing_grades_subjects:
            self.stdout.write(self.style.WARNING(f"Missing Grades/Subjects: {missing_grades_subjects}"))

        self.stdout.write(self.style.MIGRATE_LABEL(f"\nAudit Completion Score: {completion_percentage}%"))
        self.stdout.write(self.style.SUCCESS(f"Audit report saved to: {json_output_path}\n"))

        if completion_percentage < 100.0:
            self.stderr.write("AUDIT WARNING: Audit requirements incomplete or pending.")
