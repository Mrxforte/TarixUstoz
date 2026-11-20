"""Tarix fani — the history subject as its own domain module.

History was the only subject of the original app, so its stored subject names and
book conventions live here instead of being hard-coded in views or engines.
"""
from __future__ import annotations

from enum import StrEnum

HISTORY_SUBJECT_CODE = "tarix"


class HistoryTrack(StrEnum):
    """The three history books served by the platform."""

    UZBEKISTAN = "O'zbekiston tarixi"
    WORLD = "Jahon tarixi"
    ANCIENT_WORLD = "Qadimgi dunyo tarixi"

    @property
    def title(self) -> str:
        return self.value


HISTORY_TRACKS: tuple[HistoryTrack, ...] = (
    HistoryTrack.ANCIENT_WORLD,
    HistoryTrack.UZBEKISTAN,
    HistoryTrack.WORLD,
)

# Which track each grade reads in the school curriculum.
GRADE_TRACKS: dict[int, HistoryTrack] = {
    6: HistoryTrack.ANCIENT_WORLD,
    7: HistoryTrack.UZBEKISTAN,
    8: HistoryTrack.UZBEKISTAN,
    9: HistoryTrack.UZBEKISTAN,
    10: HistoryTrack.UZBEKISTAN,
    11: HistoryTrack.UZBEKISTAN,
}


def canonical_history_subject(subject: str, grade: int = 0) -> str:
    """Map app subject ids ("jahon", "uzbekiston", "qadimgi") to stored subject names."""
    value = (subject or "").lower()
    if grade == 6 and ("qadimgi" in value or "uz" in value or "zbek" in value):
        return HistoryTrack.ANCIENT_WORLD.value
    if "jahon" in value:
        return HistoryTrack.WORLD.value
    if "qadimgi" in value:
        return HistoryTrack.ANCIENT_WORLD.value
    if "uz" in value or "zbek" in value:
        return HistoryTrack.UZBEKISTAN.value
    return subject


def track_for_grade(grade: int) -> HistoryTrack:
    return GRADE_TRACKS.get(int(grade), HistoryTrack.UZBEKISTAN)
