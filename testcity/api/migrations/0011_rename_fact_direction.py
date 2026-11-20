"""Rename the monument flag/card to facts: is_monument -> is_fact, MONUMENT -> FACT.

The card is now labelled "Tarixiy faktlar" and its keywords no longer include the
noisy substring "ark" (which matched "banklar").
"""
from django.db import migrations, models
from django.db.models import Q

FACT_KEYWORDS = (
    "masjid", "madrasa", "maqbara", "minora", "saroy", "qal'a", "qo'rg'on",
    "obida", "me'moriy", "majmua", "yodgorlik", "haykal",
)


def recompute_facts(apps, schema_editor):
    Question = apps.get_model("api", "Question")
    DirectionCount = apps.get_model("api", "DirectionCount")

    match = Q()
    for word in FACT_KEYWORDS:
        match |= (
            Q(text__icontains=word)
            | Q(option_a__icontains=word)
            | Q(option_b__icontains=word)
            | Q(option_c__icontains=word)
            | Q(option_d__icontains=word)
        )
    Question.objects.update(is_fact=False)
    Question.objects.filter(match).update(is_fact=True)
    DirectionCount.objects.filter(code="MONUMENT").update(code="FACT")


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0010_widen_direction_index"),
    ]

    operations = [
        migrations.RenameField(
            model_name="question",
            old_name="is_monument",
            new_name="is_fact",
        ),
        migrations.RemoveIndex(
            model_name="question",
            name="q_monument_idx",
        ),
        migrations.AddIndex(
            model_name="question",
            index=models.Index(fields=["verification_status", "is_fact"], name="q_fact_idx"),
        ),
        migrations.RemoveIndex(
            model_name="question",
            name="q_book_direction_idx",
        ),
        migrations.AddIndex(
            model_name="question",
            index=models.Index(
                fields=[
                    "grade", "subject", "verification_status",
                    "direction_code", "is_war", "is_fact",
                ],
                name="q_book_direction_idx",
            ),
        ),
        migrations.RunPython(recompute_facts, migrations.RunPython.noop),
    ]
