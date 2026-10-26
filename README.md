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
    banks/                   # per-book question archives (.ndjson.xz) + manifest
    pdfs/                    # optimized textbook PDFs
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
* The raw `generated/questions.ndjson` (about 90 MB per book) is **not** committed.
  Instead each bank is packed with `pack_banks.py` into
  `backend/content/banks/<book>.ndjson.xz` (about 2 MB each, 22 MB for all 11
  books) plus `manifest.json`. Rebuild a bank from facts with `build_book_bank.py`;
  see `backend/content/books/README.md` for the full table of books and commands.

## Restoring the database (1.1 million tests + PDFs)

`db.sqlite3` (1.4–2.5 GB) and `backend/media/` are never committed. A fresh
clone or server rebuilds them from the compact archives in about 7 minutes:

```bash
cd backend
python manage.py migrate
python manage.py restore_content            # all 11 books: questions + PDFs
python manage.py restore_content --only 9-sinf_Jahon   # a single book
```

* `backend/content/banks/` — per-book question archives (`.ndjson.xz`) and manifest.
* `backend/content/pdfs/` — textbook PDFs; the scanned ones were recompressed
  (9-sinf Jahon 90 → 33 MB, 8-sinf O‘zbekiston 48 → 29 MB), every file is under 50 MB.
* The command is idempotent: books whose question count already matches are skipped.
* Verify at any time with `python manage.py audit_banks` (read-only; exits non-zero
  if any book is below 100 000). Never run a bulk edit without it afterwards.
* Questions are paged per book (`grade`, `subject`, `offset`, `limit`). Migration
  `0004_question_book_indexes` adds the `(grade, subject, verification_status, id)`
  index that serves this query (about 1 200 ms → 40 ms per 1 000-question page).
* To test against a scratch database set `SQLITE_PATH=<file>`.

After regenerating banks run `python content/tools/pack_banks.py --src-root content/books`
and `python content/tools/pack_pdfs.py --src media/books/pdfs`, then commit the results.

Textbooks that ship only as scanned PDFs (8-sinf and 9-sinf O‘zbekiston/Jahon
tarixi) go through `ocr_pdf_book.py` + `clean_ocr.py` before segmentation.

## Test directions (Yo'nalishlar bo'yicha testlar)

The six cards on the Tests screen are served from the database instead of fixed
numbers:

* `GET /api/questions/directions/?grade=7&subject=jahon` → question count per card.
* `POST /api/quizzes/generate/` with `category_code` = `EVENT`, `PERSON`, `WAR`,
  `YEAR`, `CITY` or `LOGICAL` returns only questions of that direction.

| Card | Combinations |
|---|---|
| Tarixiy voqealar | CAUSE_TO_EFFECT, EFFECT_TO_CAUSE, SOURCE_BASED, MAIN_CAUSE, TERM_TO_DEFINITION, DEFINITION_TO_TERM |
| Tarixiy shaxslar | PERSON_TO_ACTIVITY, ACTIVITY_TO_PERSON, WHO_WAS_FIRST |
| Yillar va sanalar | DATE_TO_EVENT, EVENT_TO_DATE, NUMBER_TO_MEANING, CHRONOLOGY |
| Shahar va hududlar | MAP_BASED, PLACE_TO_EVENT |
| Mantiqiy va DTM | TRUE_FALSE, ODD_ONE_OUT, MATCHING, COMPARE_SIMILAR, COMPARE_DIFFERENT |
| Urushlar va janglar | questions mentioning war vocabulary (`is_war` column) |

`Question.direction_code` and `Question.is_war` are filled by the import command and
serve these filters; see `backend/api/directions.py`.

## License

Educational use. Textbook content © respective publishers (Sharq, O‘qituvchi, etc.).
