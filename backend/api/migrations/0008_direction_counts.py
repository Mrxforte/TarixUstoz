"""Add the monument flag and the direction-count cache table."""
from django.db import migrations, models
from django.db.models import Q

MONUMENT_KEYWORDS = (
    "masjid", "madrasa", "maqbara", "minora", "saroy", "qal'a", "qo'rg'on",
    "obida", "me'moriy", "majmua", "yodgorlik", "haykal", "regist", "ark",
)


def mark_monuments(apps, schema_editor):
    Question = apps.get_model("api", "Question")
    match = Q()
    for word in MONUMENT_KEYWORDS:
        match |= (
            Q(text__icontains=word)
            | Q(option_a__icontains=word)
            | Q(option_b__icontains=word)
            | Q(option_c__icontains=word)
            | Q(option_d__icontains=word)
        )
    Question.objects.filter(match).update(is_monument=True)


def fill_counts(apps, schema_editor):
    """Seed the cache table; the management command refreshes it exactly."""
    DirectionCount = apps.get_model("api", "DirectionCount")
    Question = apps.get_model("api", "Question")
    from django.db.models import Count, Sum

    per_book = {}
    for grade, subject in Question.objects.values_list("grade", "subject").distinct():
        base = Question.objects.filter(verification_status="VERIFIED", grade=grade, subject=subject)
        counts = dict(base.values_list("direction_code").annotate(n=Count("id")))
        counts = {code: n for code, n in counts.items() if code}
        counts["WAR"] = base.filter(is_war=True).count()
        counts["MONUMENT"] = base.filter(is_monument=True).count()
        per_book[(grade, subject)] = counts

    for (grade, subject), counts in per_book.items():
        for code, count in counts.items():
            DirectionCount.objects.update_or_create(
                grade=grade, subject=subject, code=code, defaults={"count": count}
            )


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0007_question_direction_index"),
    ]

    operations = [
        migrations.AddField(
            model_name="question",
            name="is_monument",
            field=models.BooleanField(db_index=True, default=False),
        ),
        migrations.RunPython(mark_monuments, migrations.RunPython.noop),
        migrations.CreateModel(
            name="DirectionCount",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("grade", models.IntegerField()),
                ("subject", models.CharField(max_length=100)),
                ("code", models.CharField(max_length=16)),
                ("count", models.IntegerField(default=0)),
                ("updated_at", models.DateTimeField(auto_now=True)),
            ],
            options={
                "verbose_name": "Yo'nalish bo'yicha savollar soni",
                "verbose_name_plural": "Yo'nalish bo'yicha savollar soni",
                "ordering": ["grade", "subject", "code"],
                "unique_together": {("grade", "subject", "code")},
            },
        ),
        migrations.RunPython(fill_counts, migrations.RunPython.noop),
    ]
