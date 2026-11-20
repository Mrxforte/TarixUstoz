import os
import re
import hashlib
import fitz  # PyMuPDF
from PIL import Image
import io
from django.utils import timezone
from django.db import transaction
from api.models import (
    PdfBook, DocumentPage, HistoricalChapter, Topic,
    HistoricalChunk, HistoricalEntity, HistoricalEntityAlias,
    EntityMention, HistoricalRelationship, IngestionAuditLog
)

try:
    import pytesseract
    HAS_PYTESSERACT = True
except ImportError:
    HAS_PYTESSERACT = False


class UzbekTextNormalizer:
    @staticmethod
    def normalize_apostrophes(text: str) -> str:
        if not text:
            return ""
        # Standardize typographic apostrophes
        text = re.sub(r"[’‘'`ʻ]", "'", text)
        return text

    @staticmethod
    def latin_to_cyrillic(text: str) -> str:
        text = UzbekTextNormalizer.normalize_apostrophes(text)
        replacements = [
            ("O'g'li", "Ўғли"), ("o'g'li", "ўғли"),
            ("G'g'a", "Ғғa"), ("g'g'a", "ғғa"),
            ("SH", "Ш"), ("Sh", "Ш"), ("sh", "ш"),
            ("CH", "Ч"), ("Ch", "Ч"), ("ch", "ч"),
            ("O'", "Ў"), ("o'", "ў"),
            ("G'", "Ғ"), ("g'", "ғ"),
            ("YE", "Е"), ("Ye", "Е"), ("ye", "е"),
            ("YO", "Ё"), ("Yo", "Ё"), ("yo", "ё"),
            ("YU", "Ю"), ("Yu", "Ю"), ("yu", "ю"),
            ("YA", "Я"), ("Ya", "Я"), ("ya", "я"),
            ("A", "А"), ("a", "а"),
            ("B", "Б"), ("b", "б"),
            ("V", "В"), ("v", "в"),
            ("G", "Г"), ("g", "г"),
            ("D", "Д"), ("d", "д"),
            ("E", "Е"), ("e", "е"),
            ("J", "Ж"), ("j", "ж"),
            ("Z", "З"), ("z", "з"),
            ("I", "И"), ("i", "и"),
            ("K", "К"), ("k", "к"),
            ("L", "Л"), ("l", "л"),
            ("M", "М"), ("m", "м"),
            ("N", "Н"), ("n", "н"),
            ("O", "О"), ("o", "о"),
            ("P", "П"), ("p", "п"),
            ("R", "Р"), ("r", "р"),
            ("S", "С"), ("s", "с"),
            ("T", "Т"), ("t", "т"),
            ("U", "У"), ("u", "у"),
            ("F", "Ф"), ("f", "ф"),
            ("X", "Х"), ("x", "х"),
            ("H", "Ҳ"), ("h", "ҳ"),
            ("Q", "Қ"), ("q", "қ"),
            ("'", "ъ"),
        ]
        res = text
        for old, new in replacements:
            res = res.replace(old, new)
        return res


class TextbookIngestionPipeline:
    def __init__(self, pdf_book_id: int):
        self.book = PdfBook.objects.get(id=pdf_book_id)

    def compute_file_hash(self) -> str:
        hasher = hashlib.sha256()
        file_path = self.book.pdf_file.path
        with open(file_path, 'rb') as f:
            while chunk := f.read(8192):
                hasher.update(chunk)
        return hasher.hexdigest()

    def process_pdf(self, force_reprocess=False, use_ocr=True):
        from django import db
        db.close_old_connections()

        if self.book.is_processed and not force_reprocess and self.book.processing_status == 'COMPLETED':
            return True

        file_path = self.book.pdf_file.path
        if not os.path.exists(file_path):
            self.book.processing_status = 'FAILED'
            self.book.error_log = f"File not found: {file_path}"
            self.book.save()
            IngestionAuditLog.objects.create(
                document=self.book, step="file_check", status="FAILED",
                message=f"File not found: {file_path}"
            )
            return False

        self.book.processing_status = 'PROCESSING'
        self.book.processing_started_at = timezone.now()
        self.book.file_size = os.path.getsize(file_path)
        self.book.original_filename = os.path.basename(file_path)
        self.book.file_hash = self.compute_file_hash()
        self.book.save()

        # Idempotency / Reset old child records safely in proper FK dependency order
        EntityMention.objects.filter(chunk__document=self.book).delete()
        HistoricalRelationship.objects.filter(evidence_chunk__document=self.book).update(evidence_chunk=None)
        HistoricalChunk.objects.filter(document=self.book).delete()
        Topic.objects.filter(document=self.book).delete()
        HistoricalChapter.objects.filter(document=self.book).delete()
        DocumentPage.objects.filter(document=self.book).delete()

        doc = fitz.open(file_path)
        self.book.total_pages = len(doc)
        self.book.save()

        IngestionAuditLog.objects.create(
            document=self.book, step="extraction_start", status="RUNNING",
            message=f"Processing {self.book.total_pages} pages for {self.book.title}"
        )

        extracted_pages = []
        failed_pages = 0

        for page_idx in range(len(doc)):
            page_num = page_idx + 1
            page = doc[page_idx]
            raw_text = page.get_text("text") or ""

            extraction_method = 'NATIVE'
            is_ocr_used = False
            ocr_confidence = 0.0
            has_error = False
            error_msg = None

            # Check if native text extraction is sparse or missing
            if len(raw_text.strip()) < 50 and use_ocr and HAS_PYTESSERACT:
                try:
                    pix = page.get_pixmap(dpi=150)
                    img = Image.open(io.BytesIO(pix.tobytes("png")))
                    ocr_text = pytesseract.image_to_string(img, lang='uzb+eng+rus')
                    if len(ocr_text.strip()) > len(raw_text.strip()):
                        raw_text = ocr_text
                        extraction_method = 'OCR'
                        is_ocr_used = True
                        ocr_confidence = 0.85
                except Exception as e:
                    has_error = True
                    error_msg = f"OCR engine unavailable or failed: {str(e)}"
                    if not raw_text.strip():
                        raw_text = f"[{self.book.title} - {page_num}-bet tasviriy material / OCR unavailable]"

            elif len(raw_text.strip()) < 50:
                extraction_method = 'MIXED'
                if not raw_text.strip():
                    raw_text = f"[{self.book.title} - {page_num}-bet tasviriy/xarita material]"

            normalized = UzbekTextNormalizer.normalize_apostrophes(raw_text)

            # Detect headings on page
            detected_headings = []
            for line in raw_text.splitlines():
                lstr = line.strip()
                if lstr and (re.match(r'^(?:[I|V|X]+|[0-9]+)\s*-\s*BOB', lstr, re.IGNORECASE) or
                             re.match(r'^(?:§\s*[0-9]+|[0-9]+\s*-\s*MAVZU|[0-9]+\s*\.)', lstr, re.IGNORECASE) or
                             lstr.isupper() and len(lstr) < 80):
                    detected_headings.append(lstr)

            page_obj = DocumentPage(
                document=self.book,
                page_number=page_num,
                raw_text=raw_text,
                normalized_text=normalized,
                extraction_method=extraction_method,
                is_ocr_used=is_ocr_used,
                ocr_confidence=ocr_confidence,
                char_count=len(raw_text),
                word_count=len(raw_text.split()),
                has_error=has_error,
                error_message=error_msg,
                detected_headings=detected_headings
            )
            extracted_pages.append(page_obj)
            if has_error:
                failed_pages += 1

        doc.close()

        DocumentPage.objects.filter(document=self.book).delete()
        DocumentPage.objects.bulk_create(extracted_pages, batch_size=100, ignore_conflicts=True)

        # Refetch created pages to get primary key IDs
        extracted_pages = list(DocumentPage.objects.filter(document=self.book).order_by('page_number'))

        # Step 2: Extract Chapter and Topic Structure
        self._build_topics_and_chapters(extracted_pages)

        # Step 3: Semantic Chunking
        self._create_chunks(extracted_pages)

        # Step 4: Historical Entity and Relationship Extraction
        self._extract_entities_and_relationships()

        self.book.is_processed = True
        self.book.processing_status = 'COMPLETED' if failed_pages == 0 else 'PARTIAL'
        self.book.processing_completed_at = timezone.now()
        self.book.save()

        IngestionAuditLog.objects.create(
            document=self.book, step="ingestion_complete", status="COMPLETED",
            message=f"Successfully ingested {self.book.title} ({len(extracted_pages)} pages, status: {self.book.processing_status})."
        )
        return True

    def _build_topics_and_chapters(self, pages):
        current_chapter = None
        chapter_order = 1
        topic_order = 1

        topic_records = []
        current_topic_dict = None

        for page in pages:
            lines = page.normalized_text.split("\n")
            for line in lines:
                line_str = line.strip()
                if not line_str:
                    continue

                is_chapter = re.match(r'^(?:[I|V|X]+|[0-9]+)\s*-\s*BOB', line_str, re.IGNORECASE) or (line_str.isupper() and "BOB" in line_str)
                is_topic = re.match(r'^(?:§\s*[0-9]+|[0-9]+\s*-\s*MAVZU|[0-9]+\s*\.)', line_str, re.IGNORECASE)

                if is_chapter and len(line_str) < 120:
                    current_chapter, _ = HistoricalChapter.objects.get_or_create(
                        document=self.book,
                        number=chapter_order,
                        defaults={
                            'title': line_str,
                            'start_page': page.page_number,
                            'end_page': page.page_number
                        }
                    )
                    chapter_order += 1

                elif is_topic and len(line_str) < 150:
                    if current_topic_dict:
                        topic_records.append(current_topic_dict)
                    current_topic_dict = {
                        'title': line_str[:250],
                        'chapter': current_chapter,
                        'category': self.book.category,
                        'order': topic_order,
                        'start_page': page.page_number,
                        'end_page': page.page_number,
                        'content': f"{line_str}\n"
                    }
                    topic_order += 1

                elif current_topic_dict:
                    current_topic_dict['content'] += line_str + " "
                    current_topic_dict['end_page'] = page.page_number

        if current_topic_dict:
            topic_records.append(current_topic_dict)

        topics_to_create = []
        for tdata in topic_records:
            topics_to_create.append(Topic(
                document=self.book,
                chapter=tdata['chapter'],
                category=tdata['category'],
                title=tdata['title'],
                order=tdata['order'],
                start_page=tdata['start_page'],
                end_page=tdata['end_page'],
                content=tdata['content']
            ))

        if topics_to_create:
            Topic.objects.bulk_create(topics_to_create, batch_size=100)

        # Fallback if no explicit headings matched
        if Topic.objects.filter(document=self.book).count() == 0:
            for page_idx in range(0, len(pages), 10):
                batch_pages = pages[page_idx:page_idx + 10]
                start_p = batch_pages[0].page_number
                end_p = batch_pages[-1].page_number
                t_title = f"{self.book.title}: {start_p}-{end_p}-betlar"
                content = "\n".join([p.normalized_text for p in batch_pages])
                Topic.objects.create(
                    document=self.book,
                    category=self.book.category,
                    title=t_title,
                    order=topic_order,
                    start_page=start_p,
                    end_page=end_p,
                    content=content
                )
                topic_order += 1

    def _create_chunks(self, pages, chunk_size=400, overlap=80):
        topics = list(Topic.objects.filter(document=self.book))
        chunk_idx = 0
        chunks_to_create = []
        for page in pages:
            text = page.normalized_text or page.raw_text or ""
            page_topic = next((t for t in topics if t.start_page <= page.page_number <= t.end_page), None)

            words = text.split()
            page_chunks_added = 0
            if len(words) >= 10:
                for start in range(0, len(words), chunk_size - overlap):
                    chunk_words = words[start:start + chunk_size]
                    chunk_text = " ".join(chunk_words)
                    if len(chunk_text.strip()) < 15:
                        continue

                    provenance = f"{self.book.title}, {page.page_number}-bet"
                    stable_chunk_id = f"pdf_{self.book.id}_chunk_{chunk_idx}"

                    chunks_to_create.append(HistoricalChunk(
                        chunk_id=stable_chunk_id,
                        document=self.book,
                        page=page,
                        topic=page_topic,
                        chunk_index=chunk_idx,
                        text=chunk_text,
                        normalized_text=UzbekTextNormalizer.normalize_apostrophes(chunk_text),
                        token_count=len(chunk_words),
                        source_provenance=provenance
                    ))
                    chunk_idx += 1
                    page_chunks_added += 1

            if page_chunks_added == 0:
                provenance = f"{self.book.title}, {page.page_number}-bet"
                stable_chunk_id = f"pdf_{self.book.id}_chunk_{chunk_idx}"
                fallback_text = text.strip() if len(text.strip()) >= 5 else f"[{self.book.title} - {page.page_number}-bet tasviriy/skanerlangan sahifa]"

                chunks_to_create.append(HistoricalChunk(
                    chunk_id=stable_chunk_id,
                    document=self.book,
                    page=page,
                    topic=page_topic,
                    chunk_index=chunk_idx,
                    text=fallback_text,
                    normalized_text=UzbekTextNormalizer.normalize_apostrophes(fallback_text),
                    token_count=len(fallback_text.split()),
                    source_provenance=provenance
                ))
                chunk_idx += 1
        if chunks_to_create:
            HistoricalChunk.objects.bulk_create(chunks_to_create, batch_size=200)

    def _extract_entities_and_relationships(self):
        chunks = list(HistoricalChunk.objects.filter(document=self.book))

        entity_patterns = [
            (r'\b(Amir Temur|Temur|Tamerlan|Sohibqiron)\b', 'PERSON', 'Amir Temur', ['Temur', 'Амир Темур', 'Tamerlan']),
            (r'\b(Mirzo Ulug\'bek|Ulug\'bek)\b', 'PERSON', 'Mirzo Ulug\'bek', ['Ulugbek', 'Мирзо Улуғбек']),
            (r'\b(Alisher Navoiy|Navoiy)\b', 'PERSON', 'Alisher Navoiy', ['Navoiy', 'Алишер Навоий']),
            (r'\b(Zahiriddin Muhammad Bobur|Bobur)\b', 'PERSON', 'Zahiriddin Muhammad Bobur', ['Bobur', 'Заҳириддин Муҳаммад Бобур']),
            (r'\b(Yuliy Sezar|Sezar|Julius Caesar)\b', 'PERSON', 'Yuliy Sezar', ['Sezar', 'Юлий Цезарь']),
            (r'\b(Aleksandr Makedonskiy|Makedonskiy)\b', 'PERSON', 'Aleksandr Makedonskiy', ['Iskandar Zulqarnayn', 'Александр Македонский']),
            (r'\b(Samarqand)\b', 'PLACE', 'Samarqand', ['Samarkand', 'Самарқанд', 'Maroqanda']),
            (r'\b(Buxoro)\b', 'PLACE', 'Buxoro', ['Bukhara', 'Бухоро']),
            (r'\b(Toshkent|Choch)\b', 'PLACE', 'Toshkent', ['Tashkent', 'Тошкент', 'Binkat']),
            (r'\b(Rim|Roma)\b', 'PLACE', 'Rim', ['Rome', 'Рим']),
            (r'\b(Temuriylar|Temuriylar davlati)\b', 'DYNASTY', 'Temuriylar davlati', ['Timurids', 'Темурийлар']),
            (r'\b(Buxoro xonligi|Buxoro amirligi)\b', 'STATE', 'Buxoro xonligi', ['Bukhara Khanate']),
            (r'\b(Qo\'qon xonligi)\b', 'STATE', 'Qo\'qon xonligi', ['Kokand Khanate']),
            (r'\b(Xiva xonligi)\b', 'STATE', 'Xiva xonligi', ['Khiva Khanate']),
            (r'\b(Rim imperiyasi)\b', 'EMPIRE', 'Rim imperiyasi', ['Roman Empire']),
            (r'\b(1336|1405|1370|1449|1220|1917|1991)\b', 'DATE', 'Yil ko\'rsatkichi', []),
        ]

        entity_cache = {}
        for _, ent_type, canonical, aliases in entity_patterns:
            entity, _ = HistoricalEntity.objects.get_or_create(
                canonical_name=canonical,
                defaults={
                    'entity_type': ent_type,
                    'description': f"{canonical} haqida darslik ma'lumoti",
                    'grade_relevance': [self.book.grade],
                    'subject_relevance': [self.book.subject]
                }
            )
            entity_cache[canonical] = entity

            for alias in aliases:
                HistoricalEntityAlias.objects.get_or_create(
                    entity=entity,
                    alias_name=alias,
                    defaults={'language': 'UZ_LATIN' if not re.search(r'[\u0400-\u04FF]', alias) else 'UZ_CYRILLIC'}
                )

        mentions_to_create = []
        for chunk in chunks:
            for pattern, _, canonical, _ in entity_patterns:
                if re.search(pattern, chunk.text, re.IGNORECASE):
                    entity = entity_cache.get(canonical)
                    if entity:
                        mentions_to_create.append(EntityMention(
                            entity=entity,
                            chunk=chunk,
                            mention_text=canonical,
                            confidence=0.95
                        ))

        if mentions_to_create:
            EntityMention.objects.bulk_create(mentions_to_create, batch_size=200)

        temur = HistoricalEntity.objects.filter(canonical_name='Amir Temur').first()
        samarqand = HistoricalEntity.objects.filter(canonical_name='Samarqand').first()
        temuriylar = HistoricalEntity.objects.filter(canonical_name='Temuriylar davlati').first()

        if temur and samarqand:
            HistoricalRelationship.objects.get_or_create(
                source_entity=temur,
                target_entity=samarqand,
                relationship_type='BORN_IN',
                defaults={'description': 'Amir Temur Samarqand va Kesh (Shahrisabz) bilan uzviy bog\'liq.'}
            )
        if temur and temuriylar:
            HistoricalRelationship.objects.get_or_create(
                source_entity=temur,
                target_entity=temuriylar,
                relationship_type='FOUNDED',
                defaults={'description': 'Amir Temur Temuriylar davlatiga asos solgan.'}
            )
