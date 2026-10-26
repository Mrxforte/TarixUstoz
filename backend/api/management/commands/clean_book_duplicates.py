import os
import shutil
from django.core.management.base import BaseCommand
from django.conf import settings

class Command(BaseCommand):
    help = "Cleans duplicate book text files in media/books/txt, keeping the canonical largest file for each book."

    def handle(self, *args, **options):
        txt_dir = getattr(settings, 'BOOKS_TXT_DIR', os.path.join(settings.MEDIA_ROOT, 'books', 'txt'))
        if not os.path.exists(txt_dir):
            self.stdout.write(self.style.ERROR(f"Directory not found: {txt_dir}"))
            return

        # Canonical mapping of book groups -> canonical filename
        groups = {
            "6_Qadimgi": {
                "canonical": "6-sinf_Qadimgi_dunyo_tarixi.txt",
                "variants": ["6-sinf_Qadimgi_dunyo_tarixi.txt", "6_sinf_Qadimgi_dunyo_tarixi.txt", "6_sinf_Tarix_Qadimgi_dunyo_tarixi.txt"]
            },
            "7_Jahon": {
                "canonical": "7-sinf_Jahon_tarixi.txt",
                "variants": ["7-sinf_Jahon_tarixi.txt", "7_sinf_Jahon_tarixi.txt"]
            },
            "7_Ozbekiston": {
                "canonical": "7-sinf_Ozbekiston_tarixi.txt",
                "variants": ["7-sinf_Ozbekiston_tarixi.txt", "7_sinf_Ozbekiston_tarixi.txt"]
            },
            "8_Jahon": {
                "canonical": "8-sinf_Jahon_tarixi.txt",
                "variants": ["8-sinf_Jahon_tarixi.txt", "8_sinf_Jahon_tarixi.txt"]
            },
            "8_Ozbekiston": {
                "canonical": "8-sinf_Ozbekiston_tarixi.txt",
                "variants": ["8-sinf_Ozbekiston_tarixi.txt", "8_sinf_Ozbekiston_tarixi.txt"]
            },
            "9_Jahon": {
                "canonical": "9-sinf_Jahon_tarixi.txt",
                "variants": ["9-sinf_Jahon_tarixi.txt", "9_sinf_9_Sinf_Jahon_tarixi.txt", "9_sinf_Jahon_tarixi.txt"]
            },
            "9_Ozbekiston": {
                "canonical": "9-sinf_Ozbekiston_tarixi.txt",
                "variants": ["9-sinf_Ozbekiston_tarixi.txt", "9_sinf_Ozbekiston_tarixi.txt"]
            },
            "10_Jahon": {
                "canonical": "10-sinf_Jahon_tarixi.txt",
                "variants": ["10-sinf_Jahon_tarixi.txt", "10_sinf_Jahon_tarixi.txt"]
            },
            "10_Ozbekiston": {
                "canonical": "10-sinf_Ozbekiston_tarixi.txt",
                "variants": ["10-sinf_Ozbekiston_tarixi.txt", "10_sinf_Ozbekiston_tarixi.txt"]
            },
            "11_Jahon": {
                "canonical": "11-sinf_Jahon_tarixi.txt",
                "variants": ["11-sinf_Jahon_tarixi.txt", "11_sinf_Jahon_tarixi.txt"]
            },
            "11_Ozbekiston": {
                "canonical": "11-sinf_Ozbekiston_tarixi.txt",
                "variants": ["11-sinf_Ozbekiston_tarixi.txt", "11_sinf_Ozbekiston_tarixi.txt"]
            },
        }

        deleted_count = 0
        updated_count = 0

        for key, info in groups.items():
            canonical_name = info["canonical"]
            canonical_path = os.path.join(txt_dir, canonical_name)
            variants = info["variants"]

            # Find the largest file among variants
            best_file = None
            best_size = -1

            for var in variants:
                vpath = os.path.join(txt_dir, var)
                if os.path.exists(vpath):
                    size = os.path.getsize(vpath)
                    if size > best_size:
                        best_size = size
                        best_file = vpath

            if best_file:
                # Read best content
                with open(best_file, "r", encoding="utf-8", errors="ignore") as f:
                    best_content = f.read()

                # Ensure canonical file exists with best content
                with open(canonical_path, "w", encoding="utf-8") as f:
                    f.write(best_content)

                self.stdout.write(self.style.SUCCESS(f"[+] Canonical file '{canonical_name}' updated with best content ({best_size} bytes from '{os.path.basename(best_file)}')"))
                updated_count += 1

                # Delete other non-canonical variants
                for var in variants:
                    if var != canonical_name:
                        vpath = os.path.join(txt_dir, var)
                        if os.path.exists(vpath):
                            os.remove(vpath)
                            self.stdout.write(f"   [-] Deleted duplicate: {var}")
                            deleted_count += 1

        self.stdout.write(self.style.SUCCESS(f"\n[FINISHED] Updated {updated_count} canonical files, deleted {deleted_count} duplicate files."))
