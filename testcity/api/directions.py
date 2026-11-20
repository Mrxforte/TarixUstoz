"""Direction helpers for the history pipeline (legacy module).

The pure rules live in :mod:`domain.assessment.directions`. This module only keeps
the database-bound helpers that the released v1 API still imports.
"""
from __future__ import annotations

import time

from django.db.models import Count, Sum

from api.models import DirectionCount, Question
from domain.assessment.directions import (
    COMBINATION_DIRECTIONS,
    DIRECTION_ALIASES,
    DIRECTION_TITLES,
    FACT_KEYWORDS,
    WAR_KEYWORDS,
    direction_for_combination,
    is_fact_question,
    is_war_question,
    normalize_code,
)

__all__ = [
    "COMBINATION_DIRECTIONS",
    "COUNTS_TTL_SECONDS",
    "DIRECTION_ALIASES",
    "DIRECTION_TITLES",
    "FACT_KEYWORDS",
    "WAR_KEYWORDS",
    "apply_direction",
    "book_counts",
    "direction_counts",
    "direction_for_combination",
    "is_fact_question",
    "is_war_question",
    "normalize_code",
    "recompute_book_flags",
    "refresh_counts",
]

COUNTS_TTL_SECONDS = 300
_counts_cache: dict[tuple[int, str], tuple[float, dict]] = {}


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
