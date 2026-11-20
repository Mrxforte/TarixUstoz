from django.core.management.base import BaseCommand
import json
import os
from api.models import Question

class Command(BaseCommand):
    help = 'Seeds tricky tests from tricky_backend_tests_full.json into the DB'

    def handle(self, *args, **kwargs):
        file_path = os.path.join('media', 'exports', 'json', 'tricky_backend_tests_full.json')
        if not os.path.exists(file_path):
            self.stdout.write(self.style.ERROR(f"File not found: {file_path}"))
            return

        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)

        questions = data.get('questions', [])
        created_count = 0

        for q in questions:
            grade_id = q.get('grade', 7)
            subject_id = q.get('subject', 'uzbekiston')
            
            Question.objects.update_or_create(
                text=q['text'],
                defaults={
                    'grade': grade_id,
                    'subject': subject_id,
                    'option_a': q['options'][0]['text'],
                    'option_b': q['options'][1]['text'],
                    'option_c': q['options'][2]['text'],
                    'option_d': q['options'][3]['text'],
                    'correct_option': next((opt['id'].upper() for opt in q['options'] if opt['isCorrect']), 'A'),
                    'explanation': q.get('explanation', ''),
                    'source_citation': q.get('sourceReference', ''),
                    'difficulty': q.get('difficulty', 'HARD'),
                    'tags': [q.get('category', 'ALL')],
                }
            )
            created_count += 1
        
        self.stdout.write(self.style.SUCCESS(f'Successfully seeded {created_count} tricky questions.'))
