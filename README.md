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
```

## License

Educational use. Textbook content © respective publishers (Sharq, O‘qituvchi, etc.).
