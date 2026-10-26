import os
import sys
import json
import re
import hashlib
from django.core.management.base import BaseCommand
from django.conf import settings
from django.utils import timezone
from api.models import (
    PdfBook, Topic, HistoricalChunk, EntityMention, HistoricalRelationship
)

def sanitize_filename(name: str) -> str:
    cleaned = re.sub(r'[^\w\s-]', '', name).strip()
    return re.sub(r'[-\s]+', '_', cleaned)

def generate_book_id(grade: int, subject: str) -> str:
    subj_clean = "Ozbekiston_tarixi"
    if "jahon" in subject.lower():
        subj_clean = "Jahon_tarixi"
    elif grade == 6:
        subj_clean = "Qadimgi_dunyo_tarixi"
    return f"{grade}_sinf_{subj_clean}"

def extract_dates_and_events(text: str):
    dates_events = []
    # Regex to catch dates like mil. avv. 1370-yil, 1991-yil 31-avgust, XV asr, etc.
    date_regex = re.compile(
        r'\b((?:mil\.\s*avv\.\s*)?\d{1,4}(?:-\d{1,4})?\s*(?:-yil|-yillar|-yilgi|-asr|-asrlarda)?(?:\s+\d{1,2}-[a-zà-ў]+)?)\b\s*[-–—:]?\s*([^.!?\n]{10,120})',
        re.IGNORECASE
    )
    matches = date_regex.findall(text)
    seen = set()
    for date_str, event_str in matches[:15]:
        d_clean = date_str.strip()
        e_clean = event_str.strip()
        if len(d_clean) >= 4 and d_clean not in seen:
            seen.add(d_clean)
            dates_events.append({
                "sana": d_clean,
                "voqea": e_clean or "Tarixiy voqea"
            })
    return dates_events

def extract_historical_events(text: str):
    events = []
    sentences = re.split(r'[.!?]\s+', text)
    seen = set()
    for s in sentences:
        s_clean = s.strip()
        if any(kw in s_clean.lower() for kw in ['bo\'ldi', 'etildi', 'qabul qil', 'tashkil', 'vujudga', 'e\'lon', 'yuz ber', 'barpo']):
            if len(s_clean) > 20 and len(s_clean) < 200 and s_clean not in seen:
                seen.add(s_clean)
                year_match = re.search(r'\b((?:mil\.\s*avv\.\s*)?\d{1,4}(?:-\d{1,4})?\s*(?:-yil|-asr)?)\b', s_clean)
                year_str = year_match.group(1) if year_match else "Noma'lum"
                events.append({
                    "nomi": s_clean[:100],
                    "yili": year_str,
                    "izoh": s_clean
                })
                if len(events) >= 10:
                    break
    return events

def extract_entities_from_chunks(chunks):
    persons = []
    wars = []
    places = []

    mentions = EntityMention.objects.filter(chunk__in=chunks).select_related('entity')
    seen_persons = set()
    seen_wars = set()
    seen_places = set()

    for m in mentions:
        e = m.entity
        name = e.canonical_name
        if e.entity_type == 'PERSON' and name not in seen_persons:
            seen_persons.add(name)
            persons.append({
                "ism": name,
                "rol": e.description[:50] or "Tarixiy shaxs",
                "faoliyati": e.description or f"{name} haqida darslik ma'lumotlari"
            })
        elif e.entity_type == 'WAR' and name not in seen_wars:
            seen_wars.add(name)
            wars.append({
                "nomi": name,
                "yili": "Darslik ko'rsatkichi",
                "tomonlar": "Tarixiy tomonlar",
                "natija": e.description or f"{name} natijasi"
            })
        elif e.entity_type in ('PLACE', 'STATE', 'EMPIRE') and name not in seen_places:
            seen_places.add(name)
            places.append({
                "nomi": name,
                "izoh": e.description or f"{name} haqida tarixiy geografik ma'lumot"
            })

    return persons[:10], wars[:10], places[:10]

def generate_hashtags(grade: int, subject: str, topic_title: str, dates_events, persons, places):
    tags = set()
    tags.add(f"#{grade}sinf")
    if "jahon" in subject.lower():
        tags.add("#jahontarixi")
    else:
        tags.add("#ozbekistontarixi")

    words = re.findall(r'\b[A-Za-zÀ-Ўà-ў]{4,}\b', topic_title)
    for w in words[:4]:
        tags.add(f"#{w.lower()}")

    for d in dates_events[:3]:
        num = re.search(r'\d{3,4}', d["sana"])
        if num:
            tags.add(f"#{num.group(0)}")

    for p in persons[:2]:
        p_tag = re.sub(r'[^\w]', '', p["ism"])
        if p_tag:
            tags.add(f"#{p_tag.lower()}")

    for pl in places[:2]:
        pl_tag = re.sub(r'[^\w]', '', pl["nomi"])
        if pl_tag:
            tags.add(f"#{pl_tag.lower()}")

    return list(tags)[:10]


class Command(BaseCommand):
    help = 'Export textbooks into strict structured JSON and JSONL formats requested by user.'

    def add_arguments(self, parser):
        parser.add_argument('--book-id', type=int, help='Export specific PdfBook ID')
        parser.add_argument('--grade', type=int, help='Export all textbooks for a specific grade')

    def compute_sha256(self, file_path: str) -> str:
        hasher = hashlib.sha256()
        with open(file_path, 'rb') as f:
            while chunk := f.read(8192):
                hasher.update(chunk)
        return hasher.hexdigest()

    def handle(self, *args, **options):
        book_id = options.get('book_id')
        grade_filter = options.get('grade')

        json_dir = getattr(settings, 'BOOKS_JSON_DIR', os.path.join(settings.MEDIA_ROOT, 'books', 'json'))
        jsonl_dir = getattr(settings, 'BOOKS_JSONL_DIR', os.path.join(settings.MEDIA_ROOT, 'books', 'jsonl'))
        txt_dir = getattr(settings, 'BOOKS_TXT_DIR', os.path.join(settings.MEDIA_ROOT, 'books', 'txt'))

        for d in [txt_dir, json_dir, jsonl_dir]:
            os.makedirs(d, exist_ok=True)

        self.stdout.write(self.style.MIGRATE_HEADING("=== Tarix Ustoz - Structured Content Exporter ==="))

        books_qs = PdfBook.objects.all()
        if book_id:
            books_qs = books_qs.filter(id=book_id)
        elif grade_filter:
            books_qs = books_qs.filter(grade=grade_filter)

        books = list(books_qs.order_by('grade', 'subject'))
        if not books:
            self.stderr.write("No textbooks found.")
            sys.exit(1)

        exported_count = 0

        for book in books:
            b_id = generate_book_id(book.grade, book.subject)
            safe_name = sanitize_filename(f"{book.grade}_sinf_{book.subject}")
            self.stdout.write(f"\nExporting [{book.grade}-sinf | {book.subject}] book_id={b_id}...")

            # TXT Export
            txt_filename = f"{safe_name}.txt"
            txt_path = os.path.join(txt_dir, txt_filename)
            pages = book.pages.all().order_by('page_number')
            txt_lines = []
            for p in pages:
                txt_lines.append(f"================================================================================")
                txt_lines.append(f"DARSLIK: {book.title} | SINF: {book.grade} | FAN: {book.subject} | BET: {p.page_number}")
                txt_lines.append(f"================================================================================")
                txt_lines.append(p.normalized_text or p.raw_text or "")
                txt_lines.append("\n")
            full_txt = "\n".join(txt_lines) if txt_lines else f"DARSLIK: {book.title} ({book.grade}-sinf)"
            with open(txt_path, 'w', encoding='utf-8') as f:
                f.write(full_txt)

            book.txt_export_path = txt_path
            book.txt_export_hash = self.compute_sha256(txt_path)
            book.txt_export_size = os.path.getsize(txt_path)

            topics = list(book.topics.filter(review_status='REVIEWED').order_by('order'))
            if not topics:
                topics = list(book.topics.all().order_by('order'))

            structured_mavzular = []
            jsonl_lines = []

            for idx, t in enumerate(topics):
                m_id = f"{book.grade}-{idx + 1}"
                chapter_name = t.chapter.title if t.chapter else "Umumiy bo'lim"
                content_text = t.content or ""
                t_chunks = t.chunks.all()

                dates_events = extract_dates_and_events(content_text)
                hist_events = extract_historical_events(content_text)
                persons, wars, places = extract_entities_from_chunks(t_chunks)
                hashtags = generate_hashtags(book.grade, book.subject, t.title, dates_events, persons, places)

                davr_str = f"{book.grade}-sinf {book.subject} davri"
                if dates_events:
                    davr_str = f"{dates_events[0]['sana']} davri"

                topic_json = {
                    "mavzu_id": m_id,
                    "mavzu_nomi": t.title,
                    "bolim": chapter_name,
                    "davr": davr_str,
                    "yil_va_sanalar": dates_events,
                    "tarixiy_voqealar": hist_events,
                    "tarixiy_shaxslar": persons,
                    "urush_va_janglar": wars,
                    "shahar_va_hududlar": places,
                    "hacktag": hashtags
                }
                structured_mavzular.append(topic_json)

                jsonl_item = {
                    "book_id": b_id,
                    "sinf": book.grade,
                    "fan": book.subject,
                    "mavzu_id": m_id,
                    "mavzu_nomi": t.title,
                    "bolim": chapter_name,
                    "davr": davr_str,
                    "yil_va_sanalar": dates_events,
                    "tarixiy_voqealar": hist_events,
                    "tarixiy_shaxslar": persons,
                    "urush_va_janglar": wars,
                    "shahar_va_hududlar": places,
                    "hacktag": hashtags,
                    "text_passage": content_text[:1000]
                }
                jsonl_lines.append(json.dumps(jsonl_item, ensure_ascii=False))

            book_json_payload = {
                "book_id": b_id,
                "sinf": book.grade,
                "fan": book.subject,
                "mavzular": structured_mavzular
            }

            json_path = os.path.join(json_dir, f"{safe_name}.json")
            with open(json_path, 'w', encoding='utf-8') as f:
                json.dump(book_json_payload, f, ensure_ascii=False, indent=2)

            jsonl_path = os.path.join(jsonl_dir, f"{safe_name}.jsonl")
            with open(jsonl_path, 'w', encoding='utf-8') as f:
                f.write("\n".join(jsonl_lines))

            book.json_export_path = json_path
            book.json_export_hash = self.compute_sha256(json_path)
            book.json_export_size = os.path.getsize(json_path)

            book.jsonl_export_path = jsonl_path
            book.jsonl_export_hash = self.compute_sha256(jsonl_path)
            book.jsonl_export_size = os.path.getsize(jsonl_path)

            book.export_status = 'COMPLETED'
            book.last_exported_at = timezone.now()
            book.save()

            exported_count += 1
            self.stdout.write(self.style.SUCCESS(
                f"  -> Exported structured JSON ({len(structured_mavzular)} topics), JSONL ({len(jsonl_lines)} items)"
            ))

        self.stdout.write(self.style.SUCCESS(f"\nAll {exported_count} textbooks successfully exported to structured JSON and JSONL!"))
