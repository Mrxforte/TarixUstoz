# TestCity backend arxitekturasi

Backend **Clean Architecture** (SOLID) qatlamlariga bo'lingan. Bog'liqlik yo'nalishi
faqat ichkariga:

```
presentation → application → domain
        ↘ data ↗ (domain portlarini implement qiladi)
```

| Qatlam | Papka | Vazifasi | Bog'liqliklari |
|---|---|---|---|
| **Domain** | `domain/` | Sof biznes-qoidalar: fanlar, tarix, baholash, sertifikatlar, reyting | Hech kim (Django yo'q) |
| **Application** | `application/` | Use case'lar: katalog, biletlar, sertifikat baholash, auth, leaderboard | faqat `domain` |
| **Data** | `data/` | Django ORM repolari, Google token tekshiruvchi | `domain` portlarini implement qiladi |
| **Presentation** | `presentation/http/` | DRF serializer, view, marshrut, cache/ETag, xato mapping | `application` + `infrastructure.container` |
| **Composition root** | `infrastructure/container.py` | Portlarni adapterlar bilan ulaydi | hammasi |
| **ORM modellari** | `catalog/`, `accounts/` | Jadvallar (fanlar, sertifikatlar, tillar, foydalanuvchi, reyting) | — |
| **Legacy** | `api/` | v1 API va tarix ORM modellari (mobil ilova uchun o'zgarmaydi) | — |

```
testcity/
├── domain/
│   ├── shared/         # DomainError, ValidationError, AuthenticationError, value objectlar
│   ├── subjects/       # Subject, Language, SubjectRepository, LanguageRepository
│   ├── tarix/          # Tarix fani: 3 yo'nalish, sinf xaritasi
│   ├── assessment/     # QuestionRef, Ticket, TicketPlan/Kind/Size, yo'nalish qoidalari
│   ├── certificates/   # CertificateProgram, milliy/xalqaro family, baholash (IELTS/TOEFL/SAT)
│   ├── accounts/       # Account, AccountRepository
│   └── leaderboard/    # Leaderboard, LeaderboardEntry, davrlar
├── application/
│   ├── subjects/       # Fanlar + til tanlash
│   ├── assessment/     # Overview, ticket, pack, kunlik savol
│   ├── certificates/   # Sertifikat dasturlari va baholash
│   ├── accounts/       # Ro'yxatdan o'tish, login, Google, profil
│   ├── leaderboard/    # Reyting va ball qo'shish
│   └── seeds.py        # Standart tillar, fanlar, sertifikat dasturlari
├── data/
│   ├── repositories/   # Django ORM adapterlari
│   ├── ai/             # Gemini/RAG engine'lar
│   └── google.py       # Google ID token tekshiruvi
├── presentation/http/  # serializers, views, auth_views, leaderboard_views, caching, exceptions, urls
├── catalog/            # Subject, Language, SubjectTranslation, ExamProgram, ExamProgramTranslation...
├── accounts/           # UserProfile, UserScore, parol tiklash tokenlari
└── api/                # Legacy v1
```

## So'rov oqimi

```
HTTP → presentation/http/views.py → application/*/service.py
     → domain/*/ports.py (Protocol) → data/repositories/* → Django ORM
```

- View'da biznes-mantiq yo'q: parametr → servis → serializer.
- Domain xatolari (`NotFoundError`, `ValidationError`, `AuthenticationError`) global
  exception handler bilan `404` / `400` / `401` ga aylanadi.
- `catalog/tests/test_architecture.py` qatlam qoidalarini tekshiradi: `domain/` yoki
  `application/` ichida Django importi bo'lsa test yiqiladi.

## Fanlar va tillar

- Har bir fan `subjects` katalogida — **Tarix** alohida fan (`code="tarix"`) va o'z
  domain moduli bilan (`domain/tarix`).
- Tillar **dinamik**: `catalog.Language` (uz, ru, en, kz, tj) admin panelidan
  qo'shiladi/o'chiriladi. Fan va dastur nomlari `SubjectTranslation` /
  `ExamProgramTranslation` jadvallarida.
- API'da `?lang=ru` — tarjima bo'lmasa default nom qaytadi.

Yangi fan qo'shish: admin panelda `Subject` yaratish **yoki** `application/seeds.py`
ga qo'shib `python manage.py seed_catalog`.

## Sertifikat testlari

`ExamProgram` bitta jadvalda ikki bo'limni saqlaydi:

| Family | Kind | Izoh |
|---|---|---|
| `national` | `national_certificate` | Milliy sertifikat (fan bo'yicha) |
| `national` | `attestation` | Attestatsiya (fan bo'yicha) |
| `international` | `sat` | SAT (400–1600, 2 bo'lim) |
| `international` | `ielts` | IELTS Academic (0–9, overall band) |
| `international` | `toefl` | TOEFL iBT (0–120, 4 bo'lim) |
| `international` | `cefr` | CEFR daraja imtihoni + A1–C2 xaritasi |

Baholash sof funksiyalarda (`domain/certificates/scoring.py`):
`ielts_overall_band()` (6.25 → 6.5), `toefl_total()`, `sat_total()`,
`cefr_from_ielts()`, `cefr_from_score()`.

Yangi sertifikat qo'shish: admin panelda `ExamProgram` + bo'limlar + baho oraliqlari,
yoki `application/seeds.py`.

## Biletlar (10 / 20 / 30)

`domain/assessment/entities.py` dagi `TicketPlan`:

| `kind` | Ma'nosi |
|---|---|
| `topic` | Mavzulashtirilgan test (`topic_id`) |
| `direction` | Yo'nalishli test (`direction=EVENT/PERSON/...`) |
| `book` | Sinf kitobi bo'yicha (`grade` + `track`) |
| `mixed` | Aralash — easy/medium/hard bo'yicha balanslangan |
| `global` | Butun bazadan 10/20/30 talik global bilet |

`size` faqat **10, 20, 30**. `seed` berilsa to'plam "yangi" bo'lib qaytadi
(pool deterministik rotatsiya qilinadi), `ticket` esa bilet raqami.

## Reyting va keshlash

- `accounts.UserScore` — `points`, `weekly_points`, `streak` (indekslangan).
- `GET /api/v2/leaderboard/?period=all|weekly&limit=50` — keshlangan (60 s).
- Keshlash: `core/settings.py` da `CACHES`. Default — `LocMemCache`; ko'p workerli
  (enterprise) deploy uchun `DJANGO_CACHE_BACKEND=redis` + `REDIS_URL`.
- Keshlangan endpointlar: fanlar ro'yxati, fan tafsiloti, sertifikatlar ro'yxati,
  leaderboard. TTL'lar: `CACHE_CATALOG_TTL`, `CACHE_LEADERBOARD_TTL`,
  `CACHE_OVERVIEW_TTL`.

## Autentifikatsiya

| Metod | Yo'l | Tavsif |
|---|---|---|
| POST | `/api/v2/auth/register/` | Email + parol → JWT |
| POST | `/api/v2/auth/login/` | Email + parol → JWT |
| POST | `/api/v2/auth/google/` | Google `id_token` → JWT |
| POST | `/api/v2/auth/refresh/` | Refresh → yangi access |
| POST | `/api/v2/auth/password-reset/` | Tiklash havolasi (email) |
| POST | `/api/v2/auth/password-reset/confirm/` | `uid`, `token`, yangi parol |
| GET/PATCH | `/api/v2/auth/me/` | Profil (JWT talab qilinadi) |

- JWT: `djangorestframework-simplejwt` (access + refresh).
- Google: `data/google.py` — `tokeninfo` endpoint orqali tekshiriladi
  (`GOOGLE_CLIENT_ID` berilgan bo'lsa `aud` ham tekshiriladi).
- Parol tiklash javobi har doim bir xil — hisob bor-yo'qligi oshkor bo'lmaydi.

## API v2

| Metod | Yo'l |
|---|---|
| GET | `/api/v2/languages/` |
| GET | `/api/v2/subjects/?category=&lang=` |
| GET | `/api/v2/subjects/{code}/?lang=` |
| GET | `/api/v2/subjects/{code}/overview/?grade=&track=` |
| GET | `/api/v2/subjects/{code}/tickets/?grade=&track=&direction=&difficulty=&size=&ticket=` |
| GET | `/api/v2/subjects/{code}/packs/?kind=&size=10|20|30&topic_id=&direction=&grade=&track=&ticket=&seed=` |
| GET | `/api/v2/subjects/{code}/daily/?grade=&track=&user_identifier=` |
| GET | `/api/v2/certificates/?family=national|international&kind=&subject=&lang=` |
| GET | `/api/v2/certificates/families/` |
| GET | `/api/v2/certificates/{code}/` |
| POST | `/api/v2/certificates/{code}/evaluate/` |
| GET | `/api/v2/certification/` (faqat milliy) |
| GET | `/api/v2/international/` (faqat xalqaro) |
| GET | `/api/v2/leaderboard/?period=&limit=` |
| POST | `/api/v2/leaderboard/submit/` (JWT) |

`v1` (`/api/...`, `/api/v1/...`) o'zgarmagan — chiqarilgan mobil ilova shu yo'llardan
foydalanadi.

## Ma'lumotlar bazasi siyosati

- `api` jadvallariga **tegilmagan** (1.1M savol bazasi xavfsiz).
- Yangi jadvallar: `catalog_*` (fanlar, tillar, dasturlar) va `accounts_*`
  (profil, reyting ballari) — `migrate` faqat ularni yaratadi.
- `python manage.py seed_catalog` — tillar, fanlar va sertifikat dasturlari (idempotent).

## Testlar

```bash
python manage.py test catalog   # domain, arxitektura, v2 API, auth, leaderboard
python manage.py test           # to'liq to'plam (v1 + v2)
```
