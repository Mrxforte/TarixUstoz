from django.core.management.base import BaseCommand
from api.models import Question


class Command(BaseCommand):
    help = (
        "Savol variantlarini tekshiradi. True/False savollarda faqat A va B bo'ladi, "
        "shuning uchun ular 4 variant talabidan ozod. --dry-run bilan faqat hisobot."
    )

    def add_arguments(self, parser):
        parser.add_argument("--dry-run", action="store_true", help="faqat hisobot, bazani o'zgartirmaydi")

    def handle(self, *args, **options):
        dry = options["dry_run"]
        verified, rejected, fixed = 0, 0, 0
        by_reason = {}

        for q in Question.objects.all().iterator(chunk_size=5000):
            reason = self._check(q)
            if reason:
                rejected += 1
                by_reason[reason] = by_reason.get(reason, 0) + 1
                if not dry and q.verification_status != "REJECTED":
                    q.verification_status = "REJECTED"
                    q.save(update_fields=["verification_status"])
            else:
                verified += 1
                if not dry and q.verification_status != "VERIFIED":
                    q.verification_status = "VERIFIED"
                    q.save(update_fields=["verification_status"])
                    fixed += 1

        if dry:
            self.stdout.write("DRY RUN — baza o'zgartirilmadi")
        for reason, count in sorted(by_reason.items(), key=lambda i: -i[1]):
            self.stdout.write(f"  {reason}: {count}")
        self.stdout.write(self.style.SUCCESS(
            f"Tekshirildi: {verified + rejected}; yaroqli: {verified}; rad etildi: {rejected}; "
            f"holati tuzatildi: {fixed}"
        ))

    def _check(self, q: Question) -> str:
        if not q.correct_option or q.correct_option not in {"A", "B", "C", "D"}:
            return "javob varianti ko'rsatilmagan"
        if not (q.text or "").strip():
            return "savol matni bo'sh"
        options = [q.option_a or "", q.option_b or "", q.option_c or "", q.option_d or ""]
        if q.question_type == "TRUE_FALSE":
            # Faqat A/B to'ldiriladi, C/D ataylab bo'sh qoladi.
            if not options[0].strip() or not options[1].strip():
                return "True/False variantlari to'liq emas"
            if options[0].strip() == options[1].strip():
                return "True/False variantlari bir xil"
            return ""
        if any(not o.strip() for o in options):
            return "to'ldirilmagan variant bor"
        if len({o.strip() for o in options}) < 4:
            return "takrorlangan variantlar"
        return ""
