import os
import time
import hashlib
import urllib.parse
import requests
from bs4 import BeautifulSoup
from django.utils import timezone
from api.models import (
    ExamSourceDomain,
    SourceVerificationRecord,
    ExaminationAuthority,
    ExaminationProgram,
    ExaminationBlueprint,
    BlueprintVersion,
    BlueprintSource,
)

OFFICIAL_ENTRY_POINTS = [
    ("https://uzbmb.uz/page/milliytest", "BMBA Milliy Test"),
    ("https://uzbmb.uz/page/namuna_m", "BMBA Namuna Testlar"),
    ("https://dtm.uz/page/tafsilot", "DTM Tafsilotlar"),
    ("https://dtm.uz/page/namuna_testlar", "DTM Namuna Testlar"),
    ("https://gov.uz/oz/uzedu", "Maktabgacha va maktab ta'limi vazirligi"),
]

class ExamSourceResearchService:
    @staticmethod
    def ensure_approved_domain(url):
        parsed = urllib.parse.urlparse(url)
        domain_name = parsed.netloc.lower()
        if not domain_name:
            return None, False
        domain, created = ExamSourceDomain.objects.get_or_create(
            domain=domain_name,
            defaults={
                'title': domain_name,
                'is_official_authority': any(off in domain_name for off in ['uzbmb.uz', 'dtm.uz', 'gov.uz']),
                'is_approved': True,
                'rate_limit_per_minute': 30,
            }
        )
        return domain, domain.is_approved

    @staticmethod
    def discover_official_sources():
        results = []
        headers = {
            'User-Agent': 'TarixUstozBot/1.0 (Official Educational Assessment Ingestion)'
        }

        for url, title_hint in OFFICIAL_ENTRY_POINTS:
            domain, is_approved = ExamSourceResearchService.ensure_approved_domain(url)
            if not is_approved:
                continue

            record, created = SourceVerificationRecord.objects.get_or_create(
                url=url,
                defaults={
                    'title': title_hint,
                    'responsible_authority': 'BMBA / DTM' if 'uzbmb' in url or 'dtm' in url else 'Maktabgacha va Maktab Ta\'limi Vazirligi',
                    'exam_period': '2024-2025',
                    'status': 'discovered',
                }
            )

            try:
                response = requests.get(url, headers=headers, timeout=10)
                if response.status_code == 200:
                    soup = BeautifulSoup(response.text, 'html.parser')
                    page_title = soup.title.string.strip() if soup.title and soup.title.string else title_hint
                    text_content = soup.get_text(separator=' ', strip=True)

                    content_hash = hashlib.sha256(text_content.encode('utf-8')).hexdigest()

                    # Check for superseded content
                    existing_matching_hash = SourceVerificationRecord.objects.filter(
                        content_hash=content_hash
                    ).exclude(id=record.id).first()

                    if existing_matching_hash and existing_matching_hash.url != record.url:
                        record.superseded_by = existing_matching_hash
                        record.status = 'superseded'
                    else:
                        record.status = 'parsed'

                    record.title = page_title
                    record.extracted_text = text_content[:10000] # Store snippet
                    record.content_hash = content_hash
                    record.is_broken = False
                    record.save()

                    # Extract document links (PDFs, docx)
                    for a_tag in soup.find_all('a', href=True):
                        href = a_tag['href']
                        full_link = urllib.parse.urljoin(url, href)
                        if any(ext in full_link.lower() for ext in ['.pdf', '.doc', '.docx']):
                            sub_domain, sub_approved = ExamSourceResearchService.ensure_approved_domain(full_link)
                            if sub_approved:
                                link_title = a_tag.get_text(strip=True) or os.path.basename(full_link)
                                SourceVerificationRecord.objects.get_or_create(
                                    url=full_link,
                                    defaults={
                                        'title': link_title[:255],
                                        'responsible_authority': record.responsible_authority,
                                        'exam_period': '2024-2025',
                                        'status': 'discovered',
                                    }
                                )

                    results.append({'url': url, 'status': record.status, 'title': record.title})
                else:
                    record.is_broken = True
                    record.status = 'unavailable'
                    record.notes = f"HTTP Status {response.status_code}"
                    record.save()
                    results.append({'url': url, 'status': 'unavailable', 'error': f"HTTP {response.status_code}"})
            except Exception as e:
                record.is_broken = True
                record.status = 'unavailable'
                record.notes = str(e)
                record.save()
                results.append({'url': url, 'status': 'failed', 'error': str(e)})

            time.sleep(0.5) # Rate limiting respect

        return results

    @staticmethod
    def retry_source(source_id):
        try:
            record = SourceVerificationRecord.objects.get(id=source_id)
            record.status = 'discovered'
            record.is_broken = False
            record.notes = "Retried by admin"
            record.save()
            return ExamSourceResearchService.discover_official_sources()
        except SourceVerificationRecord.DoesNotExist:
            return None
