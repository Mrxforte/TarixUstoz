import re
from django.core.management.base import BaseCommand
from api.models import Question

class Command(BaseCommand):
    help = "Barcha testlarni tozalash: bet raqamlari, OCR xatolar va tushunarsiz joylarni o'chirish va manba/izohlarni bir xil uslubga keltirish"

    def add_arguments(self, parser):
        parser.add_argument(
            "--apply", action="store_true",
            help="o'zgarishlarni bazaga yozadi; berilmasa faqat hisobot chiqadi",
        )

    def handle(self, *args, **options):
        apply_changes = options["apply"]
        self.stdout.write("Testlarni tozalash va bir xil professional uslubga keltirish boshlandi...")
        if not apply_changes:
            self.stdout.write(self.style.WARNING(
                "DRY RUN — baza o'zgartirilmaydi. Yozish uchun --apply qo'shing."
            ))

        questions = Question.objects.all()
        total = questions.count()
        self.stdout.write(f"Jami tekshiriladigan testlar soni: {total}")

        cleaned_count = 0
        deleted_count = 0

        # Keywords for metadata/publishing noise to delete completely
        NOISE_KEYWORDS = [
            "Yangiyo'l Poligraf", "yangiyo'l poligraf", "SAGDULLAYEV", "KOSTETSKIY",
            "Sahifalovchi", "Musahhih", "Bosishga ruxsat", "Ofset bosma", "Bichimi 70x90",
            "O'quv nashri", "UO'K", "ISBN", "Taqrizchilar", "Mundarija"
        ]

        for q in questions:
            # Delete completely irrelevant publisher/metadata questions
            if any(k in (q.text or "") for k in NOISE_KEYWORDS):
                if apply_changes:
                    q.delete()
                deleted_count += 1
                continue

            changed = False

            # 1. Clean Text
            text = q.text or ""
            
            # Remove page numbers like (10-sinf O'zbekiston tarixi, 8-bet), 5-bet, BET: 12
            text = re.sub(r'\(?\d+-sinf.*?\d+-bet\)?\s*,?', '', text, flags=re.IGNORECASE)
            text = re.sub(r'\b\d+-bet\b,?\s*', '', text, flags=re.IGNORECASE)
            text = re.sub(r'\bBET:\s*\d+\b\s*', '', text, flags=re.IGNORECASE)
            text = re.sub(r'http[s]?://\S+', '', text)
            
            # Remove chapter headers like "27 I BOB. O'RTA ASRLARNING ILK DAVRI"
            text = re.sub(r'^\d+\s+[I|V|X]+\s+BOB\..*?\s+', '', text, flags=re.IGNORECASE)
            text = re.sub(r'^\d+\s+BOB\..*?\s+', '', text, flags=re.IGNORECASE)
            
            # Clean up extra quotes, spaces, and trailing ellipses
            text = text.strip(' "“’\'')
            if text.endswith('...'):
                text = text[:-3].strip()
            if text.endswith('…'):
                text = text[:-1].strip()

            # Ensure proper question mark
            if not text.endswith('?') and not text.endswith('.'):
                text += '?'

            if text != q.text:
                q.text = text
                changed = True

            # 2. Clean Source Citation
            src = q.source_citation or ""
            src = re.sub(r'\b\d+-bet\b,?\s*', '', src, flags=re.IGNORECASE)
            src = re.sub(r'\bBET:\s*\d+\b\s*', '', src, flags=re.IGNORECASE)
            src = re.sub(r'http[s]?://\S+', '', src)
            src = src.strip(' "“’\',-')

            if not src:
                src = f"{q.grade}-sinf {q.subject}"

            if src != q.source_citation:
                q.source_citation = src
                changed = True

            # 3. Clean Explanation & Short Explanation
            exp = q.explanation or ""
            exp = re.sub(r'\(?\d+-sinf.*?\d+-bet\)?\s*,?', '', exp, flags=re.IGNORECASE)
            exp = re.sub(r'\b\d+-bet\b,?\s*', '', exp, flags=re.IGNORECASE)
            exp = re.sub(r'\bBET:\s*\d+\b\s*', '', exp, flags=re.IGNORECASE)
            exp = re.sub(r'http[s]?://\S+', '', exp)
            exp = exp.strip(' "“’\',-')

            if not exp:
                exp = f"Darslikda ushbu ma'lumot bo'yicha to'g'ri javob {q.correct_option} varianti ekanligi ko'rsatilgan."

            if exp != q.explanation:
                q.explanation = exp
                q.short_explanation = exp[:250]
                changed = True

            # Save if modified
            if changed:
                if apply_changes:
                    q.save()
                cleaned_count += 1

        self.stdout.write(self.style.SUCCESS(
            f"\nMuvaffaqiyatli yakunlandi! {cleaned_count} ta test qayta ishlanib tozalandi, "
            f"{deleted_count} ta axlat savol {'o‘chirildi' if apply_changes else 'o‘chirilishi mumkin'}."
        ))
