"""Generate the 7-sinf Jahon tarixi test bank (100 000 questions, 20 combination types).

Input : content/facts/*.json      hand-authored, evidence-checked facts
        content/source/topics.json 44 paragraphs (titles, pages)
Output: content/generated/questions.ndjson   one question per line
        content/generated/report.json        per-type / per-topic distribution

Every question keeps a reference to the paragraph and page it was taken from,
so the Android client can show "Manba: §12, 53-bet".
"""
from __future__ import annotations

import json
import argparse
import random
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FACTS_DIR = ROOT / "books" / "7-sinf_Jahon_tarixi" / "facts"
OUT_DIR = ROOT / "books" / "7-sinf_Jahon_tarixi" / "generated"
TOPICS_FILE = ROOT / "books" / "7-sinf_Jahon_tarixi" / "source" / "topics.json"

GRADE = 7
SUBJECT = "jahon"
ID_PREFIX = "jh7"
BOOK_TITLE = "Jahon tarixi (V\u2013XV asrlar)"

TOTAL_TARGET = 100_000

TYPE_LABELS = {
    "DATE_TO_EVENT": "Sana → Voqea",
    "EVENT_TO_DATE": "Voqea → Sana",
    "PERSON_TO_ACTIVITY": "Shaxs → Faoliyat",
    "ACTIVITY_TO_PERSON": "Faoliyat → Shaxs",
    "TERM_TO_DEFINITION": "Atama → Ta'rif",
    "DEFINITION_TO_TERM": "Ta'rif → Atama",
    "MATCHING": "Moslashtirish (4 juftlik)",
    "CHRONOLOGY": "Xronologik tartib",
    "TRUE_FALSE": "To'g'ri/Noto'g'ri gap",
    "CAUSE_TO_EFFECT": "Sabab → Natija",
    "EFFECT_TO_CAUSE": "Natija → Sabab",
    "COMPARE_SIMILAR": "Qiyoslash (o'xshashlik)",
    "COMPARE_DIFFERENT": "Qiyoslash (farq)",
    "SOURCE_BASED": "Manba/Matn asosida",
    "MAP_BASED": "Xarita/joy nomi asosida",
    "ODD_ONE_OUT": "Qaysi biri ortiqcha",
    "WHO_WAS_FIRST": "Birinchi bo'lib kim",
    "MAIN_CAUSE": "Eng muhim sabab",
    "NUMBER_TO_MEANING": "Raqam → Ma'no",
    "PLACE_TO_EVENT": "Joy nomi → Voqea",
}

CATEGORY_BUCKET = {
    "DATE_TO_EVENT": "yillar_boyicha",
    "EVENT_TO_DATE": "yillar_boyicha",
    "CHRONOLOGY": "yillar_boyicha",
    "NUMBER_TO_MEANING": "yillar_boyicha",
    "PERSON_TO_ACTIVITY": "tarixiy_shaxslar",
    "ACTIVITY_TO_PERSON": "tarixiy_shaxslar",
    "WHO_WAS_FIRST": "tarixiy_shaxslar",
    "TERM_TO_DEFINITION": "tarixiy_voqealar",
    "DEFINITION_TO_TERM": "tarixiy_voqealar",
    "SOURCE_BASED": "tarixiy_voqealar",
    "TRUE_FALSE": "tarixiy_voqealar",
    "COMPARE_SIMILAR": "tarixiy_voqealar",
    "COMPARE_DIFFERENT": "tarixiy_voqealar",
    "PLACE_TO_EVENT": "urushlar_va_janglar",
    "MAP_BASED": "urushlar_va_janglar",
    "ODD_ONE_OUT": "urushlar_va_janglar",
    "CAUSE_TO_EFFECT": "sabab_natija",
    "EFFECT_TO_CAUSE": "sabab_natija",
    "MAIN_CAUSE": "sabab_natija",
    "MATCHING": "moslashtirish",
}

ROMAN = {
    "I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7,
    "VIII": 8, "IX": 9, "X": 10, "XI": 11, "XII": 12, "XIII": 13,
    "XIV": 14, "XV": 15, "XVI": 16, "XVII": 17, "XVIII": 18, "XIX": 19,
    "XX": 20, "XXI": 21, "XXII": 22, "XXIII": 23, "XXIV": 24, "XXV": 25,
}
ROMAN_REV = {v: k for k, v in ROMAN.items()}
ROMAN_RE = re.compile(
    r"\b(" + "|".join(sorted(ROMAN, key=len, reverse=True)) + r")\b"
)

LETTERS = "ABCD"
MULTI_LETTERS = "ABCDEFGH"


# --------------------------------------------------------------------------- data


@dataclass(eq=False)
class Fact:
    kind: str
    topic: int
    title: str
    page: int
    data: dict
    uid: str = ""

    @property
    def span(self) -> tuple[int, int] | None:
        return parse_span(self.data.get("date", ""))

    @property
    def label(self) -> str:
        return self.data.get("date_label") or self.data.get("date", "")

    @property
    def bare_label(self) -> str:
        """Date label without the locative suffix the textbook style adds ("476-yilda")."""
        label = clean(self.label)
        for suffix in ("dagi", "da", "dа"):
            if label.endswith(suffix) and len(label) > len(suffix) + 2:
                return label[: -len(suffix)].strip()
        return label


@dataclass
class Topic:
    number: int
    title: str
    page: int
    chapter: str


def parse_span(raw: str) -> tuple[int, int] | None:
    """Return (start_year, end_year) for '476', '1055-yil', 'IV asr', '1096–1270'."""
    if not raw:
        return None
    text = str(raw).replace("\u2013", "-").replace("\u2014", "-").strip()
    negative = "avv" in text
    text = text.replace("mil. avv.", "").replace("mil.avv.", "").replace("avv.", "")

    romans = ROMAN_RE.findall(text)
    years = [int(y) for y in re.findall(r"\d{1,4}", text) if len(y) <= 4]
    if years and "asr" not in text:
        start = years[0]
        end = years[1] if len(years) > 1 else years[0]
        if negative:
            return (-start, -end)
        return (start, end)
    if romans:
        nums = sorted(ROMAN[r] for r in romans)
        start = max(1, (nums[0] - 1) * 100 + 1)
        end = nums[-1] * 100
        if negative:
            return (-end, -start)
        return (start, end)
    if years:
        start = years[0]
        end = years[1] if len(years) > 1 else years[0]
        return (-start, -end) if negative else (start, end)
    return None


def century_label(span: tuple[int, int]) -> str:
    if span[0] < 0:
        return f"mil. avv. {(abs(span[0]) - 1) // 100 + 1} asr"
    mid = max(1, (span[0] + span[1]) // 2)
    return f"{ROMAN_REV.get((mid - 1) // 100 + 1, '?')} asr"


def century_index(span: tuple[int, int]) -> int:
    """Signed century number: 16 for 1501-1600, -5 for 5th century BC."""
    start = span[0]
    if start > 0:
        return (start - 1) // 100 + 1
    return -((abs(start) - 1) // 100 + 1)


def century_name(index: int) -> str:
    if index > 0:
        return f"{ROMAN_REV.get(index, '?')} asr"
    return f"mil. avv. {ROMAN_REV.get(abs(index), '?')} asr"


def year_label(year: int) -> str:
    return f"mil. avv. {abs(year)}" if year < 0 else f"{year}"


def load_topics() -> dict[int, Topic]:
    items = json.loads(TOPICS_FILE.read_text(encoding="utf-8"))
    return {
        i["number"]: Topic(i["number"], i["title"], i["book_page"], i["chapter"])
        for i in items
    }


def load_facts(topics: dict[int, Topic]) -> list[Fact]:
    facts: list[Fact] = []
    for path in sorted(FACTS_DIR.glob("*.json")):
        payload = json.loads(path.read_text(encoding="utf-8"))
        for topic in payload.get("topics", []):
            num = topic["number"]
            if num not in topics:
                continue
            for idx, item in enumerate(topic.get("items", [])):
                facts.append(
                    Fact(
                        kind=item["kind"],
                        topic=num,
                        title=topics[num].title,
                        page=topics[num].page,
                        data=item,
                        uid=f"t{num}-{item['kind']}-{idx}",
                    )
                )
    return facts


# ---------------------------------------------------------------------- question


@dataclass
class Question:
    qtype: str
    topic: int
    page: int
    text: str
    options: list[str]
    correct: int
    explanation: str
    difficulty: str = "MEDIUM"


def clean(text: str) -> str:
    text = re.sub(r"\s+", " ", str(text)).strip(" .")
    return text


CYR_LOOKALIKE = {
    "\u0430": "a", "\u0410": "A", "\u0435": "e", "\u0415": "E",
    "\u043e": "o", "\u041e": "O", "\u0441": "c", "\u0421": "C",
    "\u0440": "p", "\u0420": "P", "\u0445": "x", "\u0425": "X",
    "\u0443": "y", "\u0423": "Y", "\u0456": "i", "\u0406": "I",
    "\u0455": "s", "\u051b": "q", "\u0412": "B", "\u041d": "H",
    "\u041a": "K", "\u041c": "M", "\u0422": "T",
}
CYR_LOOKALIKE_RE = re.compile("[" + "".join(CYR_LOOKALIKE) + "]")
APOS_OG_RE = re.compile(r"([oOgG])'")


def typography(text: str) -> str:
    """Uzbek-proof typography for the strings shown to students."""
    if not text:
        return text
    if CYR_LOOKALIKE_RE.search(text):
        text = CYR_LOOKALIKE_RE.sub(lambda m: CYR_LOOKALIKE[m.group(0)], text)
    if "'" in text:
        text = APOS_OG_RE.sub("\\1\u2018", text)
        text = text.replace("'", "\u2019")
    return re.sub(r"\s+", " ", text)


def short(text: str, limit: int = 110) -> str:
    text = clean(text)
    if len(text) <= limit:
        return text
    cut = text[:limit]
    if " " in cut:
        cut = cut[: cut.rfind(" ")]
    return cut.rstrip(",;:") + "…"


def normalize(text: str) -> str:
    text = clean(text).lower().replace("\u2018", "'").replace("\u2019", "'")
    return re.sub(r"[^a-z0-9\u0400-\u04ff]+", " ", text).strip()


class Bank:
    def __init__(self, topics: dict[int, Topic], facts: list[Fact], seed: int = 20251026):
        self.topics = topics
        self.facts = facts
        self.rng = random.Random(seed)
        self.by_topic: dict[int, dict[str, list[Fact]]] = defaultdict(lambda: defaultdict(list))
        self.by_kind: dict[str, list[Fact]] = defaultdict(list)
        for fact in facts:
            self.by_topic[fact.topic][fact.kind].append(fact)
            self.by_kind[fact.kind].append(fact)

        self.dated = [f for f in facts if f.kind == "date_event" and f.span]
        self.years_by_topic: dict[int, list[int]] = defaultdict(list)
        for fact in self.dated:
            span = fact.span
            for year in range(span[0], min(span[1], span[0] + 3) + 1):
                self.years_by_topic[fact.topic].append(year)

        self.events_by_year: dict[int, set[str]] = defaultdict(set)
        for fact in self.dated:
            span = fact.span
            for year in range(span[0], min(span[1], span[0] + 3) + 1):
                self.events_by_year[year].add(normalize(fact.data["event"]))

        self.comparable = [f for f in self.dated if self.is_comparable(f)]

    @staticmethod
    def is_comparable(fact: Fact) -> bool:
        span = fact.span
        if not span or span[1] < span[0]:
            return False
        label = fact.label.lower()
        if "ming" in label:
            return False
        return bool(re.search(r"\d{3,4}", label) or "asr" in label)

    # -- helpers ---------------------------------------------------------
    def chapter_topics(self, number: int) -> list[int]:
        chapter = self.topics[number].chapter
        return [n for n, t in self.topics.items() if t.chapter == chapter]

    def pick(self, seq, k: int, rng: random.Random) -> list:
        seq = list(seq)
        if len(seq) <= k:
            return seq[:]
        return rng.sample(seq, k)

    def sibling(self, fact: Fact, kind: str, scope: str, count: int, rng: random.Random,
                exclude: set[str] = frozenset()) -> list[Fact]:
        """Pick distractor facts of the same kind, preferring the same topic/chapter."""
        pools: list[list[Fact]] = []
        if scope in ("topic", "both"):
            pools.append([f for f in self.by_topic[fact.topic].get(kind, []) if f is not fact])
        if scope in ("chapter", "both"):
            chapter = self.chapter_topics(fact.topic)
            pools.append(
                [f for f in self.by_kind.get(kind, [])
                 if f.topic in chapter and f is not fact]
            )
        pools.append([f for f in self.by_kind.get(kind, []) if f is not fact])

        chosen: list[Fact] = []
        seen = set(exclude)
        for pool in pools:
            rest = [f for f in pool if f not in chosen]
            rng.shuffle(rest)
            for candidate in rest:
                key = normalize(str(candidate.data))
                if key in seen:
                    continue
                seen.add(key)
                chosen.append(candidate)
                if len(chosen) >= count:
                    return chosen
        return chosen

    def date_distractors(self, fact: Fact, count: int, rng: random.Random) -> list[str]:
        """Near-miss dates: the confusing part of the bank."""
        span = fact.span
        if not span:
            return []
        correct = normalize(fact.bare_label)
        labels: list[str] = []

        numeric = span[0] == span[1] and span[0] > 0
        if numeric:
            base = span[0]
            offsets = rng.sample([1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 25, 30, 40, 50], 12)
            for delta in offsets:
                for year in (base - delta, base + delta):
                    if 2 <= year <= 1600:
                        label = f"{year}-yil"
                        if normalize(label) != correct:
                            labels.append(label)
        elif span[0] == span[1]:
            base = span[0]
            for delta in rng.sample([1, 2, 3, 5, 7, 10, 15, 20, 30, 40, 60], 8):
                for year in (base - delta, base + delta):
                    if 100 <= year <= 1600:
                        labels.append(f"{year}-yil")
        else:
            shift = span[1] - span[0]
            for delta in rng.sample([1, 2, 3, 5, 8, 10, 15, 20], 6):
                for start in (span[0] - delta, span[0] + delta):
                    end = start + shift
                    if 100 <= start and end <= 1600:
                        labels.append(f"{start}–{end}-yillar")
            if span[0] > 100 and span[0] % 100 != 1:
                labels.append(f"{span[0]}-yil")

        # real dates from the same topic/chapter are the most convincing distractors
        for other in self.dated:
            if other is fact:
                continue
            label = other.bare_label
            if label and normalize(label) != correct:
                labels.insert(0, label)

        dedup: list[str] = []
        seen = {correct}
        for label in labels:
            key = normalize(label)
            if key not in seen:
                seen.add(key)
                dedup.append(label)
        rng.shuffle(dedup)
        return dedup[:count]


# ------------------------------------------------------------------- generator


class Generator:
    def __init__(self, bank: Bank):
        self.bank = bank
        self.rng = random.Random(777)
        self.seen_text: set[str] = set()
        self.cursor: dict[str, int] = defaultdict(int)
        self.type_counts: Counter = Counter()
        self.topic_counts: Counter = Counter()

    # -- shared utilities ------------------------------------------------
    def explanation(self, topic: int, detail: str) -> str:
        meta = self.bank.topics[topic]
        return f"Manba: «{meta.title}» (§{topic}), {meta.page}-bet. Izoh: {clean(detail)}."

    def accept(self, q: Question) -> bool:
        if not q.options or len(q.options) < 2:
            return False
        if len(set(normalize(o) for o in q.options)) != len(q.options):
            return False
        if not (0 <= q.correct < len(q.options)):
            return False
        if len(q.text) > 480 or any(len(o) > 120 for o in q.options):
            return False
        key = normalize(q.text) + "|" + "|".join(sorted(normalize(o) for o in q.options))
        if key in self.seen_text:
            return False
        self.seen_text.add(key)
        self.type_counts[q.qtype] += 1
        self.topic_counts[q.topic] += 1
        return True

    def build_options(self, correct: str, distractors: list[str], rng: random.Random) -> tuple[list[str], int]:
        options = [short(correct, 115)] + [short(d, 115) for d in distractors]
        rng.shuffle(options)
        return options, options.index(short(correct, 115))

    def next_fact(self, kind: str, rng: random.Random) -> Fact | None:
        pool = self.bank.by_kind.get(kind, [])
        if not pool:
            return None
        idx = self.cursor[kind] % len(pool)
        self.cursor[kind] += 1
        return pool[idx]

    # -- type 1: sana -> voqea -------------------------------------------
    def date_to_event(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("date_event", rng)
        if not fact or not fact.span:
            return None
        event = clean(fact.data["event"])
        others = self.bank.sibling(
            fact, "date_event", "chapter", 6, rng,
            exclude={normalize(event)},
        )
        distractors = []
        for other in others:
            other_event = clean(other.data["event"])
            span = other.span
            if span and span[0] <= fact.span[1] and span[1] >= fact.span[0]:
                continue  # overlapping periods would make two answers defensible
            if normalize(other_event) in self.bank.events_by_year.get(fact.span[0], set()):
                continue
            if normalize(other_event) == normalize(event):
                continue
            distractors.append(other_event)
            if len(distractors) == 3:
                break
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(event, distractors, rng)
        templates = [
            "{d}da qanday voqea yuz bergan?",
            "{d} qaysi voqea bilan bog‘liq?",
            "{d}da nima sodir bo‘lgan?",
            "Darslik ma’lumotlariga ko‘ra, {d}da qaysi voqea amalga oshgan?",
            "{d}dagi voqeani aniqlang.",
            "«{t}» mavzusi bo‘yicha {d}da yuz bergan voqeani toping.",
            "{d}da tarixda qanday o‘zgarish sodir bo‘lgan?",
        ]
        text = rng.choice(templates).format(d=fact.bare_label, t=fact.title)
        return Question("DATE_TO_EVENT", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{fact.bare_label} — {event}"))

    # -- type 2: voqea -> sana -------------------------------------------
    def event_to_date(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("date_event", rng)
        if not fact or not fact.span:
            return None
        event = clean(fact.data["event"])
        distractors = self.bank.date_distractors(fact, 3, rng)
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(fact.bare_label, distractors[:3], rng)
        templates = [
            "«{e}» qachon sodir bo‘lgan?",
            "«{e}» voqeasi qaysi sanaga to‘g‘ri keladi?",
            "Darslikda «{e}» voqeasi qaysi yil bilan bog‘langan?",
            "«{e}» — bu qaysi davrda yuz bergan?",
            "«{m}» mavzusi bo‘yicha «{e}» qachon yuz bergan?",
            "«{e}» voqeasining yilini aniqlang.",
        ]
        text = rng.choice(templates).format(e=event, m=fact.title)
        return Question("EVENT_TO_DATE", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"«{event}» — {fact.bare_label}."),
                        "HARD")

    # -- type 3: shaxs -> faoliyat ---------------------------------------
    def person_to_activity(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("person", rng)
        if not fact:
            return None
        person = clean(fact.data["person"])
        activity = clean(fact.data["activity"])
        others = self.bank.sibling(fact, "person", "chapter", 5, rng,
                                   exclude={normalize(activity)})
        distractors = [clean(o.data["activity"]) for o in others
                       if normalize(o.data["person"]) != normalize(person)
                       and normalize(clean(o.data["activity"])) != normalize(activity)
                       and normalize(person) not in normalize(o.data["activity"])]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(activity, distractors[:3], rng)
        templates = [
            "{p}ning faoliyatini aniqlang.",
            "{p} kim va u nima qilgan?",
            "Darslikda {p} qanday faoliyati bilan tilga olinadi?",
            "{p} bilan bog‘liq to‘g‘ri ma’lumotni toping.",
            "«{m}» mavzusida {p} haqida qanday ma’lumot berilgan?",
            "{p}ning tarixdagi o‘rni qanday tavsiflanadi?",
        ]
        text = rng.choice(templates).format(p=person, m=fact.title)
        role = clean(fact.data.get("role", ""))
        detail = f"{person} — {role}. {activity}" if role else f"{person}: {activity}"
        return Question("PERSON_TO_ACTIVITY", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, detail))

    # -- type 4: faoliyat -> shaxs ---------------------------------------
    def activity_to_person(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("person", rng)
        if not fact:
            return None
        person = clean(fact.data["person"])
        activity = clean(fact.data["activity"])
        others = self.bank.sibling(fact, "person", "chapter", 5, rng,
                                   exclude={normalize(person)})
        distractors = [clean(o.data["person"]) for o in others
                       if normalize(o.data["person"]) != normalize(person)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(person, distractors[:3], rng)
        templates = [
            "«{a}» — bu kimning faoliyati?",
            "{a}. Bu ma’lumot qaysi shaxsga tegishli?",
            "Darslikda «{a}» deb ta’riflangan shaxsni toping.",
            "«{m}» mavzusida «{a}» faoliyati kimga tegishli?",
            "Qaysi shaxs «{a}» ishi bilan tanilgan?",
        ]
        text = rng.choice(templates).format(a=activity, m=fact.title)
        return Question("ACTIVITY_TO_PERSON", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{activity} — {person}."))

    # -- type 5: atama -> ta'rif -----------------------------------------
    def term_to_definition(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("term", rng)
        if not fact:
            return None
        term = clean(fact.data["term"])
        definition = clean(fact.data["definition"])
        others = self.bank.sibling(fact, "term", "chapter", 5, rng,
                                   exclude={normalize(definition)})
        distractors = [clean(o.data["definition"]) for o in others
                       if normalize(o.data["definition"]) != normalize(definition)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(definition, distractors[:3], rng)
        templates = [
            "«{t}» nima?",
            "«{t}» atamasining ta’rifini toping.",
            "Darslikda «{t}» qanday izohlanadi?",
            "{t} — bu nima?",
            "«{m}» mavzusida «{t}» atamasi qanday ma’noda qo‘llangan?",
            "Darslikning «{m}» paragrafida «{t}» nima deb ta’riflanadi?",
            "«{t}» so‘zi bilan ataladigan tarixiy tushunchani aniqlang.",
        ]
        text = rng.choice(templates).format(t=term, m=fact.title)
        return Question("TERM_TO_DEFINITION", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{term} — {definition}."))

    # -- type 6: ta'rif -> atama -----------------------------------------
    def definition_to_term(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("term", rng)
        if not fact:
            return None
        term = clean(fact.data["term"])
        definition = clean(fact.data["definition"])
        others = self.bank.sibling(fact, "term", "chapter", 5, rng,
                                   exclude={normalize(term)})
        distractors = [clean(o.data["term"]) for o in others
                       if normalize(o.data["term"]) != normalize(term)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(term, distractors[:3], rng)
        templates = [
            "«{d}» — bu qaysi atama?",
            "«{d}» deb nimaga aytiladi?",
            "Darslikdagi ta’rif qaysi atamaga tegishli: «{d}»?",
            "Qaysi tushuncha «{d}» deb ta’riflanadi?",
            "«{m}» mavzusidagi «{d}» ta’rifi qaysi atamaga mos keladi?",
        ]
        text = rng.choice(templates).format(d=definition, m=fact.title)
        return Question("DEFINITION_TO_TERM", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{definition} — {term}."))

    # -- type 7: moslashtirish -------------------------------------------
    def matching(self, rng: random.Random) -> Question | None:
        topic_numbers = [n for n in self.bank.topics if len(self.bank.by_topic[n].get("person", [])) >= 4]
        if not topic_numbers:
            return None
        topic = rng.choice(topic_numbers)
        pool = self.bank.by_topic[topic]["person"]
        if len(pool) < 5:
            chapter = self.bank.chapter_topics(topic)
            pool = [p for n in chapter for p in self.bank.by_topic[n].get("person", [])]
        people = self.bank.pick(pool, 4, rng)
        if len(people) < 4:
            return None
        activities = [clean(p.data["activity"]) for p in people]
        if len({normalize(a) for a in activities}) < 4:
            return None
        people = list(people)
        rng.shuffle(people)
        activities = [clean(p.data["activity"]) for p in people]
        if len({normalize(a) for a in activities}) < 4:
            return None
        answers = list(range(4))
        rng.shuffle(answers)  # answers[i] = index of the activity shown for person i

        correct_map = ", ".join(f"{i + 1}-{LETTERS[answers[i]]}" for i in range(4))
        listed = "\n".join(
            f"{i + 1}) {short(clean(p.data['person']), 40)}" for i, p in enumerate(people)
        )
        listed_answers = "\n".join(
            f"{LETTERS[j]}) {short(activities[j], 60)}" for j in range(4)
        )
        text = (
            "Quyidagi shaxslar va ularning faoliyatini moslashtiring:\n"
            f"{listed}\n{listed_answers}"
        )

        options = [correct_map]
        guard = 0
        while len(options) < 4 and guard < 40:
            guard += 1
            perm = answers[:]
            rng.shuffle(perm)
            candidate = ", ".join(f"{i + 1}-{LETTERS[perm[i]]}" for i in range(4))
            if candidate not in options:
                options.append(candidate)
        if len(options) < 4:
            return None
        correct_index = options.index(correct_map)
        shuffled = options[:]
        rng.shuffle(shuffled)
        return Question(
            "MATCHING", topic, self.bank.topics[topic].page, text, shuffled,
            shuffled.index(correct_map),
            self.explanation(topic, "To‘g‘ri moslashtirish: " + correct_map + ". " +
                             "; ".join(f"{clean(p.data['person'])} — {short(clean(p.data['activity']), 60)}"
                                       for p in people)),
            "HARD",
        )

    # -- type 8: xronologik tartib ---------------------------------------
    def chronology(self, rng: random.Random) -> Question | None:
        topic_numbers = [n for n in self.bank.topics if len(self.bank.by_topic[n].get("date_event", [])) >= 4]
        if not topic_numbers:
            return None
        topic = rng.choice(topic_numbers)
        facts = self.bank.pick(self.bank.by_topic[topic]["date_event"], 6, rng)
        dated = [f for f in facts if f.span]
        if len(dated) < 4:
            return None
        # keep 4 events with distinct start years
        chosen: list[Fact] = []
        years: set[int] = set()
        for fact in dated:
            year = fact.span[0]
            if year in years:
                continue
            years.add(year)
            chosen.append(fact)
            if len(chosen) == 4:
                break
        if len(chosen) < 4:
            return None

        ordered = sorted(chosen, key=lambda f: f.span[0])
        described = "\n".join(f"{i + 1}) {short(clean(f.data['event']), 70)}"
                              for i, f in enumerate(chosen))
        correct_order = "-".join(str(chosen.index(f) + 1) for f in ordered)
        options = [correct_order]
        guard = 0
        while len(options) < 4 and guard < 60:
            guard += 1
            perm = rng.sample(range(1, 5), 4)
            candidate = "-".join(str(x) for x in perm)
            if candidate not in options:
                options.append(candidate)
        if len(options) < 4:
            return None
        rng.shuffle(options)
        detail = "; ".join(f"{f.bare_label} — {short(clean(f.data['event']), 50)}" for f in ordered)
        return Question(
            "CHRONOLOGY", topic, self.bank.topics[topic].page,
            "Quyidagi voqealarni xronologik tartibda joylashtiring:\n" + described,
            options, options.index(correct_order),
            self.explanation(topic, "To‘g‘ri ketma-ketlik: " + correct_order + ". " + detail),
            "HARD",
        )

    # -- type 9: to'g'ri / noto'g'ri -------------------------------------
    def true_false(self, rng: random.Random) -> Question | None:
        kinds = [k for k in ["date_event", "person", "term", "place", "cause"] if self.bank.by_kind.get(k)]
        fact = self.next_fact(rng.choice(kinds), rng)
        if not fact:
            return None

        other = self.bank.sibling(fact, fact.kind, "chapter", 1, rng)
        if not other:
            return None
        other = other[0]

        if fact.kind == "date_event":
            name = clean(fact.data["event"])
            true_text = f"{fact.bare_label}da {name}."
            span = other.span
            if not span or span == fact.span:
                return None
            false_text = f"{other.bare_label}da {name}."
        elif fact.kind == "person":
            person = clean(fact.data["person"])
            true_text = f"{person} — {clean(fact.data['role'])}, {clean(fact.data['activity'])}."
            false_text = f"{person} — {clean(other.data['activity'])}."
        elif fact.kind == "term":
            term = clean(fact.data["term"])
            true_text = f"{term} — {clean(fact.data['definition'])}."
            false_text = f"{term} — {clean(other.data['definition'])}."
        elif fact.kind == "place":
            place = clean(fact.data["place"])
            true_text = f"{place} — {clean(fact.data['event'])}."
            false_text = f"{place} — {clean(other.data['event'])}."
        else:
            cause = clean(fact.data["cause"])
            true_text = f"{cause} natijasida {clean(fact.data['effect'])}."
            false_text = f"{cause} natijasida {clean(other.data['effect'])}."

        if normalize(true_text) == normalize(false_text):
            return None

        make_true = rng.random() < 0.5
        statement = true_text if make_true else false_text
        templates = [
            "Quyidagi fikr to‘g‘rimi: «{s}»?",
            "«{s}» degan fikr darslikka mos keladimi?",
            "Fikrning haqiqatga mosligini aniqlang: «{s}».",
        ]
        text = rng.choice(templates).format(s=statement)
        verdict = "to‘g‘ri" if make_true else "noto‘g‘ri"
        return Question(
            "TRUE_FALSE", fact.topic, fact.page, text,
            ["To‘g‘ri", "Noto‘g‘ri"], 0 if make_true else 1,
            self.explanation(fact.topic, f"Bu fikr {verdict}. Darslikdagi ma’lumot: {true_text}"),
            "MEDIUM",
        )

    # -- type 10: sabab -> natija ----------------------------------------
    def cause_to_effect(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("cause", rng)
        if not fact:
            return None
        cause = clean(fact.data["cause"])
        effect = clean(fact.data["effect"])
        others = self.bank.sibling(fact, "cause", "chapter", 5, rng,
                                   exclude={normalize(effect)})
        distractors = [clean(o.data["effect"]) for o in others
                       if normalize(o.data["effect"]) != normalize(effect)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(effect, distractors[:3], rng)
        templates = [
            "«{c}» — buning natijasi nima bo‘lgan?",
            "{c}. Bu jarayon qanday oqibatga olib kelgan?",
            "Darslikda «{c}» natijasida nima yuz bergani ko‘rsatilgan?",
            "«{m}» mavzusida «{c}» qanday oqibatga olib kelgan?",
            "«{c}» tufayli qanday natija yuz bergan?",
        ]
        text = rng.choice(templates).format(c=cause, m=fact.title)
        return Question("CAUSE_TO_EFFECT", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{cause} → {effect}."))

    # -- type 11: natija -> sabab ----------------------------------------
    def effect_to_cause(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("cause", rng)
        if not fact:
            return None
        cause = clean(fact.data["cause"])
        effect = clean(fact.data["effect"])
        others = self.bank.sibling(fact, "cause", "chapter", 5, rng,
                                   exclude={normalize(cause)})
        distractors = [clean(o.data["cause"]) for o in others
                       if normalize(o.data["cause"]) != normalize(cause)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(cause, distractors[:3], rng)
        templates = [
            "«{e}» holatining sababi nima?",
            "Nima uchun «{e}» yuz bergan?",
            "Darslikda «{e}»ning asosiy sababi sifatida nima ko‘rsatilgan?",
            "«{m}» mavzusida «{e}»ning sababi nimada?",
            "Nima sababdan «{e}» holati kelib chiqqan?",
        ]
        text = rng.choice(templates).format(e=effect, m=fact.title)
        return Question("EFFECT_TO_CAUSE", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{effect} ← {cause}."))

    # -- type 12: qiyoslash (o'xshashlik) --------------------------------
    def compare_similar(self, rng: random.Random) -> Question | None:
        pool = self.bank.comparable
        if len(pool) < 8:
            return None
        first = rng.choice(pool)
        century = century_index(first.span)
        mates = [f for f in pool if f is not first and century_index(f.span) == century]
        if not mates:
            return None
        second = rng.choice(mates)
        if second.topic == first.topic and rng.random() < 0.7:
            second = rng.choice(mates)
        label = century_name(century)
        correct = f"ikkala voqea ham {label}da sodir bo‘lgan"
        distractors = [
            "voqealar bir yilda sodir bo‘lgan",
            f"voqealar o‘rtasida 100 yildan ortiq farq bor",
            "voqealar turli asrlarga tegishli",
        ]
        options, correct_idx = self.build_options(correct, distractors, rng)
        text = (f"«{short(clean(first.data['event']), 80)}» va "
                f"«{short(clean(second.data['event']), 80)}» voqealari o‘rtasidagi o‘xshashlikni aniqlang.")
        return Question("COMPARE_SIMILAR", first.topic, first.page, text, options, correct_idx,
                        self.explanation(first.topic, f"Birinchi voqea — {first.bare_label} ({self.bank.topics[first.topic].title}), "
                                         f"ikkinchisi — {second.bare_label} ({self.bank.topics[second.topic].title}). Ikkalasi ham {label}da."),
                        "HARD")

    # -- type 13: qiyoslash (farq) ---------------------------------------
    def compare_different(self, rng: random.Random) -> Question | None:
        pool = self.bank.comparable
        if len(pool) < 8:
            return None
        first = rng.choice(pool)
        others = [f for f in pool if f is not first and f.span != first.span
                  and f.bare_label != first.bare_label]
        if not others:
            return None
        second = rng.choice(others)
        correct = f"birinchisi {first.bare_label}da, ikkinchisi {second.bare_label}da sodir bo‘lgan"
        distractors = [
            f"birinchisi {second.bare_label}da, ikkinchisi {first.bare_label}da sodir bo‘lgan",
            f"ikkalasi ham {first.bare_label}da sodir bo‘lgan",
            f"ikkalasi ham {second.bare_label}da sodir bo‘lgan",
        ]
        options, correct_idx = self.build_options(correct, distractors, rng)
        text = (f"«{short(clean(first.data['event']), 80)}» va "
                f"«{short(clean(second.data['event']), 80)}» voqealari o‘rtasidagi farqni aniqlang.")
        return Question("COMPARE_DIFFERENT", first.topic, first.page, text, options, correct_idx,
                        self.explanation(first.topic, f"{first.bare_label} — {clean(first.data['event'])}; "
                                         f"{second.bare_label} — {clean(second.data['event'])}."),
                        "HARD")

    # -- type 14: manba / matn asosida ------------------------------------
    def source_based(self, rng: random.Random) -> Question | None:
        fact = rng.choice(self.bank.facts)
        if fact.kind == "date_event":
            statement = clean(fact.data["event"])
        elif fact.kind == "person":
            statement = clean(fact.data["activity"])
        elif fact.kind == "term":
            statement = clean(fact.data["definition"])
        elif fact.kind == "place":
            statement = clean(fact.data["event"])
        elif fact.kind == "cause":
            statement = clean(fact.data["effect"])
        else:
            statement = clean(fact.data["meaning"])

        chapter = self.bank.chapter_topics(fact.topic)
        candidates = [n for n in chapter if n != fact.topic]
        if len(candidates) < 3:
            candidates = [n for n in self.bank.topics if n != fact.topic]
        picks = self.bank.pick(candidates, 3, rng)
        if len(picks) < 3:
            return None
        correct = self.bank.topics[fact.topic].title
        distractors = [self.bank.topics[n].title for n in picks]
        options, correct_idx = self.build_options(correct, distractors, rng)
        templates = [
            "«{s}» haqidagi ma’lumot darslikning qaysi mavzusida berilgan?",
            "«{s}» — bu qaysi paragrafdagi ma’lumot?",
            "Manba: «{s}». Bu fikr qaysi mavzuga oid?",
            "Darslik matnidagi «{s}» qaysi paragrafga tegishli?",
            "«{s}» ma’lumoti qaysi mavzuni o‘rganishda uchraydi?",
        ]
        text = rng.choice(templates).format(s=short(statement, 120))
        return Question("SOURCE_BASED", fact.topic, fact.page, text, options, correct_idx,
                        self.explanation(fact.topic, f"Bu ma’lumot «{correct}» mavzusida keltirilgan."),
                        "HARD")

    # -- helper for place facts --------------------------------------------
    def _place_fact(self, rng: random.Random) -> Fact | None:
        return self.next_fact("place", rng)

    # -- type 15: xarita / joy nomi ---------------------------------------
    def map_based(self, rng: random.Random) -> Question | None:
        fact = self._place_fact(rng)
        if not fact:
            return None
        place = clean(fact.data["place"])
        event = clean(fact.data["event"])
        others = self.bank.sibling(fact, "place", "chapter", 5, rng,
                                   exclude={normalize(place)})
        distractors = [clean(o.data["place"]) for o in others
                       if normalize(o.data["place"]) != normalize(place)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(place, distractors[:3], rng)
        templates = [
            "«{e}» qaysi joy nomi bilan bog‘liq?",
            "Xaritada qaysi joy «{e}» bilan belgilanadi?",
            "Darslikda «{e}» qaysi hudud nomi orqali ko‘rsatilgan?",
            "«{m}» mavzusida «{e}» qaysi joy bilan bog‘lanadi?",
        ]
        text = rng.choice(templates).format(e=event, m=fact.title)
        return Question("MAP_BASED", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{event} — {place} bilan bog‘liq."))

    # -- type 16: qaysi biri ortiqcha -------------------------------------
    def odd_one_out(self, rng: random.Random) -> Question | None:
        topics = [n for n in self.bank.topics if self.bank.by_topic[n].get("person")]
        if len(topics) < 2:
            return None
        topic = rng.choice(topics)
        insiders = self.bank.pick(self.bank.by_topic[topic]["person"], 3, rng)
        if len(insiders) < 3:
            return None
        outsider_pool = [f for f in self.bank.by_kind["person"] if f.topic != topic]
        if not outsider_pool:
            return None
        outsider = rng.choice(outsider_pool)
        options = [clean(f.data["person"]) for f in insiders] + [clean(outsider.data["person"])]
        if len({normalize(o) for o in options}) < 4:
            return None
        rng.shuffle(options)
        correct = options.index(clean(outsider.data["person"]))
        text = (f"Quyidagi shaxslardan qaysi biri «{self.bank.topics[topic].title}» "
                f"mavzusiga oid emas?")
        return Question("ODD_ONE_OUT", topic, self.bank.topics[topic].page, text, options, correct,
                        self.explanation(topic, f"{clean(outsider.data['person'])} — «{self.bank.topics[outsider.topic].title}» mavzusiga tegishli."),
                        "HARD")

    # -- type 17: birinchi bo'lib kim / qaysi ------------------------------
    def who_was_first(self, rng: random.Random) -> Question | None:
        pool = [f for f in self.bank.comparable
                if f.span and f.span[0] == f.span[1]]
        if len(pool) < 8:
            return None
        first = rng.choice(pool)
        others = [f for f in pool if f is not first and f.span[0] > first.span[1]]
        if len(others) < 3:
            return None
        picks = self.bank.pick(others, 3, rng)
        options_facts = [first] + picks
        options = [short(clean(f.data["event"]), 90) for f in options_facts]
        rng.shuffle(options)
        correct = options.index(short(clean(first.data["event"]), 90))
        text = "Quyidagi voqealardan qaysi biri avval sodir bo‘lgan?"
        return Question("WHO_WAS_FIRST", first.topic, first.page, text, options, correct,
                        self.explanation(first.topic, "Eng avvalgi voqea: "
                                         + clean(first.data["event"]) + f" ({first.bare_label})."),
                        "HARD")

    # -- type 18: eng muhim sabab -----------------------------------------
    def main_cause(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("cause", rng)
        if not fact:
            return None
        effect = clean(fact.data["effect"])
        cause = clean(fact.data["cause"])
        others = self.bank.sibling(fact, "cause", "chapter", 5, rng,
                                   exclude={normalize(cause)})
        distractors = [clean(o.data["cause"]) for o in others
                       if normalize(o.data["cause"]) != normalize(cause)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(cause, distractors[:3], rng)
        templates = [
            "Darslikda «{e}»ning eng muhim sababi sifatida nima ko‘rsatilgan?",
            "«{e}» holatini keltirib chiqargan asosiy omilni aniqlang.",
            "«{e}» — buning eng muhim sababi qaysi?",
        ]
        text = rng.choice(templates).format(e=effect)
        return Question("MAIN_CAUSE", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"Asosiy sabab: {cause}."),
                        "HARD")

    # -- type 19: raqam -> ma'no -------------------------------------------
    def number_to_meaning(self, rng: random.Random) -> Question | None:
        fact = self.next_fact("number", rng)
        if not fact:
            return None
        number = clean(fact.data["number"])
        unit = clean(fact.data.get("unit", ""))
        meaning = clean(fact.data["meaning"])
        others = self.bank.sibling(fact, "number", "chapter", 8, rng,
                                   exclude={normalize(meaning)})
        pool = [clean(o.data["meaning"]) for o in others
                if normalize(o.data["meaning"]) != normalize(meaning)]
        if len(pool) < 3:
            return None
        distractors = self.bank.pick(pool, 3, rng)
        options, correct = self.build_options(meaning, distractors, rng)
        display = f"{number} {unit}".strip()
        templates = [
            "Darslikda «{n}» nimani bildiradi?",
            "«{n}» qaysi ma’noda keltirilgan?",
            "«{n}» — bu nimani ko‘rsatadi?",
            "«{m}» mavzusida «{n}» nimani bildiradi?",
            "Darslikda «{n}» qaysi ma’lumot uchun keltirilgan?",
        ]
        text = rng.choice(templates).format(n=display, m=fact.title)
        return Question("NUMBER_TO_MEANING", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{display} — {meaning}."))

    # -- type 20: joy nomi -> voqea ----------------------------------------
    def place_to_event(self, rng: random.Random) -> Question | None:
        fact = self._place_fact(rng)
        if not fact:
            return None
        place = clean(fact.data["place"])
        event = clean(fact.data["event"])
        others = self.bank.sibling(fact, "place", "chapter", 5, rng,
                                   exclude={normalize(event)})
        distractors = [clean(o.data["event"]) for o in others
                       if normalize(o.data["event"]) != normalize(event)]
        if len(distractors) < 3:
            return None
        options, correct = self.build_options(event, distractors[:3], rng)
        templates = [
            "«{p}» bilan bog‘liq voqeani aniqlang.",
            "«{p}» nomi qaysi voqea bilan tarixda qolgan?",
            "Darslikda «{p}» qanday voqea orqali tilga olinadi?",
            "«{m}» mavzusida «{p}» nomi qaysi voqea bilan bog‘lanadi?",
            "Xarita bo‘yicha «{p}» joyida qanday voqea yuz bergan?",
        ]
        text = rng.choice(templates).format(p=place, m=fact.title)
        return Question("PLACE_TO_EVENT", fact.topic, fact.page, text, options, correct,
                        self.explanation(fact.topic, f"{place} — {event}."))

    # -- driver ------------------------------------------------------------
    METHODS = {
        "DATE_TO_EVENT": "date_to_event",
        "EVENT_TO_DATE": "event_to_date",
        "PERSON_TO_ACTIVITY": "person_to_activity",
        "ACTIVITY_TO_PERSON": "activity_to_person",
        "TERM_TO_DEFINITION": "term_to_definition",
        "DEFINITION_TO_TERM": "definition_to_term",
        "MATCHING": "matching",
        "CHRONOLOGY": "chronology",
        "TRUE_FALSE": "true_false",
        "CAUSE_TO_EFFECT": "cause_to_effect",
        "EFFECT_TO_CAUSE": "effect_to_cause",
        "COMPARE_SIMILAR": "compare_similar",
        "COMPARE_DIFFERENT": "compare_different",
        "SOURCE_BASED": "source_based",
        "MAP_BASED": "map_based",
        "ODD_ONE_OUT": "odd_one_out",
        "WHO_WAS_FIRST": "who_was_first",
        "MAIN_CAUSE": "main_cause",
        "NUMBER_TO_MEANING": "number_to_meaning",
        "PLACE_TO_EVENT": "place_to_event",
    }

    def produce(self, qtype: str) -> Question | None:
        method = getattr(self, self.METHODS[qtype])
        for _ in range(6):
            question = method(self.rng)
            if question and self.accept(question):
                return question
        return None


def question_row(q: Question, topics: dict[int, Topic], seq: int) -> dict:
    options = q.options[:]
    correct = q.correct
    while len(options) < 4:
        options.append("—")
    letters = ["A", "B", "C", "D"]
    topic = topics[q.topic]
    options = [typography(o) for o in options]
    return {
        "external_id": f"{ID_PREFIX}-{q.qtype[:4].lower()}-{seq:06d}",
        "grade_id": GRADE,
        "subject_id": SUBJECT,
        "topic_number": q.topic,
        "question_type": q.qtype,
        "category_type": CATEGORY_BUCKET[q.qtype],
        "text": typography(q.text),
        "option_a": options[0],
        "option_b": options[1],
        "option_c": options[2],
        "option_d": options[3],
        "correct_option": letters[correct],
        "explanation": typography(q.explanation),
        "difficulty": q.difficulty,
        "source_page": q.page,
        "source_topic": typography(topic.title),
    }


def main(argv: list[str]) -> int:
    global FACTS_DIR, OUT_DIR, TOPICS_FILE, GRADE, SUBJECT, ID_PREFIX

    parser = argparse.ArgumentParser(
        description="Generate a question bank for one textbook"
    )
    parser.add_argument("target", nargs="?", type=int, default=TOTAL_TARGET)
    parser.add_argument("--facts", default=str(FACTS_DIR))
    parser.add_argument("--topics", default=str(TOPICS_FILE))
    parser.add_argument("--out", default=str(OUT_DIR))
    parser.add_argument("--grade", type=int, default=GRADE)
    parser.add_argument("--subject", default=SUBJECT)
    parser.add_argument("--prefix", default=ID_PREFIX)
    args = parser.parse_args(argv)

    FACTS_DIR = Path(args.facts)
    OUT_DIR = Path(args.out)
    TOPICS_FILE = Path(args.topics)
    GRADE = args.grade
    SUBJECT = args.subject
    ID_PREFIX = args.prefix

    target = args.target
    topics = load_topics()
    facts = load_facts(topics)
    if not facts:
        print("No facts found in content/facts/*.json", file=sys.stderr)
        return 1

    bank = Bank(topics, facts)
    print(f"topics: {len(topics)}, facts: {len(facts)}")
    print("facts by kind:", dict(Counter(f.kind for f in facts)))

    gen = Generator(bank)
    OUT_DIR.mkdir(exist_ok=True)
    out_path = OUT_DIR / "questions.ndjson"

    per_type_target = target // len(TYPE_LABELS)
    remaining = dict.fromkeys(TYPE_LABELS, per_type_target)
    written = 0
    seq = 0

    with out_path.open("w", encoding="utf-8") as fh:
        # round 1: equal share per type
        for qtype in TYPE_LABELS:
            made = 0
            misses = 0
            while made < remaining[qtype] and misses < 40:
                question = gen.produce(qtype)
                if question is None:
                    misses += 1
                    continue
                misses = 0
                seq += 1
                fh.write(json.dumps(question_row(question, topics, seq), ensure_ascii=False) + "\n")
                made += 1
                written += 1
            remaining[qtype] -= made
            print(f"  {qtype:<20} {made:>6}  (missing {remaining[qtype]})")

        # round 2: redistribute what a type could not produce
        deficit = sum(remaining.values())
        if written < target and deficit:
            print(f"redistributing {target - written} questions")
            guard = 0
            order = list(TYPE_LABELS)
            while written < target and guard < target * 40:
                guard += 1
                progressed = False
                for qtype in order:
                    if written >= target:
                        break
                    question = gen.produce(qtype)
                    if question is None:
                        continue
                    progressed = True
                    seq += 1
                    fh.write(json.dumps(question_row(question, topics, seq), ensure_ascii=False) + "\n")
                    written += 1
                if not progressed:
                    break

    report = {
        "total": written,
        "target": target,
        "facts": len(facts),
        "facts_by_kind": dict(Counter(f.kind for f in facts)),
        "by_type": dict(gen.type_counts),
        "by_topic": dict(sorted(gen.topic_counts.items())),
    }
    (OUT_DIR / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"written: {written} -> {out_path}")
    print("by type:", dict(gen.type_counts))
    return 0 if written >= target else 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
