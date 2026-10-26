# Tarix Ustoz — O‘zbekiston maktablari uchun tarix o‘rganish ilovasi

Premium native Android (Kotlin + Jetpack Compose) history learning platform for grades 6–11.

## Features implemented

- **Neo Dashboard** with XP, streak, progress, daily question, mistakes badge
- **Textbooks** organized by grade (6–11) for O‘zbekiston tarixi and Jahon tarixi
- **Interactive Quiz Engine** with feedback, explanations, source references
- **Bilmagan savollarim** (My Mistakes) with spaced-repetition ready tracking
- **XP & Level system**
- **Light / Dark / System themes**
- **Offline-first** Room database with seeded content from official textbooks
- **AI Tutor screen** (ready for secure Gemini backend)
- **Leaderboard UI** (ready for Firestore)
- Clean Architecture + MVVM + Hilt + Navigation Compose

## How to open in Android Studio

1. Unzip this archive.
2. Open Android Studio → **Open** → select the `TarixUstozApp` folder.
3. Let Gradle sync (internet required for dependencies).
4. (Optional) Add `app/google-services.json` from your Firebase project to enable Auth & Firestore.
5. Run on emulator or device (minSdk 26).

## Seeded textbooks (from provided PDFs)

| Grade | Subject              | Title                                      | Authors                          | Year |
|-------|----------------------|--------------------------------------------|----------------------------------|------|
| 6     | Jahon                | Tarix. Qadimgi dunyo                       | Sagdullayev, Kostetskiy          | 2017 |
| 7     | O‘zbekiston          | O‘zbekiston tarixi (IV–XVI)                | A. Muhammadjonov                 | 2017 |
| 7     | Jahon                | Jahon tarixi (V–XV)                        | Salimov, Sultonov                | 2017 |
| 8–11  | both                 | Corresponding official editions            | various                          | 2017–2019 |

Sample verified questions are included for 7-sinf O‘zbekiston tarixi (Kirish section).

## Next steps for full production

1. Extract remaining topics & questions from the PDF textbooks (use admin pipeline or OCR + review).
2. Connect Firebase Authentication + Cloud Firestore for multi-device sync and leaderboard.
3. Deploy Cloud Functions that call Gemini with RAG over textbook chunks (never put API key in APK).
4. Expand question types (chronology, match, cause-effect).
5. Add timed exam simulator and full analytics charts.
6. Implement full spaced-repetition algorithm and mastery rules.

## Architecture

```
app/
  data/          # Room, repositories, entities
  domain/        # Models, use-cases
  presentation/  # Compose screens, ViewModels
  di/            # Hilt modules

backend/                     # Django REST API + content pipeline
  api/                       # models, serializers, viewsets, import commands
  core/                      # settings, urls
  content/
    tools/                   # textbook -> facts -> questions pipeline
    books/<sinf>-sinf_<Fan>/ # per-textbook source text, topics, verified facts
```

## Content pipeline (100 000 tests per textbook)

Every textbook is processed in four steps; all of it is reproducible from the
files committed under `backend/content/books/<book>/`.

```bash
cd backend
python content/tools/extract_mavzu_book.py --in <source.txt> --out-dir <book>/source --name <Name>
python content/tools/validate_facts.py <book>/facts/*.json --topics <book>/source/topics.json
python content/tools/build_book_bank.py 9-sinf_Jahon_tarixi --grade 9 --subject jahon --prefix j9j
python manage.py import_jahon7_bank --ndjson <book>/generated/questions.ndjson \
    --topics <book>/source/topics.json --grade 9 --subject "Jahon tarixi" --prefix j9j --replace
```

* `facts/*.json` — every fact carries an `evidence` field that must be a verbatim
  quote from the textbook paragraph; the validator enforces this, so no question
  can be answered from anything except the book.
* Each bank contains exactly 100 000 questions, spread over the 20 fixed
  combinations (5 000 each) with near-miss distractors (neighbouring year/century,
  same-topic person, similar event).
* `generated/questions.ndjson` is **not** committed (about 90 MB per book, 1 GB in
  total). Rebuild any bank with `build_book_bank.py`; see
  `backend/content/books/README.md` for the full table of books and commands.

Textbooks that ship only as scanned PDFs (8-sinf and 9-sinf O‘zbekiston/Jahon
tarixi) go through `ocr_pdf_book.py` + `clean_ocr.py` before segmentation.

## License

Educational use. Textbook content © respective publishers (Sharq, O‘qituvchi, etc.).
