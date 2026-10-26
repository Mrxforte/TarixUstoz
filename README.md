# Tarix Ustoz — O‘zbekiston tarixi va Jahon tarixi ta'lim ilovasi / History Learning App / Приложение по истории

[🇺🇿 O'zbekcha](#-ozbekcha) | [🇬🇧 English](#-english) | [🇷🇺 Русский](#-русский)

---

## 🇺🇿 O'zbekcha

**Tarix Ustoz** — O‘zbekiston Respublikasining 6–11-sinf o‘quvchilari, o‘qituvchilari hamda Milliy Sertifikat va DTM imtihonlariga tayyorlanayotgan abituriyentlar uchun mo‘ljallangan eng zamonaviy, mukammal va professional **Android (Kotlin + Jetpack Compose)** ta'lim platformasi.

---

### 📦 Loyihaning asosiy modullari va imkoniyatlari:

1. **Neo Dashboard (Bosh sahifa)**:
   - **XP va Daraja tizimi**: Har bir to‘g‘ri javob uchun ballar yig‘ish va darajani oshirish.
   - **Streak (Kunlik ketma-ketlik)**: O‘quvchining har kuni o‘qish odatini shakllantirish uchun kunlik seriya hisoblagichi.
   - **Kun Savoli (Daily Question)**: Har kuni yangi tarixiy savol va tezkor test.
   - **Xatolar nishoni (Mistakes Badge)**: Noto‘g‘ri javob berilgan savollarga tezkor o‘tish va ularni qayta ishlash.
   - **Yo‘nalishlar bo‘yicha testlar (Directions Grid)**: Tarixiy voqealar, shaxslar, urushlar va janglar, yillar va sanalar, shahar va hududlar, tarixiy faktlar hamda Mantiqiy/DTM savollari.

2. **Darsliklar va PDF Viewer (Kutubxona)**:
   - 6–11-sinflar uchun **O‘zbekiston tarixi** va **Jahon tarixi** fanlarining rasmiy darsliklari.
   - Har bir darslik bo‘limlarga, boblarga va mavzularga bo‘lingan.
   - **O‘rnatilgan PDF Reader**: Darsliklarni to‘liq PDF formatida o‘qish, sahifalash va qulay ko‘rish imkoniyati.

3. **Interaktiv Test Dvigateli (Quiz Engine)**:
   - **Biletlar tizimi**: Har biri 30 ta savoldan iborat rasmiy test biletlari (Duolingo qoidasi asosida: kamida 10 ta to‘g‘ri topilsa keyingi bilet ochiladi).
   - **Tasodifiy taqsimot (Random Shuffle)**: Testni boshlaganda savollar ham, **A, B, C, D variantlari** ham har safar butunlay tasodifiy tartibda aralashib tushadi.
   - **MX Player uslubidagi "Davom etish" (Resume FAB)**: Testni oxirigacha tugatmasdan chala tashlab ketsangiz, biletlar ekranining markazida chiqadigan maxsus pleyer uslubidagi tugma orqali to‘xtatgan joyingizdan darhol davom ettirishingiz mumkin.
   - **Dinamik progress va indikatorlar**: Biletlar ro‘yxatida har bir biletning ishlanish jarayoni (masalan, `2/30 (3 ta ishlangan)`) real vaqt rejimida ko‘rsatiladi.
   - **Activity Lifecycle xavfsizligi**: Foydalanuvchi orqaga (`Back`) tugmasini bosib chiqib ketsa yoki ilovadan chiqsa ham, `onCleared()` va `DataStore` orqali barcha javoblar va savollar indeksi avtomatik ravishda xavfsiz saqlanadi.

4. **Xatolar ustida ishlash ("Bilmagan savollarim")**:
   - Test paytida xato qilingan savollar avtomatik ravishda saqlanadi va maxsus bo‘limda ularni qayta tahlil qilish, to‘g‘ri javoblarni o‘rganish imkoniyati beriladi.

5. **Reyting va Profil (Leaderboard & Profile)**:
   - Foydalanuvchilarning umumiy XP ballari bo‘yicha reyting jadvali.
   - Profilni tahrirlash, o‘quvchining maqsadi (DTM balli), ism va avatar sozlamalari.

---

### 🛠 Texnologik stek va Arxitektura:
- **UI / Presentation**: Jetpack Compose, Material 3, Material Icons Extended, Coil (rasmlar uchun).
- **Arxitektura**: Clean Architecture (Data, Domain, Presentation qatlamlari) + MVVM (ViewModel, StateFlow).
- **Dependency Injection**: Hilt (Google Dagger Hilt).
- **Mahalliy ma'lumlilar bazasi (Offline-First)**: Room Database (1.1 milliondan ortiq test bazasi va darsliklar uchun indekslangan so‘rovlar) hamda Jetpack DataStore Preferences (foydalanuvchi sozlamalari va active quiz state uchun).
- **Asinxronlik**: Kotlin Coroutines va Flow.
- **Serializatsiya**: Kotlinx Serialization JSON.

---

## 🇬🇧 English

**Tarix Ustoz** is a modern, premium native Android (Kotlin + Jetpack Compose) history learning platform designed for students in grades 6–11 and university entrance exam (DTM) candidates in Uzbekistan.

### 📦 Key Modules & Features:

1. **Neo Dashboard (Home Screen)**:
   - **XP & Level System**: Earn points and level up with every correct answer.
   - **Streak Counter**: Daily study streak tracker to build consistent learning habits.
   - **Daily Question**: A fresh historical question every day with instant quiz.
   - **Mistakes Badge**: Quick shortcut to review incorrectly answered questions.
   - **Direction Quizzes**: Topic-based categories (Historical Events, Figures, Wars & Battles, Dates & Years, Cities & Regions, Historical facts, and DTM Logical tests).

2. **Textbooks & PDF Viewer (Library)**:
   - Official Ministry of Education textbooks for **Uzbekistan History** and **World History** (Grades 6–11).
   - Structured chapters and topics.
   - **Built-in PDF Reader**: View, read, and navigate textbook PDFs seamlessly.

3. **Interactive Quiz Engine**:
   - **Ticket System**: Official exam tickets containing 30 questions each (unlocking the next ticket upon scoring at least 10 correct answers).
   - **Random Shuffling**: Questions and A/B/C/D answer options are thoroughly shuffled on every single attempt.
   - **MX Player-style "Resume" FAB**: If you leave a test unfinished, a floating action button appears instantly on the tickets screen, allowing you to resume right where you left off.
   - **Dynamic Progress**: Real-time progress tracking showing answered counts and scores directly on ticket cards.
   - **Lifecycle Safety**: Progress is automatically persisted via `onCleared()` and `DataStore` when pressing back or leaving the quiz, preventing data loss.

4. **My Mistakes Review**:
   - Automatically records incorrect answers and provides a dedicated spaced-repetition training section.

5. **Leaderboard & Profile**:
   - Global XP leaderboard and fully editable user profile with study target goals.

---

## 🇷🇺 Русский

**Tarix Ustoz** — современная, премиальная нативная Android (Kotlin + Jetpack Compose) платформа для изучения истории, созданная для школьников 6–11 классов и абитуриентов, готовящихся к экзаменам ДТМ в Узбекистане.

### 📦 Основные модули и возможности:

1. **Neo Dashboard (Главный экран)**:
   - **Система XP и уровней**: Начисление баллов и повышение уровня за каждый правильный ответ.
   - **Streak (Серия дней)**: Трекер ежедневной активности для выработки привычки регулярного обучения.
   - **Вопрос дня**: Ежедневный исторический вопрос с мгновенной викториной.
   - **Панель ошибок**: Быстрый доступ к повторению неверно отвеченных вопросов.
   - **Тесты по направлениям**: Исторические события, личности, войны и сражения, даты и годы, города и регионы, памятники, а также логические вопросы ДТМ.

2. **Учебники и PDF Viewer (Библиотека)**:
   - Официальные учебники по **Истории Узбекистана** и **Всемирной истории** для 6–11 классов.
   - Разделение на главы, параграфы и темы.
   - **Встроенный PDF Reader**: Чтение и навигация по полным версиям учебников в формате PDF.

3. **Интерактивный движок тестов**:
   - **Система билетов**: Официальные экзаменационные билеты по 30 вопросов (разблокировка следующего билета при наборе не менее 10 правильных ответов).
   - **Полное перемешивание (Random Shuffle)**: При каждом запуске билета вопросы и варианты ответа **(A, B, C, D)** перемешиваются в случайном порядке.
   - **Кнопка "Продолжить" (в стиле MX Player / Resume FAB)**: Если вы вышли из теста, не завершив его, внизу экрана билетов появляется специальная плавающая кнопка, позволяющая возобновить тест с той же секунды/вопроса.
   - **Динамический прогресс**: Карточки билетов отображают актуальный прогресс и количество решенных вопросов в реальном времени.
   - **Безопасность жизненного цикла (Lifecycle Safety)**: При нажатии кнопки «Назад» текущий прогресс теста мгновенно сохраняется в DataStore благодаря методу `onCleared()`, защищая от потери данных.

4. **Работа над ошибками ("Мои ошибки")**:
   - Автоматическое сохранение неверных ответов для последующего повторения и проработки.

5. **Рейтинг и профиль**:
   - Таблица лидеров по очкам XP и полноценное редактирование профиля с указанием целей (баллы ДТМ).

---

## 📂 Project File Structure / Структура проекта

```
app/
  src/main/java/com/tarixustoz/app/
    data/          # Room DB, Repositories, DataStore preferences, DAOs
    domain/        # Domain models (Question, Textbook, Topic, etc.), use-cases
    presentation/  # Jetpack Compose UI screens & ViewModels:
                   #   - home/         (Neo Dashboard)
                   #   - textbooks/    (Library & PDF Viewer)
                   #   - quiz/         (QuizScreen, TicketListScreen, QuizOptions)
                   #   - mistakes/     (Mistakes review)
                   #   - leaderboard/  (Rankings)
                   #   - profile/      (User settings)
                   #   - navigation/   (NavGraph & bottom bar)
    di/            # Hilt dependency injection modules

backend/                     # Django REST API + content generation pipeline
  api/                       # Models, serializers, viewsets, management commands
  core/                      # Settings, URLs
  content/
    banks/                   # Per-book question archives (.ndjson.xz)
    pdfs/                    # Optimized textbook PDFs
    tools/                   # Fact extraction & question generation pipeline
```

## 🚀 Getting Started & Installation / Как запустить

1. Clone or download the repository.
2. Open **Android Studio** → **Open** → select the `TarixUstozApp` folder.
3. Wait for Gradle to synchronize dependencies.
4. (Optional) Add `app/google-services.json` to enable Firebase Authentication & Firestore features.
5. Run the application on an Android emulator or physical device (`minSdk 26` or higher).

## 📄 License / Лицензия

Educational use. Textbook content © respective publishers (Sharq, O‘qituvchi, etc.).
