"""Mark war-related questions so the "Urushlar va janglar" card is index-backed."""
from django.db import migrations, models
from django.db.models import Q

WAR_KEYWORDS = (
    "urush", "jang", "safar", "hujum", "qamal", "g'alaba", "mag'lubiyat",
    "sulh", "qo'shin", "lashkar", "jangchi", "mudofaa", "bosqin",
)


def mark_war_questions(apps, schema_editor):
    Question = apps.get_model("api", "Question")
    match = Q()
    for word in WAR_KEYWORDS:
        match |= (
            Q(text__icontains=word)
            | Q(option_a__icontains=word)
            | Q(option_b__icontains=word)
            | Q(option_c__icontains=word)
            | Q(option_d__icontains=word)
        )
    Question.objects.filter(match).update(is_war=True)


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0004_question_book_indexes"),
    ]

    operations = [
        migrations.AddField(
            model_name="question",
            name="is_war",
            field=models.BooleanField(db_index=True, default=False),
        ),
        migrations.RunPython(mark_war_questions, migrations.RunPython.noop),
    ]
