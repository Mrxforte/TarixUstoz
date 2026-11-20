import os
import json
import re
from django.core.management.base import BaseCommand
from django.conf import settings
from api.models import Question

class Command(BaseCommand):
    help = "Backend va JSON keshdagi barcha testlarni birma-bir mantiqiy va strukturaviy audit qilish"

    def handle(self, *args, **options):
        self.stdout.write("=== Tarix Ustoz - Barcha Testlar Audit Diagnostikasi ===\n")

        # 1. Audit Backend Database Questions
        db_qs = Question.objects.all()
        db_total = db_qs.count()
        self.stdout.write(f"1. Django Ma'lumotlar Bazasi (db.sqlite3): Jami {db_total} ta test")

        db_issues = []
        unique_texts = set()
        duplicates = 0

        for q in db_qs:
            text = (q.text or "").strip()
            
            # Uniqueness check
            norm_text = text.lower()
            if norm_text in unique_texts:
                duplicates += 1
            else:
                unique_texts.add(norm_text)

            # Quality & Logic checks
            if len(text) < 10:
                db_issues.append(f"ID {q.id}: Savol matni juda qisqa ('{text}')")
            
            if re.search(r'\b\d+-bet\b|\bBET:\s*\d+\b|http[s]?://', text, re.IGNORECASE):
                db_issues.append(f"ID {q.id}: Savolda bet raqami yoki URL qolgan ('{text[:60]}...')")

            if not text.endswith('?') and not text.endswith('.'):
                db_issues.append(f"ID {q.id}: Savol so'roq belgisi bilan tugamagan ('{text[:60]}...')")

            opts = [q.option_a, q.option_b, q.option_c, q.option_d]
            if len(set([o.strip().lower() for o in opts if o])) < 4:
                db_issues.append(f"ID {q.id}: Takrorlangan yoki bo'sh variantlar mavjud")

            if q.correct_option not in ['A', 'B', 'C', 'D']:
                db_issues.append(f"ID {q.id}: Noto'g'ri javob kaliti ('{q.correct_option}')")

        self.stdout.write(f"   - Takrorlangan savollar (Duplicates): {duplicates} ta")
        self.stdout.write(f"   - Mantiqiy/Strukturaviy muammolar: {len(db_issues)} ta")
        if db_issues:
            for issue in db_issues[:10]:
                self.stdout.write(f"     * {issue}")

        # 2. Audit JSON Cache Files
        json_dir = os.path.join(settings.BASE_DIR, 'media', 'exports', 'json')
        self.stdout.write(f"\n2. Backend JSON Kesh Fayllari ({json_dir}):")

        if os.path.exists(json_dir):
            files = [f for f in os.listdir(json_dir) if f.endswith('.json')]
            for fn in sorted(files):
                fp = os.path.join(json_dir, fn)
                try:
                    with open(fp, 'r', encoding='utf-8') as f:
                        items = json.load(f)
                    
                    j_issues = 0
                    for item in items:
                        t = item.get('text', '')
                        if not t or len(t) < 10 or 'bet' in t.lower() or 'http' in t.lower():
                            j_issues += 1

                    self.stdout.write(f"   - {fn}: Jami {len(items)} ta test ({j_issues} ta ogohlantirish)")
                except Exception as e:
                    self.stdout.write(f"   - {fn}: O'qishda xatolik - {e}")
        else:
            self.stdout.write("   - JSON kesh katalogi topilmadi!")

        self.stdout.write(self.style.SUCCESS("\n=== Audit Diagnostikasi Yakunlandi ==="))
