"""Precompute the direction card of every question so the cards filter by index."""
from django.db import migrations, models
from django.db.models import Q

COMBINATION_DIRECTIONS = {
    "EVENT": (
        "CAUSE_TO_EFFECT", "EFFECT_TO_CAUSE", "SOURCE_BASED",
        "MAIN_CAUSE", "TERM_TO_DEFINITION", "DEFINITION_TO_TERM",
    ),
    "PERSON": ("PERSON_TO_ACTIVITY", "ACTIVITY_TO_PERSON", "WHO_WAS_FIRST"),
    "YEAR": ("DATE_TO_EVENT", "EVENT_TO_DATE", "NUMBER_TO_MEANING", "CHRONOLOGY"),
    "CITY": ("MAP_BASED", "PLACE_TO_EVENT"),
    "LOGICAL": ("TRUE_FALSE", "ODD_ONE_OUT", "MATCHING", "COMPARE_SIMILAR", "COMPARE_DIFFERENT"),
}


def fill_direction_code(apps, schema_editor):
    Question = apps.get_model("api", "Question")
    for code, combinations in COMBINATION_DIRECTIONS.items():
        Question.objects.filter(
            Q(generation_config__combination__in=combinations) & ~Q(direction_code=code)
        ).update(direction_code=code)


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0005_question_is_war"),
    ]

    operations = [
        migrations.AddField(
            model_name="question",
            name="direction_code",
            field=models.CharField(db_index=True, default="", max_length=16),
        ),
        migrations.RunPython(fill_direction_code, migrations.RunPython.noop),
    ]
