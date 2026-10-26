# Tarix Ustoz — O‘zbekiston tarixi va Jahon tarixi ta'lim ilovasi / History Learning App / Приложение по истории

[🇺🇿 O'zbekcha](#-ozbekcha) | [🇬🇧 English](#-english) | [🇷🇺 Русский](#-русский)

---

## 🇺🇿 O'zbekcha

**Tarix Ustoz** — 6–11-sinf o'quvchilari va DTM / abituriyentlar uchun mo'ljallangan zamonaviy, qulay va professional Android (Kotlin + Jetpack Compose) tarix o'rganish ilovasi.

### ✨ Asosiy imkoniyatlar:
- **Neo Dashboard**: XP, streak (kunlik ketma-ketlik), umumiy progress, kun savoli va xatolar paneli.
- **Darsliklar va PDF Viewer**: 6–11-sinflar uchun O'zbekiston tarixi va Jahon tarixi darsliklari, mavzular va o'rnatilgan PDF ko'rish vositasi.
- **Interaktiv Test Dvigateli**: 
  - Variantlar va savollarni har safar tasodifiy (random shuffle) taqsimlash.
  - Xatolar tahlili, tushuntirishlar va manba havolalari.
  - **MX Player uslubidagi "Davom etish" (Resume FAB)**: Tugallanmagan testlarni istalgan paytda to'xtatgan joyidan davom ettirish.
  - **Dinamik bilet progressi**: Biletlar ro'yxatida ishlangan savollar va to'g'ri javoblar sonini real vaqtda ko'rsatish.
  - **Activity Lifecycle xavfsizligi**: Orqaga tugmasi bosilganda yoki ilovadan chiqib ketilganda ham progress avtomatik ravishda DataStore'da saqlanadi.
- **Xatolar ustida ishlash ("Bilmagan savollarim")**: Noto'g'ri javob berilgan savollarni qayta ishlash.
- **XP va Daraja tizimi**: Foydalanuvchi faolligini rag'batlantirish.
- **Offline-First**: Rasmiy darsliklar asosidagi boy lokal ma'lumotlar bazasi (Room DB).
- **Clean Architecture + MVVM + Hilt + Compose**.

---

## 🇬🇧 English

**Tarix Ustoz** is a modern, premium native Android (Kotlin + Jetpack Compose) history learning platform designed for grades 6–11 and university entrance exam (DTM) preparation in Uzbekistan.

### ✨ Key Features:
- **Neo Dashboard**: XP, streak tracking, overall progress, daily question, and mistake alerts.
- **Textbooks & PDF Viewer**: Official 6–11 grade textbooks for Uzbekistan and World History with built-in PDF reader.
- **Interactive Quiz Engine**:
  - Randomized shuffling of questions and A/B/C/D options on every attempt.
  - Detailed explanations, feedback, and source references.
  - **MX Player-style "Resume" FAB**: Easily continue unfinished quizzes right where you left off.
  - **Dynamic Ticket Progress**: Real-time progress indicators showing answered and correct question counts on ticket cards.
  - **Lifecycle Safe Persistence**: Progress is automatically and safely persisted to DataStore upon navigation back or app exit.
- **My Mistakes Review**: Dedicated spaced-repetition ready tracking for incorrect answers.
- **XP & Level System**: Gamified learning experience.
- **Offline-First**: Robust Room database seeded with official curriculum data.
- **Clean Architecture + MVVM + Hilt + Navigation Compose**.

---

## 🇷🇺 Русский

**Tarix Ustoz** — современное премиальное нативное Android (Kotlin + Jetpack Compose) приложение для изучения истории для учащихся 6–11 классов и подготовки к экзаменам (ДТМ) в Узбекистане.

### ✨ Основные возможности:
- **Neo Dashboard**: XP, серия дней (streak), общий прогресс, вопрос дня и панель ошибок.
- **Учебники и PDF Viewer**: Официальные учебники с 6 по 11 классы по истории Узбекистана и всемирной истории со встроенным просмотрщиком PDF.
- **Интерактивный движок тестов**:
  - Случайное перемешивание (shuffle) вопросов и вариантов ответа (A/B/C/D) при каждом запуске.
  - Подробные объяснения, обратная связь и ссылки на источники.
  - **Кнопка "Продолжить" (в стиле MX Player)**: Возможность в любой момент возобновить незавершенный тест с того же места.
  - **Динамический прогресс билетов**: Отображение количества решенных вопросов и правильных ответов в реальном времени на карточках билетов.
  - **Безопасное сохранение состояния**: При нажатии кнопки "Назад" или выходе из приложения прогресс автоматически и надежно сохраняется в DataStore.
- **Работа над ошибками**: Специальный раздел для повторения и проработки неверных ответов.
- **Система XP и уровней**: Геймификация процесса обучения.
- **Offline-First**: Надежная локальная база данных Room на основе официальных учебников.
- **Clean Architecture + MVVM + Hilt + Navigation Compose**.

---

## 📂 Project Structure / Структура проекта

```
app/
  src/main/java/com/tarixustoz/app/
    data/          # Room DB, Repositories, DataStore preferences
    domain/        # Domain models, use-cases
    presentation/  # Jetpack Compose screens, ViewModels (Home, Quiz, Textbooks, Mistakes, etc.)
    di/            # Hilt dependency injection modules

backend/                     # Django REST API + content generation pipeline
  api/                       # Models, serializers, viewsets, import commands
  core/                      # Settings, URLs
  content/
    banks/                   # Per-book question archives (.ndjson.xz)
    pdfs/                    # Optimized textbook PDFs
    tools/                   # Fact extraction & question generation pipeline
```

## 🚀 Getting Started / Как запустить

1. Clone or unzip the repository.
2. Open **Android Studio** → **Open** → select the `TarixUstozApp` folder.
3. Allow Gradle to sync.
4. (Optional) Add `app/google-services.json` for Firebase Auth & Firestore features.
5. Run the app on an emulator or physical Android device (`minSdk 26`).

## 📄 License / Лицензия

Educational use. Textbook content © respective publishers (Sharq, O‘qituvchi, etc.).
