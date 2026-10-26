from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ("api", "0008_direction_counts"),
    ]

    operations = [
        migrations.AddIndex(
            model_name="question",
            index=models.Index(
                fields=["verification_status", "is_monument"], name="q_monument_idx"
            ),
        ),
    ]
