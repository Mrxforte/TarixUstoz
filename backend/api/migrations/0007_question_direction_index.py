from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0006_question_direction_code"),
    ]

    operations = [
        migrations.AddIndex(
            model_name="question",
            index=models.Index(
                fields=["grade", "subject", "verification_status", "direction_code", "is_war"],
                name="q_book_direction_idx",
            ),
        ),
    ]
