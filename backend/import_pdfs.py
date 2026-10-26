import os
import django

# Django muhitini sozlash
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'core.settings')
django.setup()

from api.models import Category, PdfBook
from django.core.files import File

# Siz bergan PDF lar ro'yxati
pdf_paths = [
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\9-sinf 9-Sinf Jahon tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\9-sinf 9-Sinf O‘zbekiston tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\10-sinf 10-Sinf Jahon tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\10-sinf 10-Sinf O‘zbekiston tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\11-sinf 11-Sinf Jahon tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\11-sinf 11-Sinf O‘zbekiston tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\6-sinf 6-Sinf Tarix.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\7-sinf 7-Sinf Jahon tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\7-sinf 7-Sinf O‘zbekiston tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\8-sinf 8-Sinf Jahon tarixi.pdf",
    r"C:\Users\Azamat\Desktop\Tarix darsliklari\8-sinf 8-Sinf O‘zbekiston tarixi.pdf",
]

print("PDF larni bazaga yuklash boshlandi...\n")

for path in pdf_paths:
    if not os.path.exists(path):
        print(f"XATO: Fayl topilmadi -> {path}")
        continue

    filename = os.path.basename(path)
    title = filename.replace('.pdf', '')

    # Nomi bo'yicha bo'limni avtomatik aniqlash (Masalan: "9-sinf")
    cat_name = title.split(' ')[0]

    # Bo'lim mavjud bo'lmasa yaratish
    category, created = Category.objects.get_or_create(
        name=cat_name,
        defaults={'order': int(cat_name.split('-')[0]) if cat_name.split('-')[0].isdigit() else 0}
    )

    # Agar bu PDF avval yuklanmagan bo'lsa, yuklaymiz
    if not PdfBook.objects.filter(title=title).exists():
        with open(path, 'rb') as f:
            pdf_book = PdfBook(title=title, category=category)
            pdf_book.pdf_file.save(filename, File(f), save=True)
            print(f"Muvaffaqiyatli yuklandi: {title} (Bo'lim: {category.name})")
    else:
        print(f"Mavjud: {title} (Avval yuklangan)")

print("\nBarcha PDF fayllar bazaga saqlandi!")
