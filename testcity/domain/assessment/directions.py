"""Pure direction (yo'nalish) rules for history tests.

These constants and predicates describe how generated question combinations map
to the direction cards of the app. They carry no Django dependency so the same
rules can be used by application services, management commands and tests.
"""
from __future__ import annotations

from types import MappingProxyType

DIRECTION_TITLES = MappingProxyType({
    "EVENT": "Tarixiy voqealar",
    "PERSON": "Tarixiy shaxslar",
    "WAR": "Urushlar va janglar",
    "YEAR": "Yillar va sanalar",
    "CITY": "Shahar va hududlar",
    "FACT": "Tarixiy faktlar",
    "LOGICAL": "Mantiqiy va DTM",
})

COMBINATION_DIRECTIONS = MappingProxyType({
    "EVENT": (
        "CAUSE_TO_EFFECT", "EFFECT_TO_CAUSE", "SOURCE_BASED",
        "MAIN_CAUSE", "TERM_TO_DEFINITION", "DEFINITION_TO_TERM",
    ),
    "PERSON": ("PERSON_TO_ACTIVITY", "ACTIVITY_TO_PERSON", "WHO_WAS_FIRST"),
    "YEAR": ("DATE_TO_EVENT", "EVENT_TO_DATE", "NUMBER_TO_MEANING", "CHRONOLOGY"),
    "CITY": ("MAP_BASED", "PLACE_TO_EVENT"),
    "LOGICAL": ("TRUE_FALSE", "ODD_ONE_OUT", "MATCHING", "COMPARE_SIMILAR", "COMPARE_DIFFERENT"),
})

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

# Ids used by older app builds / the home screen cards.
DIRECTION_ALIASES = MappingProxyType({
    "TARIXIY_VOQEALAR": "EVENT",
    "TARIXIY_SHAXSLAR": "PERSON",
    "YILLAR_BOYICHA": "YEAR",
    "SHAHAR_NOMLARI": "CITY",
    "TARIXIY_OBIDALAR": "FACT",
    "TARIXIY_FAKTLAR": "FACT",
    "MONUMENT": "FACT",  # the card used to be called "Tarixiy obidalar"
})


def normalize_code(code: str) -> str:
    """Map a card id to a direction code ("tarixiy_faktlar" -> "FACT")."""
    upper = (code or "").strip().upper()
    return DIRECTION_ALIASES.get(upper, upper)


def direction_for_combination(combination: str) -> str:
    """Card code for a generated combination ("" when it belongs to none)."""
    for code, combos in COMBINATION_DIRECTIONS.items():
        if combination in combos:
            return code
    return ""


def _haystack(text: str, *options: str) -> str:
    raw = " ".join([text or "", *[o or "" for o in options]]).lower()
    return (
        raw.replace("\u2019", "'")
        .replace("\u2018", "'")
        .replace("\u00bb", "'")
        .replace("\u00ab", "'")
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


def direction_codes() -> tuple[str, ...]:
    """Every card code the app can request, in display order."""
    return tuple(DIRECTION_TITLES)
