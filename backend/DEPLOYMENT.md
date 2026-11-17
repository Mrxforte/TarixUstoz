# Tarix Ustoz on Namecheap

Tarix Ustoz uses Django REST Framework and a WSGI entry point in
`passenger_wsgi.py`. Android consumes the same REST routes through Retrofit.
The release source includes 11 question banks and 11 PDFs under `content/`.

## Deployment target

Identify the current monolith's application root, runtime, routing prefix and
database before mounting the Django URLs. A Django monolith can integrate
the app and mount its URLs after verifying app labels, migration ownership,
authentication and media storage. Namecheap cPanel can also run the WSGI
application through **Setup Python App** when the existing site exposes a
separate application mount. Set the Python application root to this
`backend/` directory, the startup file to `passenger_wsgi.py`, and the entry
point to `application`. Use Python 3.11 or 3.12.

## Production settings

Configure these environment variables in the Python app and its deployment
shell. Replace the example host and paths with the actual deployment values.

| Variable | Purpose |
|---|---|
| `DJANGO_ENV=production` | Disable Django debug and enable secure cookies |
| `DJANGO_SECRET_KEY` | Unique random value of at least 50 characters |
| `DJANGO_ALLOWED_HOSTS` | Comma-separated hostnames serving the API |
| `DJANGO_CSRF_TRUSTED_ORIGINS` | HTTPS origin serving Django admin if required |
| `DJANGO_STATIC_URL` | Web-server URL for collected static files when mounted under a path |
| `DJANGO_MEDIA_URL` | External public media base URL when media is mounted separately |
| `DJANGO_MEDIA_ROOT` | Writable application media directory outside the public document root |
| `DJANGO_TRUST_PROXY_HTTPS=1` | Only when the trusted proxy strips client-supplied forwarding headers and sets the HTTPS header itself |
| `DJANGO_ENFORCE_HTTPS=1` | Optional when Django handles redirects and recognizes HTTPS behind the proxy |
| `DJANGO_HSTS_SECONDS` | Optional HSTS duration after HTTPS is verified and coordinated with the existing site |

Set `DB_ENGINE=postgresql` and `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `DB_HOST`
and `DB_PORT` for PostgreSQL. For SQLite set `SQLITE_PATH` to a persistent
writable file outside the public document root. Start the initial import
in a new Tarix Ustoz database. Activate the cPanel application's virtualenv
and run these commands from its application root.

```sh
python -m pip install -r requirements.txt
python manage.py migrate --noinput
python manage.py restore_content
python manage.py collectstatic --noinput
python manage.py check --deploy
python manage.py shell -c 'from api.models import Question; print(Question.objects.count())'
```

The expected count on a fresh import is 1,100,000. Serve the collected static
files and the public `media/pdfs/` files through the web server under their
configured URLs. Map a cover directory separately if deployed. Keep the
database, application code and media backups outside the public document
root. Restart the Python application from cPanel.

## Current production and Android build

The existing UPT application is Node.js. Tarix Ustoz runs as a separate Django
application on the same hosting account, at `https://tarix.1xbet-getapk.com/api/`.
This avoids intercepting UPT routes. Production serves PDF and static files at
`/media/` and `/static/`. Do not place the database or `.env` in that document root.

Android defaults to this HTTPS API for both debug and release builds. Override
with `-PTARIX_API_BASE_URL=https://your-host/api/` if needed. An override must use
HTTPS and end with `/`. BuildConfig supplies the only API base URL; media URLs
resolve against its host. Cached emulator PDF paths are migrated automatically.
Interrupted question downloads remain retryable instead of being marked complete.

Use JDK 17 and Gradle 8.9 with Android SDK 35. Run `gradle :app:assembleDebug
:app:testDebugUnitTest`. Release signing must use the application's existing key.

After cPanel application creation or a domain move, verify `passenger_wsgi.py`:
cPanel may replace it with a generated loader. It must import `core.wsgi.application`,
not import itself. Stop/start the Python application after routing changes.

Check all 11 book counts, the last page (`offset=99999&limit=1`) for every book,
each returned PDF over HTTPS, and quiz creation/answer/finish before release.
Gemini-powered AI answers require a separately configured server Gemini key;
question banks and normal quizzes do not require an AI key.
