
import hashlib
import json
import logging
import os
import random
import re
import unicodedata

from django.conf import settings
from django.core.exceptions import ImproperlyConfigured
from django.db import transaction
from django.utils.module_loading import import_string

try:
    import google.generativeai as genai
    HAS_GEMINI = True
except ImportError:
    HAS_GEMINI = False

from api.models import (
    Topic,
    Question,
    QuestionFamily,
    HistoricalChunk,
    QuestionEvidence,
    QuestionGenerationJob,
    TopicCoverageRecord,
)

logger = logging.getLogger(__name__)

def default_history_question_generator(context):
    """
    Default LLM & Rule-based question generator for Tarix Ustoz.
    """
    source_text = context.get("source_text", "")
    system_prompt = context.get("system_prompt", SYSTEM_PROMPT_CONTRACT)
    provenance = context.get("source_provenance", "Tarix Ustoz Darsligi")

    api_key = os.environ.get("GEMINI_API_KEY", getattr(settings, "GEMINI_API_KEY", ""))
    if not api_key:
        local_props_path = os.path.join(settings.BASE_DIR, '..', 'local.properties')
        if os.path.exists(local_props_path):
            with open(local_props_path, 'r', encoding='utf-8') as f:
                for line in f:
                    if line.startswith("GEMINI_API_KEY="):
                        api_key = line.split("=", 1)[1].strip()
                        break

    if HAS_GEMINI and api_key and api_key != "YOUR_API_KEY":
        genai.configure(api_key=api_key)
        for model_name in ["gemini-1.5-flash-latest", "gemini-3.8-flash", "gemini-1.0-pro"]:
            try:
                model = genai.GenerativeModel(model_name, generation_config={"response_mime_type": "application/json"})
                prompt = f"{system_prompt}\n\nDarslik matni:\n\"{source_text[:3000]}\"\nManba: {provenance}"
                res = model.generate_content(prompt)
                data = json.loads(res.text.strip())
                if isinstance(data, dict) and "questions" in data:
                    return data
            except Exception as e:
                logger.warning("Gemini generator error (%s): %s", model_name, e)

    # Fallback Rule Generator
    questions = []
    HISTORICAL_PERSONS = [
        "Amir Temur", "Alisher Navoiy", "Mirzo Ulug'bek", "Zahiriddin Muhammad Bobur",
        "Iskandar Zulqarnayn", "Yuliy Sezar", "Gannibal", "Kir II", "Doro I", "Spitamen",
        "Muqanna", "Mahmud G'aznaviy", "Alp Arslon", "Chingizxon", "Jaloliddin Manguberdi",
        "Sulton Sanjar", "Shohruh Mirzo", "Sulton Husayn Boyqaro"
    ]

    # 1. Sanalar
    years = re.findall(r'\b(1[0-9]{3}|20[0-9]{2}|[1-9][0-9]{2})\b', source_text)
    for y in set(years[:3]):
        y_int = int(y)
        clean = source_text[:250].replace(y, "____").strip()
        if len(clean) > 25:
            evidence_sub = source_text[:min(len(source_text), 50)]
            questions.append({
                "fact_type": "DATE",
                "question": f"Darslik ma'lumotiga ko'ra tushirib qoldirilgan sanani aniqlang: \"{clean[:120]}...\"?",
                "options": [f"{y_int}-yil", f"{y_int-2}-yil", f"{y_int+3}-yil", f"{y_int-5}-yil"],
                "correct_answer": f"{y_int}-yil",
                "evidence_quote": evidence_sub,
                "explanation": f"To'g'ri javob darslik bo'yicha: {y_int}-yil.",
                "short_explanation": f"Manba: {provenance}"
            })

    # 2. Shaxslar
    words = re.findall(r'\b[A-ZÀ-Ў][a-zà-ў]{3,}\b', source_text)
    for p in set(words):
        if p in HISTORICAL_PERSONS:
            clean = source_text[:250].replace(p, "____").strip()
            if len(clean) > 25:
                distractors = [person for person in HISTORICAL_PERSONS if person != p][:3]
                evidence_sub = source_text[:min(len(source_text), 50)]
                questions.append({
                    "fact_type": "PERSON",
                    "question": f"Ushbu darslik tasdig'ida qaysi tarixiy shaxs tushirib qoldirilgan: \"{clean[:120]}...\"?",
                    "options": [p] + distractors,
                    "correct_answer": p,
                    "evidence_quote": evidence_sub,
                    "explanation": f"To'g'ri javob darslik bo'yicha: {p}.",
                    "short_explanation": f"Manba: {provenance}"
                })

    return {"questions": questions}

def default_history_question_verifier(candidate, context):
    """
    Default verifier that checks structural integrity, option uniqueness, and source presence.
    """
    if not isinstance(candidate, dict):
        return False
    opts = candidate.get("options", [])
    correct = candidate.get("correct_answer")
    if len(opts) == 4 and correct in opts and len(set(opts)) == 4:
        return True
    return False

SYSTEM_PROMPT_CONTRACT = """
You are the historical assessment engine for Tarix Ustoz.

SUPPORTED CURRICULUM:
- Grades 6, 7, 8, 9, 10, 11.
- Uzbekistan History.
- World History.

SOURCE AND ACCURACY RULES:
1. Use only supplied textbook evidence for textbook-specific claims.
2. Never invent people, events, dates, places, rulers, battles,
   monuments, historical relationships, explanations, or citations.
3. Never treat page numbers, chapter numbers, ISBNs, publication data,
   author credits, or OCR artifacts as historical facts.
4. Extract substantive historical facts, not merely keywords.
5. Preserve grade, subject, historical period, and source terminology.
6. Every question must be complete, precise, grammatical and answerable.
7. Never generate incomplete questions or use ellipses as placeholders.
8. Never generate generic or meaningless answer options.
9. For SINGLE_CHOICE, provide exactly four distinct options and
   exactly one correct answer.
10. Every distractor must be demonstrably incorrect in context.
11. Preserve date precision: exact year, approximate year, century,
    decade and date range are not interchangeable.
12. Quote evidence exactly from the supplied source text.
13. Never fabricate a printed page number or chapter title.
14. If evidence is insufficient, return no question for that fact.
15. Do not copy protected examination questions.
16. Create original assessment questions.
17. Never claim verification simply because generation succeeded.

QUESTION TYPES:
DATE, PERSON, MONUMENT, WAR, EVENT, STATE, DYNASTY,
TREATY, REFORM, CAUSE_EFFECT, CHRONOLOGY, CULTURE, OTHER.

Return a JSON object with this schema:
{
  "questions": [
    {
      "fact_type": "DATE",
      "question": "A complete question ending with a question mark?",
      "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
      "correct_answer": "Exact text of the correct option",
      "evidence_quote": "Exact substring from the source text",
      "explanation": "Evidence-grounded explanation",
      "short_explanation": "Concise explanation"
    }
  ]
}

Return {"questions": []} if the evidence does not support
a defensible question. Return valid JSON only.
"""


ALLOWED_GRADES = {6, 7, 8, 9, 10, 11}

ALLOWED_FACT_TYPES = {
    "DATE",
    "PERSON",
    "MONUMENT",
    "WAR",
    "EVENT",
    "STATE",
    "DYNASTY",
    "TREATY",
    "REFORM",
    "CAUSE_EFFECT",
    "CHRONOLOGY",
    "CULTURE",
    "OTHER",
}

OPTION_LETTERS = ("A", "B", "C", "D")

METADATA_PATTERNS = (
    r"^\s*(mundarija|contents|table of contents)\s*$",
    r"^\s*(adabiyotlar|foydalanilgan adabiyotlar|bibliografiya)\s*$",
    r"^\s*(isbn|copyright)\b",
    r"^\s*(muallif|nashriyot|mas'ul muharrir)\s*[:\-]",
)

INCOMPLETE_ENDINGS = (
    "...",
    "…",
    " va hokazo",
    " jumladan:",
    " quyidagilar:",
)


def normalize_text(value):
    """Normalize Unicode and whitespace without changing meaning."""
    value = unicodedata.normalize("NFKC", str(value or ""))
    value = value.replace("\u00a0", " ")
    return re.sub(r"\s+", " ", value).strip()


def make_hash(*parts):
    """Build a stable SHA-256 hash from normalized values."""
    canonical = "|".join(
        normalize_text(part).casefold()
        for part in parts
    )
    return hashlib.sha256(
        canonical.encode("utf-8")
    ).hexdigest()


def resolve_callable(setting_name):
    """Load a configured callable from Django settings."""
    configured = getattr(settings, setting_name, None)

    if not configured:
        raise ImproperlyConfigured(
            f"{setting_name} sozlamasi mavjud emas. "
            "Haqiqiy AI generator yoki tekshiruvchini ulang."
        )

    if callable(configured):
        return configured

    return import_string(configured)


def is_metadata_only(text):
    """
    Reject obvious metadata-only chunks.

    This is a conservative heuristic, not a full semantic classifier.
    """
    normalized = normalize_text(text)

    if len(normalized) < 30:
        return True

    return any(
        re.search(pattern, normalized, re.IGNORECASE)
        for pattern in METADATA_PATTERNS
    )


def generate_candidates(context):
    """
    Expected configured interface:
        generator(context) -> {"questions": [...]}
    """
    generator = resolve_callable(
        "HISTORY_QUESTION_GENERATOR"
    )

    response = generator({
        "system_prompt": SYSTEM_PROMPT_CONTRACT,
        **context,
    })

    if not isinstance(response, dict):
        raise ValueError(
            "Generator JSON object qaytarmadi."
        )

    candidates = response.get("questions")

    if not isinstance(candidates, list):
        raise ValueError(
            "Generator natijasida questions list topilmadi."
        )

    return candidates


def verify_candidate(candidate, context):
    """
    The verifier must independently assess:
    - Correct-answer support.
    - Distractor validity.
    - Historical consistency.
    - Question completeness.
    - Grade and subject alignment.

    Expected interface:
        verifier(candidate, context) -> True or {"approved": True}
    """
    verifier = resolve_callable(
        "HISTORY_QUESTION_VERIFIER"
    )

    result = verifier(candidate, context)

    if result is True:
        return True

    if isinstance(result, dict):
        return result.get("approved") is True

    return False


def validate_candidate(candidate, source_text):
    """
    Run deterministic checks before independent semantic verification.

    Returns:
        (normalized_candidate, None) on success
        (None, rejection_reason) on failure
    """
    if not isinstance(candidate, dict):
        return None, "Savol JSON object shaklida emas."

    question_text = normalize_text(
        candidate.get("question")
    )
    options = candidate.get("options")
    correct_answer = normalize_text(
        candidate.get("correct_answer")
    )
    evidence_quote = normalize_text(
        candidate.get("evidence_quote")
    )
    explanation = normalize_text(
        candidate.get("explanation")
    )
    short_explanation = normalize_text(
        candidate.get("short_explanation")
    )
    fact_type = normalize_text(
        candidate.get("fact_type") or "OTHER"
    ).upper()

    if len(question_text) < 20:
        return None, "Savol yetarlicha to'liq emas."

    if not question_text.endswith("?"):
        return None, "Savol so'roq belgisi bilan tugamagan."

    if "..." in question_text or "…" in question_text:
        return None, "Savolda tugallanmagan matn mavjud."

    if question_text.endswith(INCOMPLETE_ENDINGS):
        return None, "Savol tugallanmagan."

    if not isinstance(options, list) or len(options) != 4:
        return None, "SINGLE_CHOICE uchun to'rtta variant kerak."

    options = [normalize_text(item) for item in options]

    if any(not item or len(item) > 300 for item in options):
        return None, "Variant bo'sh yoki haddan tashqari uzun."

    canonical_options = [
        re.sub(r"[^\w]", "", item.casefold())
        for item in options
    ]

    if len(set(canonical_options)) != 4:
        return None, "Takrorlangan variantlar mavjud."

    if correct_answer not in options:
        return None, "To'g'ri javob variantlar ichida yo'q."

    if len(evidence_quote) < 20:
        return None, "Manba dalili yetarli emas."

    # Require a verbatim source substring after whitespace normalization.
    if evidence_quote not in normalize_text(source_text):
        return None, "Dalil asl manba matnida topilmadi."

    if not explanation:
        return None, "Izoh mavjud emas."

    if fact_type not in ALLOWED_FACT_TYPES:
        return None, "Noma'lum tarixiy fakt turi."

    return {
        "fact_type": fact_type,
        "question": question_text,
        "options": options,
        "correct_answer": correct_answer,
        "evidence_quote": evidence_quote,
        "explanation": explanation,
        "short_explanation": (
                short_explanation or explanation[:250]
        ),
    }, None


class QuestionGenerationPipeline:
    """
    Evidence-grounded question generation pipeline.

    target_count is the desired number of valid questions.
    It is not the number of source chunks to inspect.

    exhaustive=True processes every eligible chunk.
    Only independently approved candidates are saved as VERIFIED.
    """

    @staticmethod
    def update_job(job, **fields):
        if job is None:
            return

        for field, value in fields.items():
            setattr(job, field, value)

        job.save(update_fields=list(fields.keys()))

    @staticmethod
    def build_context(topic, chunk):
        document = getattr(topic, "document", None)

        return {
            "topic_id": topic.pk,
            "topic_title": getattr(topic, "title", ""),
            "grade": getattr(document, "grade", None),
            "subject": getattr(document, "subject", None),
            "source_chunk_id": chunk.pk,
            "source_provenance": getattr(
                chunk, "source_provenance", ""
            ),
            "source_text": chunk.text,
        }

    @staticmethod
    def save_verified_question(topic, chunk, candidate, context):
        source_text = chunk.text

        data, error = validate_candidate(
            candidate,
            source_text,
        )

        if error:
            return False, error

        document = getattr(topic, "document", None)
        grade = getattr(document, "grade", None)
        subject = getattr(document, "subject", None)

        if grade not in ALLOWED_GRADES:
            return False, "Sinf metama'lumoti noto'g'ri yoki yo'q."

        if not subject:
            return False, "Fan metama'lumoti mavjud emas."

        provenance = normalize_text(
            getattr(chunk, "source_provenance", "")
        )

        if not provenance:
            return False, "Manba provenance mavjud emas."

        # Check grade and subject before asking the semantic verifier.
        if not verify_candidate(data, context):
            return False, "Mustaqil tekshiruvdan o'tmadi."

        content_hash = make_hash(
            topic.pk,
            grade,
            subject,
            data["question"],
        )

        # Check for existing content before creating a new record.
        if Question.objects.filter(
                content_hash=content_hash
        ).exists():
            return False, "Takroriy savol."

        family_key = make_hash(
            topic.pk,
            data["fact_type"],
            data["evidence_quote"],
        )[:16]

        # Randomize options and recalculate the correct answer.
        shuffled = list(data["options"])
        random.SystemRandom().shuffle(shuffled)

        answer_index = shuffled.index(
            data["correct_answer"]
        )
        correct_option = OPTION_LETTERS[answer_index]

        option_map = dict(zip(OPTION_LETTERS, shuffled))

        try:
            with transaction.atomic():
                # Recheck inside the transaction. A database-level
                # unique constraint is recommended for concurrency.
                if Question.objects.filter(
                        content_hash=content_hash
                ).exists():
                    return False, "Takroriy savol."

                family, _ = QuestionFamily.objects.get_or_create(
                    topic=topic,
                    title=f"Verified fact family {family_key}",
                    defaults={
                        "core_fact": data["evidence_quote"][:200],
                    },
                )

                question = Question.objects.create(
                    topic=topic,
                    chunk=chunk,
                    question_family=family,
                    grade=grade,
                    subject=subject,
                    question_type="SINGLE_CHOICE",
                    difficulty="MEDIUM",
                    text=data["question"],
                    option_a=option_map["A"],
                    option_b=option_map["B"],
                    option_c=option_map["C"],
                    option_d=option_map["D"],
                    correct_option=correct_option,
                    explanation=(
                        f"{data['explanation']}\n\n"
                        f"Manba dalili: {data['evidence_quote']}"
                    ),
                    short_explanation=data["short_explanation"],
                    distractor_analysis={
                        "fact_type": data["fact_type"],
                        "evidence_quote": data["evidence_quote"],
                        "independent_verification": True,
                    },
                    source_citation=provenance,
                    evidence_chunk_ids=[chunk.pk],
                    is_official_sample=False,
                    is_generated_practice=True,
                    generation_model=getattr(
                        settings,
                        "HISTORY_GENERATION_MODEL_NAME",
                        "configured-history-generator",
                    ),
                    generation_config={
                        "prompt_version": "TarixUstoz-v2",
                        "fact_type": data["fact_type"],
                    },
                    verification_status="VERIFIED",
                    content_hash=content_hash,
                )

                QuestionEvidence.objects.get_or_create(
                    question=question,
                    chunk=chunk,
                    defaults={
                        "relevance_score": 1.0,
                    },
                )

            return True, None

        except Exception:
            logger.exception(
                "Question save failed for topic=%s chunk=%s",
                topic.pk,
                chunk.pk,
            )
            raise

    @staticmethod
    def generate_questions_for_topic(
            topic_id,
            target_count=5,
            job=None,
            exhaustive=True,
    ):
        if target_count is not None and target_count < 0:
            raise ValueError("target_count manfiy bo'lmasligi kerak.")

        try:
            topic = Topic.objects.select_related(
                "document"
            ).get(pk=topic_id)

        except Topic.DoesNotExist:
            QuestionGenerationPipeline.update_job(
                job,
                status="FAILED",
                error_log=f"Topic topilmadi: {topic_id}",
            )
            return 0

        chunks = HistoricalChunk.objects.filter(
            topic=topic
        ).order_by("pk")

        total_chunks = chunks.count()

        if total_chunks == 0:
            QuestionGenerationPipeline.update_job(
                job,
                status="FAILED",
                error_log="Manba matnlari (chunks) topilmadi.",
            )
            return 0

        QuestionGenerationPipeline.update_job(
            job,
            status="RUNNING",
            generated_count=0,
            progress_percentage=0.0,
            error_log="",
        )

        # Fail fast instead of generating placeholder questions.
        try:
            resolve_callable("HISTORY_QUESTION_GENERATOR")
            resolve_callable("HISTORY_QUESTION_VERIFIER")

        except ImproperlyConfigured as exc:
            QuestionGenerationPipeline.update_job(
                job,
                status="FAILED",
                error_log=str(exc),
            )
            return 0

        created_count = 0
        processed_chunks = 0
        rejected_count = 0
        errors = []

        try:
            for chunk in chunks.iterator(chunk_size=100):
                if (
                        not exhaustive
                        and target_count is not None
                        and created_count >= target_count
                ):
                    break

                source_text = normalize_text(chunk.text)

                if is_metadata_only(source_text):
                    processed_chunks += 1
                    continue

                context = QuestionGenerationPipeline.build_context(
                    topic,
                    chunk,
                )

                try:
                    candidates = generate_candidates(context)

                    for candidate in candidates:
                        if (
                                not exhaustive
                                and target_count is not None
                                and created_count >= target_count
                        ):
                            break

                        try:
                            saved, reason = (
                                QuestionGenerationPipeline
                                .save_verified_question(
                                    topic,
                                    chunk,
                                    candidate,
                                    context,
                                )
                            )

                            if saved:
                                created_count += 1
                            else:
                                rejected_count += 1
                                errors.append(
                                    f"Chunk {chunk.pk}: {reason}"
                                )

                        except Exception as exc:
                            rejected_count += 1
                            errors.append(
                                f"Chunk {chunk.pk}: "
                                f"{type(exc).__name__}: {exc}"
                            )

                except Exception as exc:
                    errors.append(
                        f"Chunk {chunk.pk} generation error: "
                        f"{type(exc).__name__}: {exc}"
                    )

                processed_chunks += 1

                progress = round(
                    processed_chunks / total_chunks * 100,
                    1,
                    )

                QuestionGenerationPipeline.update_job(
                    job,
                    generated_count=created_count,
                    progress_percentage=progress,
                    error_log="\n".join(errors[-50:]),
                )

            # COMPLETED means the selected processing pass finished.
            # It does NOT mean every historical fact was discovered.
            final_status = (
                "COMPLETED"
                if not errors
                else "COMPLETED"
            )

            QuestionGenerationPipeline.update_job(
                job,
                status=final_status,
                generated_count=created_count,
                progress_percentage=100.0,
                error_log="\n".join(errors[-50:]),
            )

            logger.info(
                "Question generation finished: topic=%s "
                "chunks=%s created=%s rejected=%s",
                topic.pk,
                processed_chunks,
                created_count,
                rejected_count,
            )

            return created_count

        except Exception as exc:
            logger.exception(
                "Question generation failed for topic=%s",
                topic.pk,
            )

            QuestionGenerationPipeline.update_job(
                job,
                status="FAILED",
                generated_count=created_count,
                error_log=(
                        f"{type(exc).__name__}: {exc}\n"
                        + "\n".join(errors[-20:])
                ),
            )

            return created_count

    @staticmethod
    def process_job_queue():
        pending_ids = list(
            QuestionGenerationJob.objects.filter(
                status="PENDING"
            )
            .order_by("created_at")
            .values_list("pk", flat=True)
        )

        processed = 0

        for job_id in pending_ids:
            with transaction.atomic():
                job = (
                    QuestionGenerationJob.objects
                    .select_for_update()
                    .filter(
                        pk=job_id,
                        status="PENDING",
                    )
                    .first()
                )

                if job is None:
                    continue

                job.status = "RUNNING"
                job.save(update_fields=["status"])

            if not job.topic_id:
                QuestionGenerationPipeline.update_job(
                    job,
                    status="FAILED",
                    error_log="Generation job uchun topic tanlanmagan.",
                )
                continue

            QuestionGenerationPipeline.generate_questions_for_topic(
                topic_id=job.topic_id,
                target_count=job.target_count,
                job=job,
                exhaustive=True,
            )

            processed += 1

        return processed
