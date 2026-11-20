from django.apps import AppConfig
from django.db.backends.signals import connection_created

def set_sqlite_pragma(sender, connection, **kwargs):
    if connection.vendor == 'sqlite':
        cursor = connection.cursor()
        cursor.execute('PRAGMA journal_mode=WAL;')
        cursor.execute('PRAGMA busy_timeout=60000;')
        cursor.execute('PRAGMA synchronous=NORMAL;')
        cursor.execute('PRAGMA cache_size=-65536;')
        cursor.execute('PRAGMA mmap_size=1073741824;')
        cursor.execute('PRAGMA temp_store=MEMORY;')

class ApiConfig(AppConfig):
    default_auto_field = "django.db.models.BigAutoField"
    name = "api"

    def ready(self):
        connection_created.connect(set_sqlite_pragma)
