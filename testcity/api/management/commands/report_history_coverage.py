from django.core.management.base import BaseCommand
from api.models import (
    PdfBook, DocumentPage, HistoricalChunk,
    HistoricalChapter, Topic, HistoricalEntity, HistoricalRelationship
)

class Command(BaseCommand):
    help = 'Report historical document processing, chunking, and knowledge graph coverage.'

    def handle(self, *args, **options):
        self.stdout.write(self.style.MIGRATE_HEADING("=== Tarix Ustoz - Historical Knowledge Engine Coverage Report ==="))

        books = PdfBook.objects.all().order_by('grade', 'subject')
        total_pages = DocumentPage.objects.count()
        total_chunks = HistoricalChunk.objects.count()
        total_topics = Topic.objects.count()
        total_entities = HistoricalEntity.objects.count()
        total_relationships = HistoricalRelationship.objects.count()

        self.stdout.write(f"Total Textbooks Ingested : {books.count()}")
        self.stdout.write(f"Total Extracted Pages    : {total_pages}")
        self.stdout.write(f"Total Semantic Chunks    : {total_chunks}")
        self.stdout.write(f"Total Structured Topics  : {total_topics}")
        self.stdout.write(f"Total Historical Entities: {total_entities}")
        self.stdout.write(f"Total Knowledge Graph Rel: {total_relationships}\n")

        self.stdout.write("-" * 70)
        self.stdout.write(f"{'Sinf':<6} | {'Fan':<25} | {'Betlar':<8} | {'Chunklar':<8} | {'Mavzular':<8}")
        self.stdout.write("-" * 70)

        for b in books:
            p_count = DocumentPage.objects.filter(document=b).count()
            c_count = HistoricalChunk.objects.filter(document=b).count()
            t_count = Topic.objects.filter(document=b).count()
            self.stdout.write(f"{b.grade:<6} | {b.subject:<25} | {p_count:<8} | {c_count:<8} | {t_count:<8}")

        self.stdout.write("-" * 70)
        self.stdout.write(self.style.SUCCESS("\nCoverage Report Generation Complete!"))
