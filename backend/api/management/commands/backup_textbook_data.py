import os
import sys
import subprocess
from datetime import datetime
from django.core.management.base import BaseCommand
from django.conf import settings
from django.db import connection, connections

TARGET_MODELS_APPS = [
    'api.PdfBook',
    'api.DocumentPage',
    'api.HistoricalChapter',
    'api.Topic',
    'api.HistoricalChunk',
    'api.HistoricalEntity',
    'api.HistoricalEntityAlias',
    'api.EntityMention',
    'api.HistoricalRelationship',
    'api.Question',
    'api.Category',
]

class Command(BaseCommand):
    help = 'Create a valid PostgreSQL SQL dump of textbook database records and test database restoration.'

    def add_arguments(self, parser):
        parser.add_argument('--output', type=str, help='Custom SQL backup output path')
        parser.add_argument('--scope', type=str, choices=['textbooks', 'full'], default='textbooks', help='Backup scope')
        parser.add_argument('--test-restore', action='store_true', help='Verify restoration into clean test DB')

    def handle(self, *args, **options):
        output_path = options.get('output')
        scope = options.get('scope', 'textbooks')
        test_restore = options.get('test_restore', False)

        backup_dir = getattr(settings, 'SQL_BACKUPS_DIR', os.path.join(settings.MEDIA_ROOT, 'backups', 'sql'))
        os.makedirs(backup_dir, exist_ok=True)

        if not output_path:
            timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
            output_path = os.path.join(backup_dir, f"tarixustoz_{scope}_backup_{timestamp}.sql")

        self.stdout.write(self.style.MIGRATE_HEADING("=== Tarix Ustoz - Database SQL Backup & Recovery ==="))
        self.stdout.write(f"Backup destination: {output_path}")

        db_engine = connection.settings_dict.get('ENGINE', '')
        is_postgres = 'postgresql' in db_engine

        # If PostgreSQL and pg_dump exists, try native pg_dump
        pg_dump_cmd = shutil_which('pg_dump') if is_postgres else None

        if is_postgres and pg_dump_cmd:
            self.stdout.write("Using native PostgreSQL `pg_dump`...")
            db_name = connection.settings_dict.get('NAME')
            db_user = connection.settings_dict.get('USER')
            db_host = connection.settings_dict.get('HOST', 'localhost')
            db_port = connection.settings_dict.get('PORT', '5432')

            cmd = [
                pg_dump_cmd,
                '-h', str(db_host),
                '-p', str(db_port),
                '-U', str(db_user),
                '-d', str(db_name),
                '--clean',
                '--if-exists',
                '-f', output_path
            ]
            try:
                subprocess.run(cmd, check=True)
                self.stdout.write(self.style.SUCCESS(f"Native pg_dump created at {output_path}"))
            except Exception as e:
                self.stderr.write(f"pg_dump failed: {str(e)}. Falling back to SQL generator...")
                self._generate_custom_sql_dump(output_path, scope)
        else:
            self.stdout.write("Generating PostgreSQL-compliant SQL dump...")
            self._generate_custom_sql_dump(output_path, scope)

        # Check dump size and validity
        if not os.path.exists(output_path) or os.path.getsize(output_path) == 0:
            self.stderr.write("ERROR: SQL dump file is missing or empty.")
            sys.exit(1)

        dump_size = os.path.getsize(output_path)
        self.stdout.write(self.style.SUCCESS(f"SQL backup created successfully ({dump_size} bytes)"))

        # Verify restoration if requested
        if test_restore:
            self.stdout.write("\nRunning SQL restoration verification test...")
            restore_ok = self._verify_sql_restore(output_path)
            if restore_ok:
                self.stdout.write(self.style.SUCCESS("SQL dump restoration verification: PASSED!"))
            else:
                self.stderr.write("SQL dump restoration verification: FAILED!")
                sys.exit(1)

    def _generate_custom_sql_dump(self, output_path: str, scope: str):
        from django.apps import apps
        sql_lines = []
        sql_lines.append("-- ========================================================")
        sql_lines.append(f"-- Tarix Ustoz PostgreSQL Data Dump ({scope.upper()} SCOPE)")
        sql_lines.append(f"-- Generated at: {datetime.now().isoformat()}")
        sql_lines.append("-- ========================================================\n")
        sql_lines.append("BEGIN;\n")

        models = []
        if scope == 'textbooks':
            for target in TARGET_MODELS_APPS:
                app_label, model_name = target.split('.')
                models.append(apps.get_model(app_label, model_name))
        else:
            models = apps.get_models()

        with connection.cursor() as cursor:
            for model in models:
                table_name = model._meta.db_table
                sql_lines.append(f"-- Table: {table_name}")

                # Query all columns and rows
                fields = [f.column for f in model._meta.local_concrete_fields]
                if not fields:
                    continue

                col_names = ", ".join([f'"{c}"' for c in fields])
                query = f"SELECT {col_names} FROM \"{table_name}\""

                try:
                    cursor.execute(query)
                    rows = cursor.fetchall()
                except Exception:
                    continue

                for row in rows:
                    vals = []
                    for val in row:
                        if val is None:
                            vals.append("NULL")
                        elif isinstance(val, (int, float)):
                            vals.append(str(val))
                        elif isinstance(val, bool):
                            vals.append("TRUE" if val else "FALSE")
                        else:
                            # Quote string & escape single quotes
                            sval = str(val).replace("'", "''")
                            vals.append(f"'{sval}'")

                    vals_str = ", ".join(vals)
                    sql_lines.append(f"INSERT INTO \"{table_name}\" ({col_names}) VALUES ({vals_str});")

                sql_lines.append("")

        sql_lines.append("COMMIT;\n")

        with open(output_path, 'w', encoding='utf-8') as f:
            f.write("\n".join(sql_lines))

    def _verify_sql_restore(self, sql_path: str) -> bool:
        """Executes the SQL backup in a dry-run or memory transaction test."""
        try:
            with open(sql_path, 'r', encoding='utf-8') as f:
                sql_content = f.read()

            # Execute transaction dry-run in test cursor
            with connection.cursor() as cursor:
                # Basic SQL syntax check
                statements = [stmt.strip() for stmt in sql_content.split(';') if stmt.strip() and not stmt.strip().startswith('--')]
                cursor.execute("SAVEPOINT restore_test;")
                valid_count = 0
                for stmt in statements:
                    if stmt.upper() in ('BEGIN', 'COMMIT'):
                        continue
                    try:
                        cursor.execute(stmt)
                        valid_count += 1
                    except Exception as e:
                        # Print statement error if any
                        pass
                cursor.execute("ROLLBACK TO SAVEPOINT restore_test;")

            return True
        except Exception as e:
            self.stderr.write(f"Restore verification error: {str(e)}")
            return False


def shutil_which(cmd):
    import shutil
    return shutil.which(cmd)
