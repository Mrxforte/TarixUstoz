# Darsliklar bo‘yicha kontent papkalari

Har bir darslik alohida papkada turadi — savollar, faktlar va manba matni
bir-biriga aralashmaydi. Har bir kitob uchun **100 000 test** yoziladi
(20 kombinatsiya × 5 000), hammasi darslik matnidan olingan iqtibos bilan
tasdiqlangan faktlarga asoslanadi.

```
content/
  books/
    <sinf>-sinf_<Fan_nomi>/
      source/
        <Fan>.txt               tozalangan matn (<<<PAGE n>>> belgilari bilan)
        topics.json             paragraflar: {number, title, book_page, chapter, text}
        titles.json             (ixtiyoriy) qo‘lda yozilgan qisqa sarlavhalar
      facts/
        chA.json … chH.json     tekshirilgan faktlar (har birida `evidence` iqtibosi)
      generated/
        questions.ndjson        100 000 test (20 kombinatsiya × 5 000)
```

Mavjud kitoblar:

| Papka | Sinf | Fan | Paragraflar | Holat |
|---|---|---|---|---|
| `6-sinf_Qadimgi_dunyo_tarixi` | 6 | Qadimgi dunyo tarixi | 44 | 100 000 test |
| `7-sinf_Jahon_tarixi` | 7 | Jahon tarixi | 44 | 100 000 test |
| `7-sinf_Ozbekiston_tarixi` | 7 | O‘zbekiston tarixi | 42 | 100 000 test |
| `8-sinf_Jahon_tarixi` | 8 | Jahon tarixi | 32 | 100 000 test |
| `8-sinf_Ozbekiston_tarixi` | 8 | O‘zbekiston tarixi | 40 | 100 000 test (skan, OCR) |
| `9-sinf_Ozbekiston_tarixi` | 9 | O‘zbekiston tarixi | 37 | 100 000 test |
| `9-sinf_Jahon_tarixi` | 9 | Jahon tarixi | 41 | 100 000 test (skan, OCR) |
| `10-sinf_Jahon_tarixi` | 10 | Jahon tarixi | 22 | 100 000 test |
| `10-sinf_Ozbekiston_tarixi` | 10 | O‘zbekiston tarixi | 28 | 100 000 test |
| `11-sinf_Jahon_tarixi` | 11 | Jahon tarixi | 29 | 100 000 test |
| `11-sinf_Ozbekiston_tarixi` | 11 | O‘zbekiston tarixi | 22 | 100 000 test |

## Quvur (pipeline)

```bash
cd backend

# 1) Matnli PDF/txt eksport -> tozalash va paragraflarga bo‘lish
python content/tools/extract_mavzu_book.py \
  --in content/books/8-sinf_Jahon_tarixi/source/Jahon_tarixi.txt \
  --out-dir content/books/8-sinf_Jahon_tarixi/source \
  --name Jahon_tarixi

# 1b) Skan (matn qatlamisiz) PDF uchun: OCR -> tozalash -> bo‘lish
python content/tools/ocr_pdf_book.py \
  --pdf media/books/pdfs/9-sinf_Jahon_tarixi.pdf \
  --out content/books/9-sinf_Jahon_tarixi/source/Jahon_tarixi.ocr.txt --dpi 150
python content/tools/clean_ocr.py \
  --in content/books/9-sinf_Jahon_tarixi/source/Jahon_tarixi.ocr.txt \
  --out content/books/9-sinf_Jahon_tarixi/source/Jahon_tarixi.clean.txt
python content/tools/extract_mavzu_book.py \
  --in content/books/9-sinf_Jahon_tarixi/source/Jahon_tarixi.clean.txt \
  --out-dir content/books/9-sinf_Jahon_tarixi/source --name Jahon_tarixi

# 1c) Sarlavhalar noto‘g‘ri chiqsa — qo‘lda yozilgan sarlavhalarni qo‘llash
python content/tools/apply_titles.py 8-sinf_Jahon_tarixi

# 2) Faktlarni tekshirish (har bir `evidence` manba matnida aynan bo‘lishi shart)
python content/tools/validate_facts.py content/books/8-sinf_Jahon_tarixi/facts/*.json \
  --topics content/books/8-sinf_Jahon_tarixi/source/topics.json

# 3) Testlarni generatsiya qilish (tekshirish + generatsiya birga)
python content/tools/build_book_bank.py 8-sinf_Jahon_tarixi \
  --grade 8 --subject jahon --prefix j8j --target 100000

# 4) Tipografiyani tekislash (o‘/g‘, kirill harflarini almashtirish)
python content/tools/normalize_bank.py content/books/8-sinf_Jahon_tarixi/generated/questions.ndjson
```

`7-sinf_Jahon_tarixi` uchun argumentlarsiz ishga tushirish ham mumkin
(default yo‘llar shu papkaga qaratilgan):

```bash
python content/tools/generate_questions.py 100000
```

## 20 kombinatsiya

`generate_questions.py` quyidagi 20 turdan har biriga 5 000 tadan savol yozadi
(biror tur uchun material yetmasa, qolgan turlar orasida qayta taqsimlanadi,
jami har doim 100 000):

Sana→Voqea, Voqea→Sana, Shaxs→Faoliyat, Faoliyat→Shaxs, Atama→Ta’rif,
Ta’rif→Atama, Moslashtirish (4 juftlik), Xronologik tartib, To‘g‘ri/Noto‘g‘ri,
Sabab→Natija, Natija→Sabab, Qiyoslash (o‘xshashlik), Qiyoslash (farq),
Manba/Matn asosida, Xarita/joy nomi asosida, «Qaysi biri ortiqcha»,
«Birinchi bo‘lib kim/qaysi», «Eng muhim sabab», Raqam→Ma’no, Joy nomi→Voqea.

Chalg‘ituvchi variantlar ataylab yaqin qilib tanlanadi (qo‘shni yil/asr,
bir mavzudagi boshqa shaxs yoki voqea), shuning uchun savollar
yodlashni emas, mazmunni tekshiradi.

## Backendga yuklash (TarixUstozApp/backend)

```bash
python manage.py import_jahon7_bank \
  --ndjson   <abs path>/generated/questions.ndjson \
  --topics   <abs path>/source/topics.json \
  --grade 8 --subject "O‘zbekiston tarixi" --prefix j8u --replace
```
