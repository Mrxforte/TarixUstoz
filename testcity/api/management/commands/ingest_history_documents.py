from django.core.management.base import BaseCommand
from django.core.management import call_command

class Command(BaseCommand):
    help = 'Alias for ingest_textbooks: Ingest history textbook PDFs, extract text, chunk, and index into DB.'

    def add_arguments(self, parser):
        parser.add_argument('--book-id', type=int, help='Process specific PdfBook ID')
        parser.add_argument('--grade', type=int, help='Process all textbooks for a specific grade')
        parser.add_argument('--dir', type=str, help='Custom directory containing PDF files')
        parser.add_argument('--force', action='store_true', help='Force reprocess existing books')
        parser.add_argument('--no-ocr', action='store_true', help='Disable OCR fallback')

    def handle(self, *args, **options):
        call_command('ingest_textbooks', **options)
