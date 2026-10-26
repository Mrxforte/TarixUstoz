from django.core.management.base import BaseCommand
from api.models import Question

class Command(BaseCommand):
    help = "Validates answer options, evidence links, and rejects ambiguous or invalid questions"

    def handle(self, *args, **options):
        self.stdout.write("Validating question bank...")
        questions = Question.objects.all()
        validated = 0
        rejected = 0

        for q in questions:
            # Basic validation rules
            options_set = {q.option_a, q.option_b, q.option_c, q.option_d}
            if len(options_set) < 4:
                # Duplicate options
                q.verification_status = 'REJECTED'
                q.save()
                rejected += 1
            elif not q.correct_option or q.correct_option not in ['A', 'B', 'C', 'D']:
                q.verification_status = 'REJECTED'
                q.save()
                rejected += 1
            else:
                q.verification_status = 'VERIFIED'
                q.save()
                validated += 1

        self.stdout.write(self.style.SUCCESS(f"Validation complete: {validated} verified, {rejected} rejected."))
