import hashlib
import re
from django.utils import timezone
from api.models import (
    PdfBook,
    DocumentPage,
    HistoricalChapter,
    Topic,
    CurriculumLearningObjective,
    HistoricalChunk,
    HistoricalEntity,
    Category,
)

DATE_REGEX = r'\b(\d{1,4})(-yil|-yillar|-asr|-asrlarda| - \d{1,4}|-yilgi)?\b'

class CurriculumInventoryBuilder:
    @staticmethod
    def build_complete_inventory():
        books = PdfBook.objects.all()
        created_topics_count = 0
        created_objectives_count = 0

        for book in books:
            category_name = f"{book.grade}-sinf {book.subject}"
            category, _ = Category.objects.get_or_create(
                name=category_name,
                defaults={'order': book.grade}
            )

            chapters = HistoricalChapter.objects.filter(document=book).order_by('number')
            if not chapters.exists():
                # Default chapter if none extracted
                chapter, _ = HistoricalChapter.objects.get_or_create(
                    document=book,
                    number=1,
                    defaults={
                        'title': f"{book.title} - Umumiy mavzular",
                        'start_page': 1,
                        'end_page': max(book.total_pages, 1)
                    }
                )
                chapters = [chapter]

            pages = DocumentPage.objects.filter(document=book).order_by('page_number')
            has_failed_pages = pages.filter(has_error=True).exists() or not pages.exists()

            for chapter in chapters:
                chapter_pages = pages.filter(
                    page_number__gte=chapter.start_page,
                    page_number__lte=chapter.end_page
                )

                combined_text = "\n".join([p.raw_text for p in chapter_pages if p.raw_text])
                content_hash = hashlib.sha256(combined_text.encode('utf-8')).hexdigest() if combined_text else ""

                # Compute extraction confidence
                total_expected_pages = max((chapter.end_page - chapter.start_page + 1), 1)
                actual_pages_found = chapter_pages.count()
                failed_pages_count = chapter_pages.filter(has_error=True).count()

                if actual_pages_found == 0:
                    confidence = 0.0
                else:
                    confidence = max(0.0, (actual_pages_found - failed_pages_count) / total_expected_pages)

                # Deduce topics or sections from heading patterns in chapter text
                headings = re.findall(r'(\d+[\.-]\s*[A-ZÀ-Ўa-zà-ў\s]{5,100})', combined_text)
                if not headings:
                    headings = [chapter.title]

                for idx, heading in enumerate(headings[:10]): # Limit per chapter
                    clean_title = heading.strip()
                    topic, created = Topic.objects.get_or_create(
                        document=book,
                        chapter=chapter,
                        title=clean_title[:255],
                        defaults={
                            'category': category,
                            'canonical_title': clean_title[:255],
                            'content': combined_text[:5000],
                            'order': idx + 1,
                            'start_page': chapter.start_page,
                            'end_page': chapter.end_page,
                            'content_fingerprint': content_hash,
                            'extraction_confidence': confidence,
                            'review_status': 'UNREVIEWED',
                            'coverage_status': 'extraction_incomplete' if (confidence < 0.8 or has_failed_pages) else 'awaiting_topic_review',
                        }
                    )

                    if created:
                        created_topics_count += 1

                    # Extract dates & entities for learning objectives
                    dates_found = re.findall(DATE_REGEX, combined_text)
                    date_list = [d[0] for d in dates_found[:5]]

                    # Create standard learning objectives
                    obj_desc = f"{topic.title} mavzusi bo'yicha asosiy tarixiy voqealar, shaxslar va sanalarni bilish."
                    objective, obj_created = CurriculumLearningObjective.objects.get_or_create(
                        topic=topic,
                        code=f"{book.grade}-{topic.id}-LO1",
                        defaults={
                            'description': obj_desc,
                            'cognitive_level': 'UNDERSTANDING',
                            'key_dates': date_list,
                            'is_assessed': True,
                        }
                    )
                    if obj_created:
                        created_objectives_count += 1

                    # Link existing or create chunks for this topic
                    chunks = HistoricalChunk.objects.filter(document=book, page__page_number__gte=topic.start_page, page__page_number__lte=topic.end_page)
                    chunks.update(topic=topic)

        return {
            'created_topics': created_topics_count,
            'created_objectives': created_objectives_count,
            'total_books': books.count(),
        }
