import django, os

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from api.models import PdfBook, Topic

books = PdfBook.objects.all().order_by('grade', 'subject')

out = ["            // All 11 Official Textbooks (384 Curriculum Topics)"]

for b in books:
    topics = list(Topic.objects.filter(document=b, review_status='REVIEWED').order_by('order'))
    subj_lower = (b.subject or '').lower()
    if b.grade == 6:
        tb_id = "uz_6"
    elif "jahon" in subj_lower:
        tb_id = f"jahon_{b.grade}"
    else:
        tb_id = f"uz_{b.grade}"

    out.append(f"            // {b.grade}-sinf {b.subject} ({len(topics)} topics) [tb_id={tb_id}]")
    for idx, t in enumerate(topics):
        safe_title = t.title.replace('"', '\\"')
        safe_book = b.title.replace('"', '\\"')
        out.append(f'            TopicEntity("topic_{tb_id}_{idx+1}", "ch_1", "{tb_id}", {idx+1}, "{safe_title}", "{safe_title}...", "[]", "[]", "{safe_book}", {b.grade}),')

kotlin_code = '\n'.join(out)
with open('temp_topics_seed.kt', 'w', encoding='utf-8') as f:
    f.write(kotlin_code)

print("Successfully regenerated temp_topics_seed.kt!")
