import re
from django.core.management.base import BaseCommand
from django.db.models import Count
from api.models import Topic

# ---------------------------------------------------------------------------
# Strict heading classifiers. The goal: never expose ordinary paragraphs,
# page numbers, captions, dates, or RAG chunks as verified curriculum topics.
# ---------------------------------------------------------------------------

# Patterns that are ALWAYS false positives for a topic heading.
REJECT_PATTERNS = [
    # Pure year + dash + page/artifact, e.g. "2017. - 192 b.", "100129. Toshkent..."
    re.compile(r'^\d{3,4}\s*[.\-–—]\s*\d{0,4}\s*(?:b|bet|betlar)?\.?\s*$', re.IGNORECASE),
    re.compile(r'^\d{3,4}\s*[.\-–—]\s*\d{0,4}\b.*', re.IGNORECASE),  # "2017. - 192 b."
    # Figure / image captions, e.g. "124-rasm Sechjon Buyuk", "23-rasmlarga qarab"
    re.compile(r'^\d+\s*[-–—]?\s*rasm', re.IGNORECASE),
    re.compile(r'^\d+\s*[-–—]?\s*rasmlar', re.IGNORECASE),
    # Bare "N-mavzu" with no substantive title, e.g. "1-MAVZU", "5-mavzu", "13-mavzu"
    re.compile(r'^\d+\s*[-–—]?\s*mavzu\s*[.:]?\s*$', re.IGNORECASE),
    # Bare "N BOB", "III BOB", "I BOB." with no title
    re.compile(r'^(?:[IVXLCDM]+|\d+)\s*[-–—]?\s*BOB\s*[.:]?\s*$', re.IGNORECASE),
    # Decimal artifacts, e.g. "63.3(0)"
    re.compile(r'^\d+(?:\.\d+)+', re.IGNORECASE),
    # Just a year range or year + "yilda", e.g. "778-yilda Kàrl", "1395-yilda"
    re.compile(r'^\d{3,4}\s*[-–—]?\s*yil(?:da|lar|ga)?\b', re.IGNORECASE),
    # Isolated page/footer numbers
    re.compile(r'^\d+\s*$'),
    # Question fragments / homework prompts are not topics
    re.compile(r'(Nazorat savollari|Mustaqil ish|Uyga vazifa|O\'zingizni|Tasavvur qiling|Ayting-chi|O\'ylab ko\'ring|Sizningcha|Nima uchun|Qaysi|Qanday|Nega|Kim)', re.IGNORECASE),
]

# Patterns that are strong evidence of a real, substantive topic heading.
VALID_HEADING_PATTERNS = [
    # "N-mavzu: TITLE" / "N-MAVZU. TITLE"
    re.compile(r'^\d+\s*[-–—]?\s*mavzu\s*[.:]\s*.{4,}$', re.IGNORECASE),
    # "§ N TITLE" (section symbol with number and title)
    re.compile(r'^§\s*\d+\b.*[A-Za-zÀ-Ўà-ў].{3,}$'),
    # "N. Title" / "N-Title" / "N. Title text"
    re.compile(r'^\d+\s*[.\-–—]\s*[A-ZÀ-ЎA-Za-zà-ў].{3,}$'),
    # Uppercase title with meaningful words (but not pure "BOB")
    re.compile(r'^[A-ZÀ-Ў0-9\s\'’\-–—.,:«»()]{6,80}$'),
]

# Topic titles that represent fallback page ranges, never verified curriculum topics.
FALLBACK_PAGE_RANGE = re.compile(r'betlar$|betlari$|betlar\b', re.IGNORECASE)


def classify_topic_title(title):
    title = (title or '').strip()
    if not title:
        return 'REJECTED', 'empty_title'

    if FALLBACK_PAGE_RANGE.search(title):
        return 'NEEDS_CORRECTION', 'fallback_page_range'

    for pat in REJECT_PATTERNS:
        if pat.search(title):
            return 'REJECTED', pat.pattern[:40]

    for pat in VALID_HEADING_PATTERNS:
        if pat.match(title):
            return 'REVIEWED', 'valid_heading'

    return 'NEEDS_CORRECTION', 'ambiguous_heading'


class Command(BaseCommand):
    help = ('Repair topic classification: mark ordinary paragraphs, page numbers, '
            'captions, and date fragments as REJECTED; keep real headings as REVIEWED. '
            'Idempotent and safe to rerun.')

    def add_arguments(self, parser):
        parser.add_argument('--dry-run', action='store_true', help='Report changes without writing')

    def handle(self, *args, **options):
        dry_run = options['dry_run']

        before_counts = dict(
            Topic.objects.values('review_status').annotate(n=Count('id')).values_list('review_status', 'n')
        )

        stats = {'REVIEWED': 0, 'REJECTED': 0, 'NEEDS_CORRECTION': 0, 'UNREVIEWED': 0}
        changed = 0
        needs_review = []

        topics = Topic.objects.all().order_by('document_id', 'order')
        for t in topics.iterator():
            status, reason = classify_topic_title(t.title)
            stats[status] = stats.get(status, 0) + 1

            if t.review_status != status:
                changed += 1
                if not dry_run:
                    t.review_status = status
                    # Preserve machine-readable provenance of the reclassification.
                    t.summary = (t.summary or '') + f' [reclassified:{status}:{reason}]'
                    t.save(update_fields=['review_status', 'summary'])

            if status == 'NEEDS_CORRECTION':
                needs_review.append({'id': t.id, 'title': t.title[:80], 'document_id': t.document_id, 'grade': t.document.grade if t.document else None})

        self.stdout.write(self.style.MIGRATE_HEADING('=== Topic Classification Repair ==='))
        self.stdout.write(f'Before: {dict(before_counts)}')
        self.stdout.write(f'After (computed): {stats}')
        self.stdout.write(f'Records to update: {changed}')
        self.stdout.write(f'Needs review (kept as candidate): {len(needs_review)}')

        if dry_run:
            self.stdout.write(self.style.WARNING('DRY RUN — no changes written.'))
        else:
            self.stdout.write(self.style.SUCCESS('Repair applied successfully.'))
