# TestCity backend arxitekturasi

Backend **Clean Architecture** qatlamlariga bo'lingan. Asosiy qoida: bog'liqlik
yo'nalishi faqat ichkariga — `interfaces → infrastructure → application → domain`.

```
testcity/
├── domain/            # Sof biznes-qoidalar (Django yo'q)
│   ├── subjects/      # Subject, SubjectCategory, SubjectRepository porti
│   ├── assessment/    # QuestionRef, Ticket, yo'nalish (direction) qoidalari
│   ├── certification/ # Milliy sertifikat va attestatsiya dasturlari, baholash
│   ├── international/ # SAT, IELTS, CEFR ballari va CEFR darajalari
│   ├── tarix/         # Tarix fani: 3 ta yo'nalish (kitob) va sinf xaritasi
│   └── shared/        # DomainError, ValidationError, value objectlar
├── application/       # Use case'lar (faqat domain portlariga tayanadi)
│   ├── subjects/      # Fanlar katalogi
│   ├── assessment/    # Overview, ticket, kunlik savol
│   ├── certification/ # Sertifikat dasturlari va baholash
│   ├── international/ # Xalqaro testlar va baholash
│   └── seeds.py       # Standart fanlar va imtihon dasturlari
├── infrastructure/    # Tashqi dunyo adapterlari
│   ├── persistence/   # Django ORM repolari (domain portlarining implementatsiyasi)
│   ├── ai/            # Gemini/RAG engine'lar (LLM adapterlari)
│   └── container.py   # Composition root (bog'liqliklarni ulash)
├── interfaces/http/   # DRF serializer, view, marshrutlar (v2)
├── catalog/           # ORM modellari: Subject, ExamProgram, ProgramSection, ScoreBand
└── api/               # Legacy v1 (mobil ilova uchun o'zgarmaydi) + tarix ORM modellari
```

## So'rov oqimi

```
HTTP so'rov → interfaces/http/views.py → application/*/service.py
            → domain/*/ports.py (Protocol) → infrastructure/persistence/repositories/*
            → Django ORM
```

View ichida biznes-mantiq yo'q: u faqat parametrlarni o'qib, servisni chaqiradi va
serializer bilan javob qaytaradi. Domain xatolari (`NotFoundError`, `ValidationError`)
global exception handler orqali `404` / `400` ga aylanadi
(`interfaces/http/exceptions.py`).

## Fanlar (subjects)

Tarix — alohida fan sifatida `subjects` katalogida turadi (`code = "tarix"`) va o'z
domain moduliga ega: `domain/tarix/subjects.py` (kitoblar va sinf xaritasi).
Qolgan fanlar (matematika, ona tili, ingliz tili, fizika, ...) shu katalogga qo'shiladi.

Yangi fan qo'shish:

```python
# application/seeds.py -> default_subjects() ro'yxatiga qo'shish
("astronomiya", "Astronomiya", "Astronomy", "🔭"),
```

so'ng `python manage.py seed_catalog` (idempotent — mavjudlarini yangilaydi).

## Imtihon dasturlari

`ExamProgram` bitta jadvalda 5 turdagi imtihonni saqlaydi:

| `kind` | Ma'nosi | Domain moduli |
|---|---|---|
| `national_certificate` | Milliy sertifikat | `domain/certification` |
| `attestation` | Attestatsiya | `domain/certification` |
| `sat` | SAT | `domain/international` |
| `ielts` | IELTS | `domain/international` |
| `cefr` | CEFR daraja imtihoni | `domain/international` |

Har bir dastur bo'limlarga (`ProgramSection`) va baho oraliqlariga (`ScoreBand`)
ega. Ballash qoidalari sof funksiyalarda:

- `CertificationProgram.score()` — bo'limlar yig'indisi, `band_for()`, `is_passing()`
- `ielts_overall_band()` — o'rtacha ball, 0.5 ga yaxlitlash (6.25 → 6.5)
- `cefr_from_ielts()` — IELTS → CEFR (B1/B2/C1/C2)
- `sat_total()` — Math + Reading/Writing (400–1600)

## API v2

| Metod | Yo'l | Tavsif |
|---|---|---|
| GET | `/api/v2/subjects/` | Fanlar ro'yxati (`?category=`) |
| GET | `/api/v2/subjects/{code}/` | Fan + uning sertifikat dasturlari |
| GET | `/api/v2/subjects/{code}/overview/` | Savollar/mavzular soni (`?grade=&track=`) |
| GET | `/api/v2/subjects/{code}/tickets/` | Test bileti (`?grade=&track=&direction=&difficulty=&size=&ticket=`) |
| GET | `/api/v2/subjects/{code}/daily/` | Kunlik savol (`?grade=&track=&user_identifier=`) |
| GET | `/api/v2/certification/` | Milliy sertifikat + attestatsiya (`?kind=&subject=`) |
| GET | `/api/v2/certification/{code}/` | Dastur tafsiloti |
| POST | `/api/v2/certification/{code}/evaluate/` | `{"sections": {"test": 65, "amaliy": 25}}` |
| GET | `/api/v2/international/` | SAT, IELTS, CEFR (`?kind=`) |
| GET | `/api/v2/international/{code}/` | Test tafsiloti |
| POST | `/api/v2/international/{code}/evaluate/` | Bo'lim ballari bo'yicha natija |

`v1` (`/api/...`, `/api/v1/...`) o'zgarmagan — chiqarilgan mobil ilova shu yo'llardan
foydalanadi.

## Ma'lumotlar bazasi siyosati

- `api` modellari va jadvallariga **tegilmagan** (1.1M savol bazasi xavfsiz).
- `catalog` — yangi, qo'shimcha jadvallar; `migrate` faqat yangi jadval yaratadi.
- `python manage.py seed_catalog` — standart fanlar va imtihon dasturlarini yozadi.

## Testlar

```bash
python manage.py test catalog        # domain, arxitektura va v2 API testlari
python manage.py test                # to'liq to'plam (v1 + v2)
```

`catalog/tests/test_architecture.py` qatlam qoidalarini tekshiradi: `domain/` da
Django importi bo'lsa, test yiqiladi.
