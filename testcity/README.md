# TarixUstoz Backend

**Django REST Framework** asosida qurilgan TarixUstoz mobil ilovasi uchun backend serveri.

## 📋 Features

- ✅ Django 5.0 + Django REST Framework
- ✅ SQLite (development) / PostgreSQL (production)
- ✅ CORS support
- ✅ Media file management (PDFs, JSON, TXT)
- ✅ RESTful API endpoints
- ✅ Admin panel
- ✅ LLM integration (Gemini)

## 🧱 Arxitektura (Clean Architecture)

```
interfaces → infrastructure → application → domain
```

| Qatlam | Papka | Vazifasi |
|---|---|---|
| Domain | `domain/` | Sof biznes-qoidalar: fanlar, tarix yo'nalishlari, baholash (Django importi yo'q) |
| Application | `application/` | Use case'lar: katalog, test bileti, kunlik savol, sertifikat/xalqaro baholash |
| Infrastructure | `infrastructure/` | Django ORM repolari, Gemini/RAG adapterlari, `container.py` |
| Interface | `interfaces/http/` | DRF serializer/view/marshrutlar (`/api/v2/`) |
| Legacy | `api/` | v1 API va tarix ORM modellari (mobil ilova uchun o'zgarmaydi) |
| ORM | `catalog/` | `Subject`, `ExamProgram`, `ProgramSection`, `ScoreBand` jadvallari |

To'liq tavsif va API jadvali: [ARCHITECTURE.md](ARCHITECTURE.md).

Qo'llab-quvvatlanadigan imtihonlar: **maktab fanlari**, **milliy sertifikat**,
**attestatsiya**, **SAT**, **IELTS** va **CEFR**.

## 🚀 Quick Start (Local Development)

### Prerequisites
- Python 3.8+
- pip
- Virtual environment (recommended)

### Installation

```bash
# 1. Clone repo
git clone https://github.com/YOUR_USERNAME/tarix-ustoz-backend.git
cd tarix-ustoz-backend

# 2. Create virtual environment
python -m venv venv

# 3. Activate virtual environment
# Windows:
venv\Scripts\activate
# Mac/Linux:
source venv/bin/activate

# 4. Install dependencies
pip install -r requirements.txt

# 5. Run migrations
python manage.py migrate

# 6. Create superuser (admin)
python manage.py createsuperuser

# 7. Standart fanlar va imtihon dasturlari (idempotent)
python manage.py seed_catalog

# 8. Start development server
python manage.py runserver
```

Server runs at: `http://localhost:8000`
Admin panel: `http://localhost:8000/admin`

## 📁 Project Structure

```
testcity/
├── core/              # Django project settings
│   ├── settings.py    # Configuration
│   ├── urls.py        # URL routing
│   └── wsgi.py        # WSGI application
├── api/               # Main API app
│   ├── models.py      # Database models
│   ├── views.py       # API views
│   ├── serializers.py # DRF serializers
│   └── urls.py        # API routes
├── content/           # Content management
├── media/             # User uploads
│   ├── books/         # PDF books
│   ├── tests/         # Test files
│   └── backups/       # Database backups
├── requirements.txt   # Dependencies
└── manage.py          # Django management
```

## 📦 Dependencies

- **Django 5.0.3** - Web framework
- **djangorestframework 3.15.1** - REST API
- **django-cors-headers 4.3.1** - CORS support
- **PyMuPDF** - PDF processing
- **Pillow** - Image processing
- **beautifulsoup4** - HTML parsing
- **requests** - HTTP client

See `requirements.txt` for full list.

## 🔧 Configuration

### Environment Variables

Create `.env` file:

```env
# Django
DJANGO_SECRET_KEY=your_secret_key_here
DEBUG=False

# Database (PostgreSQL)
DB_ENGINE=django.db.backends.postgresql
DB_NAME=tarixustoz_db
DB_USER=postgres
DB_PASSWORD=your_password
DB_HOST=localhost
DB_PORT=5432

# CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000,https://yourdomain.com

# LLM
HISTORY_GENERATION_MODEL_NAME=gemini-2.0-flash
```

### Production Settings

See `core/settings.py` for environment-based configuration:
- SQLite for development
- PostgreSQL for production
- Configurable media storage

## 📡 API Endpoints

```
GET    /api/topics/          - List topics
POST   /api/topics/          - Create topic
GET    /api/topics/{id}/     - Get topic detail
PUT    /api/topics/{id}/     - Update topic
DELETE /api/topics/{id}/     - Delete topic

GET    /api/tests/           - List tests
POST   /api/tests/           - Create test
GET    /api/tests/{id}/      - Get test detail
```

Full API documentation at `/api/docs/` (when configured)

## 🐳 Docker Deployment

```bash
# Build
docker build -t tarix-ustoz-backend .

# Run
docker run -p 8000:8000 \
  -e DJANGO_SECRET_KEY=your_key \
  -e DB_ENGINE=django.db.backends.postgresql \
  tarix-ustoz-backend
```

## 📤 Production Deployment

### Namecheap VPS

```bash
# 1. SSH into server
ssh root@YOUR_SERVER_IP

# 2. Clone repo
cd /var/www
git clone https://github.com/YOUR_USERNAME/tarix-ustoz-backend.git
cd tarix-ustoz-backend

# 3. Setup
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# 4. Collect static files
python manage.py collectstatic --noinput

# 5. Migrate database
python manage.py migrate

# 6. Run with Gunicorn
pip install gunicorn
gunicorn core.wsgi --bind 0.0.0.0:8000
```

### With Nginx

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /media/ {
        alias /var/www/testcity/media/;
    }

    location /static/ {
        alias /var/www/testcity/staticfiles/;
    }
}
```

## 📊 Database Management

### SQLite (Development)
```bash
python manage.py dbshell
```

### PostgreSQL (Production)
```bash
psql -U postgres -d tarixustoz_db
```

### Backups
```bash
# Backup
python manage.py dumpdata > backup.json

# Restore
python manage.py loaddata backup.json
```

## 🧪 Testing

```bash
# Run all tests
python manage.py test

# Run specific app tests
python manage.py test api

# With coverage
pip install coverage
coverage run --source='.' manage.py test
coverage report
```

## 📝 Migrations

```bash
# Create migration
python manage.py makemigrations

# Apply migrations
python manage.py migrate

# Show migration status
python manage.py showmigrations
```

## 🔐 Security Checklist

- [ ] Set `DEBUG = False` in production
- [ ] Set strong `DJANGO_SECRET_KEY`
- [ ] Configure `ALLOWED_HOSTS`
- [ ] Use HTTPS/SSL certificate
- [ ] Set secure CORS origins
- [ ] Use environment variables for secrets
- [ ] Regular database backups
- [ ] Update dependencies regularly

## 📚 Documentation

- [Django Documentation](https://docs.djangoproject.com/)
- [Django REST Framework](https://www.django-rest-framework.org/)
- [Deployment Checklist](https://docs.djangoproject.com/en/5.0/howto/deployment/checklist/)

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📄 License

MIT License - see LICENSE file for details

## 👨‍💻 Author

**MrxForte** - Backend Developer

## 📞 Support

For issues and questions, please create an issue on GitHub.

---

**Last Updated**: 2026-11-04
