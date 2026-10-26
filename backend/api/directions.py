"""Test directions ("Yo'nalishlar bo'yicha testlar") for the six cards in the app.

Every generated question stores its combination in `generation_config["combination"]`
(there are 20 of them). The cards group those combinations into readable directions;
"Urushlar va janglar" has no dedicated combination, so it is matched by war-related
words in the question text or options.
"""
from __future__ import annotations

import time

from django.db.models import Count, Q

from api.models import Question

DIRECTION_TITLES = {
    "EVENT": "Tarixiy voqealar",
    "PERSON": "Tarixiy shaxslar",
    "WAR": "Urushlar va janglar",
    "YEAR": "Yillar va sanalar",
    "CITY": "Shahar va hududlar",
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
)

COUNTS_TTL_SECONDS = 600
_counts_cache: dict[tuple[int, str], tuple[float, dict]] = {}


def apply_direction(qs, code: str):
    """Narrow a verified-question queryset to one direction. Unknown codes pass through."""
    code = (code or "").upper()
    if code == "WAR":
        return qs.filter(is_war=True)
    combinations = COMBINATION_DIRECTIONS.get(code)
    if not combinations:
        return qs
    return qs.filter(direction_code=code)


def is_war_question(text: str, *options: str) -> bool:
    """True when any war-related word appears in the question or its options."""
    haystack = " ".join([text or "", *[o or "" for o in options]]).lower()
    return any(word in haystack for word in WAR_KEYWORDS)


def direction_for_combination(combination: str) -> str:
    """Card code for a generated combination ("" when it belongs to none)."""
    for code, combos in COMBINATION_DIRECTIONS.items():
        if combination in combos:
            return code
    return ""


def direction_counts(grade: int = 0, subject: str = "") -> dict[str, int]:
    """Number of questions available per direction (cached for a few minutes).

    All-grade totals are summed per book: each book query is index-backed, while a
    single 1.1M-row scan is far slower on SQLite.
    """
    if grade:
        return _counts_for(grade, subject)

    pairs = (
        [(g, subject) for (g,) in Question.objects.order_by("grade").values_list("grade").distinct()]
        if subject
        else list(
            Question.objects.order_by("grade", "subject")
            .values_list("grade", "subject")
            .distinct()
        )
    )
    totals: dict[str, int] = {}
    for book_grade, book_subject in pairs:
        for code, count in _counts_for(book_grade, book_subject).items():
            totals[code] = totals.get(code, 0) + count
    return totals


def _counts_for(grade: int, subject: str) -> dict[str, int]:
    key = (grade, subject or "")
    cached = _counts_cache.get(key)
    if cached and time.time() - cached[0] < COUNTS_TTL_SECONDS:
        return cached[1]

    base = Question.objects.filter(verification_status="VERIFIED", grade=grade)
    if subject:
        base = base.filter(subject=subject)

    per_code = dict(base.values_list("direction_code").annotate(n=Count("id")))
    counts = {code: per_code.get(code, 0) for code in COMBINATION_DIRECTIONS}
    counts["WAR"] = base.filter(is_war=True).count()

    _counts_cache[key] = (time.time(), counts)
    return counts
