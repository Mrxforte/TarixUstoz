"""Test directions ("Yo'nalishlar bo'yicha testlar") for the cards in the app.

Every generated question stores its combination in `generation_config["combination"]`
(there are 20 of them). The cards group those combinations into readable directions.
Two directions have no dedicated combination and are matched by vocabulary in the
question/options; they are precomputed into the `is_war` / `is_fact` flags so the
filter stays index-backed.

Counts are materialised in `DirectionCount` (one row per book and card) so the API
answers with a single cheap query instead of scanning 1.1M rows.
"""
from __future__ import annotations

import time

from django.db.models import Count, Sum

from api.models import DirectionCount, Question

DIRECTION_TITLES = {
    "EVENT": "Tarixiy voqealar",
    "PERSON": "Tarixiy shaxslar",
    "WAR": "Urushlar va janglar",
    "YEAR": "Yillar va sanalar",
    "CITY": "Shahar va hududlar",
    "FACT": "Tarixiy faktlar",
    "LOGICAL": "Mantiqiy va DTM",
}

COMBINATION_DIRECTIONS = {
    "EVENT": (
        "CAUSE_TO_EFFECT", "EFFECT_TO_CAUSE", "SOURCE_BASED",
        "MAIN_CAUSE", "TERM_TO_DEFINITION", "DEFINITION_TO_TERM",
    ),
    "PERSON": ("PERSON_TO_ACTIVITY", "ACTIVITY_TO_PERSON", "WHO_WAS_FIRST"),
    "YEAR": ("DATE_TO_EVENT", "EVENT_TO_DATE", "NUMBER_TO_MEANING", "CHRONOLOGY"),
    "CITY": ("MAP_BASED", "PLACE_TO_EVENT"),
    "LOGICAL": ("TRUE_FALSE", "ODD_ONE_OUT", "MATCHING", "COMPARE_SIMILAR", "COMPARE_DIFFERENT"),
}

WAR_KEYWORDS = (
    "urush", "jang", "safar", "hujum", "qamal", "g'alaba", "mag'lubiyat",
    "sulh", "qo'shin", "lashkar", "jangchi", "mudofaa", "bosqin",
    "muhoraba", "istilo", "yurish", "front", "armiya", "harbiy",
)

FACT_KEYWORDS = (
    "masjid", "madrasa", "maqbara", "minora", "saroy", "qal'a", "qo'rg'on",
    "obida", "me'moriy", "majmua", "yodgorlik", "haykal",
    "fakt", "asos", "manba", "tarixiy", "dastlab", "tashkil", "islohot",
    "qonun", "farmon", "shartnoma", "madaniyat", "kashfiyot", "ilm", "asar",
)

COUNTS_TTL_SECONDS = 300
_counts_cache: dict[tuple[int, str], tuple[float, dict]] = {}

# Ids used by older app builds / the home screen cards.
DIRECTION_ALIASES = {
    "TARIXIY_VOQEALAR": "EVENT",
    "TARIXIY_SHAXSLAR": "PERSON",
    "YILLAR_BOYICHA": "YEAR",
    "SHAHAR_NOMLARI": "CITY",
    "TARIXIY_OBIDALAR": "FACT",
    "TARIXIY_FAKTLAR": "FACT",
    "MONUMENT": "FACT",  # the card used to be called "Tarixiy obidalar"
}


def normalize_code(code: str) -> str:
    """Map a card id to a direction code ("tarixiy_faktlar" -> "FACT")."""
    upper = (code or "").strip().upper()
    return DIRECTION_ALIASES.get(upper, upper)


def apply_direction(qs, code: str):
    """Narrow a verified-question queryset to one direction. Unknown codes pass through."""
    code = normalize_code(code)
    if code == "WAR":
        return qs.filter(is_war=True)
    if code == "FACT":
        return qs.filter(is_fact=True)
    if code not in COMBINATION_DIRECTIONS:
        return qs
    return qs.filter(direction_code=code)


def _haystack(text: str, *options: str) -> str:
    raw = " ".join([text or "", *[o or "" for o in options]]).lower()
    return (
        raw.replace("’", "'")
        .replace("‘", "'")
        .replace("ʻ", "'")
        .replace("ʼ", "'")
        .replace("`", "'")
    )


def is_war_question(text: str, *options: str) -> bool:
    """True when any war-related word appears in the question or its options."""
    haystack = _haystack(text, *options)
    return any(word in haystack for word in WAR_KEYWORDS)


def is_fact_question(text: str, *options: str) -> bool:
    """True when the question is about a historical fact, landmark, treaty, or discovery."""
    haystack = _haystack(text, *options)
    return any(word in haystack for word in FACT_KEYWORDS)


def direction_for_combination(combination: str) -> str:
    """Card code for a generated combination ("" when it belongs to none)."""
    for code, combos in COMBINATION_DIRECTIONS.items():
        if combination in combos:
            return code
    return ""


def recompute_book_flags(grade: int = 0, subject: str = "", batch_size: int = 2000) -> int:
    """Recompute is_war, is_fact, and direction_code for stored questions."""
    qs = Question.objects.all()
    if grade:
        qs = qs.filter(grade=grade)
    if subject:
        qs = qs.filter(subject=subject)

    updated = 0
    last_id = 0
    while True:
        rows = list(
            qs.filter(id__gt=last_id)
            .order_by("id")
            .only(
                "id", "text", "option_a", "option_b", "option_c", "option_d",
                "is_war", "is_fact", "direction_code", "generation_config", "tags",
            )[:batch_size]
        )
        if not rows:
            break
        last_id = rows[-1].id
        dirty = []
        for q in rows:
            new_war = is_war_question(q.text, q.option_a, q.option_b, q.option_c, q.option_d)
            new_fact = is_fact_question(q.text, q.option_a, q.option_b, q.option_c, q.option_d)
            combo = (q.generation_config or {}).get("combination", "")
            if not combo and q.tags:
                combo = next((t for t in q.tags if direction_for_combination(t)), "")
            new_dir = direction_for_combination(combo) if combo else q.direction_code
            if q.is_war != new_war or q.is_fact != new_fact or q.direction_code != new_dir:
                q.is_war = new_war
                q.is_fact = new_fact
                q.direction_code = new_dir
                dirty.append(q)
        if dirty:
            Question.objects.bulk_update(dirty, ["is_war", "is_fact", "direction_code"], batch_size=1000)
            updated += len(dirty)
    return updated


def book_counts(grade: int, subject: str) -> dict[str, int]:
    """Real counts of one book. Slow — only used to refresh the cache."""
    base = Question.objects.filter(
        verification_status="VERIFIED", grade=grade, subject=subject
    )
    per_code = dict(base.values_list("direction_code").annotate(n=Count("id")))
    counts = {code: per_code.get(code, 0) for code in COMBINATION_DIRECTIONS}
    counts["WAR"] = base.filter(is_war=True).count()
    counts["FACT"] = base.filter(is_fact=True).count()
    return counts


def refresh_counts(grade: int = 0, subject: str = "") -> int:
    """Recompute and store the counts of one book (or every book). Returns rows written."""
    query = Question.objects.values_list("grade", "subject").distinct().order_by("grade", "subject")
    if grade:
        query = query.filter(grade=grade)
    if subject:
        query = query.filter(subject=subject)

    written = 0
    for book_grade, book_subject in list(query):
        counts = book_counts(book_grade, book_subject)
        for code, count in counts.items():
            DirectionCount.objects.update_or_create(
                grade=book_grade, subject=book_subject, code=code,
                defaults={"count": count},
            )
        _counts_cache[(book_grade, book_subject)] = (time.time(), counts)
        written += len(counts)
    return written


def direction_counts(grade: int = 0, subject: str = "") -> dict[str, int]:
    """Counts per card, read from the materialised table (computed once if empty)."""
    rows = DirectionCount.objects.all()
    if not rows.exists():
        refresh_counts()
    if grade:
        rows = rows.filter(grade=grade)
    if subject:
        rows = rows.filter(subject=subject)
    stored = dict(rows.values_list("code").annotate(total=Sum("count")))
    return {code: int(stored.get(code, 0)) for code in DIRECTION_TITLES}
