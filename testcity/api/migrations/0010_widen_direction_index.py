from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0009_question_monument_index"),
    ]

    operations = [
        migrations.RemoveIndex(
            model_name="question",
            name="q_book_direction_idx",
        ),
        migrations.AddIndex(
            model_name="question",
            index=models.Index(
                fields=[
                    "grade", "subject", "verification_status",
                    "direction_code", "is_war", "is_monument",
                ],
                name="q_book_direction_idx",
            ),
        ),
    ]
