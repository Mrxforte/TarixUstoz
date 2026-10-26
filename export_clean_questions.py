"""Build app/src/main/assets/content.json — the offline fallback of the app.

The full 1.1 million questions live in the backend database and are synced per
book over HTTP. Bundling all of them into the APK made the asset 41 MB, so the
asset keeps a small sample per topic (default: 6) while the textbooks/topics
catalog stays complete. Textbook `totalQuestions` values come from the database.

Usage (from the project root, next to backend/):
  python export_clean_questions.py                 # 6 questions per topic
  python export_clean_questions.py --per-topic 10
"""
from __future__ import annotations

import argparse
import json
import os
import sqlite3
from collections import defaultdict

DB = os.path.join("backend", "db.sqlite3")
OUT = os.path.join("app", "src", "main", "assets", "content.json")


def subject_id(subject: str) -> str:
    return "jahon" if "jahon" in (subject or "").lower() else "uzbekiston"


def textbook_id(grade: int, subject: str) -> str:
    return "uz_6" if grade == 6 else f"{subject_id(subject)}_{grade}"


TEXTBOOKS = [
    ("uz_6", "Tarix. Qadimgi dunyo", 6, "uzbekiston", "A.S. Sagdullayev, V.A. Kostetskiy", 2017, "5-nashr", "file:///android_asset/images/uz_6_cover.png", "textbooks/6_sinf_tarix.pdf", 5, 44),
    ("jahon_7", "Jahon tarixi (V–XV asr)", 7, "jahon", "T.O‘. Salimov, F.E. Sultonov", 2017, "3-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf", 12, 40),
    ("uz_7", "O‘zbekiston tarixi (IV–XVI asr)", 7, "uzbekiston", "A. Muhammadjonov", 2017, "3-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/7-sinf_Ozbekiston_tarixi.pdf", 10, 35),
    ("uz_8", "O‘zbekiston tarixi (XVI–XIX asr I yarmi)", 8, "uzbekiston", "U. Jo‘rayev va b.", 2019, "4-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/8-sinf_Ozbekiston_tarixi.pdf", 12, 45),
    ("jahon_8", "Jahon tarixi (XVI–XIX asr)", 8, "jahon", "R. Farmonov, U. Jo‘rayev, Sh. Ergashev", 2019, "4-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/8-sinf_Jahon_tarixi.pdf", 10, 35),
    ("uz_9", "O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)", 9, "uzbekiston", "S. Tillaboyev, A. Zamonov", 2019, "3-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/9-sinf_Ozbekiston_tarixi.pdf", 10, 30),
    ("jahon_9", "Jahon tarixi (XX asr boshi)", 9, "jahon", "M. Lafasov va b.", 2019, "4-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/9-sinf_Jahon_tarixi.pdf", 12, 40),
    ("uz_10", "O‘zbekiston tarixi (1917–1991)", 10, "uzbekiston", "Q. Rajabov, A. Zamonov", 2017, "1-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/10-sinf_Ozbekiston_tarixi.pdf", 10, 30),
    ("jahon_10", "Jahon tarixi (1918–1991)", 10, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2017, "1-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/10-sinf_Jahon_tarixi.pdf", 10, 30),
    ("uz_11", "O‘zbekiston tarixi (Mustaqillik davri)", 11, "uzbekiston", "N. Jo‘rayev, A. Zamonov", 2018, "1-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/11-sinf_Ozbekiston_tarixi.pdf", 8, 25),
    ("jahon_11", "Jahon tarixi (1991–2017)", 11, "jahon", "Sh. Ergashev, B. Xodjayev, J. Abdullayev", 2018, "1-nashr", None, "http://10.0.2.2:8000/media/books/pdfs/11-sinf_Jahon_tarixi.pdf", 8, 25),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--per-topic", type=int, default=6)
    parser.add_argument("--db", default=DB)
    args = parser.parse_args()

    conn = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    cur = conn.cursor()

    topics_payload = [
        {
            "id": f"topic_{t_id}",
            "chapterId": f"ch_{ch_id}" if ch_id else "ch_6",
            "textbookId": f"book_{doc_id}" if doc_id else "uz_6",
            "number": order or 1,
            "title": title or "Tarixiy mavzu",
            "summary": summary or "",
        }
        for t_id, doc_id, ch_id, title, summary, order in cur.execute(
            "SELECT id, document_id, chapter_id, title, summary, `order` FROM api_topic"
        )
    ]

    totals = {
        textbook_id(grade, subject): count
        for grade, subject, count in cur.execute(
            "SELECT grade, subject, COUNT(*) FROM api_question "
            "WHERE verification_status='VERIFIED' GROUP BY grade, subject"
        )
    }

    questions, seen, per_topic = [], set(), defaultdict(int)
    rows = cur.execute(
        "SELECT id, topic_id, text, option_a, option_b, option_c, option_d, correct_option, "
        "explanation, source_citation, grade, subject, difficulty, question_type "
        "FROM api_question WHERE verification_status='VERIFIED' "
        "ORDER BY grade, subject, topic_id, id"
    )
    for (q_id, t_id, txt, a, b, c, d, corr, exp, src, grade, subject, diff, qtype) in rows:
        txt = (txt or "").strip()
        topic_key = t_id or f"{grade}-{subject}"
        if not txt or txt in seen or per_topic[topic_key] >= args.per_topic:
            continue
        seen.add(txt)
        per_topic[topic_key] += 1
        tb_id = textbook_id(grade, subject)
        questions.append({
            "id": f"q_{q_id}",
            "topicId": f"topic_{t_id}" if t_id else f"topic_{tb_id}_1",
            "chapterId": f"ch_{grade}",
            "textbookId": tb_id,
            "text": txt,
            "options": [
                {"id": "a", "text": a or "", "isCorrect": corr == "A"},
                {"id": "b", "text": b or "", "isCorrect": corr == "B"},
                {"id": "c", "text": c or "", "isCorrect": corr == "C"},
                {"id": "d", "text": d or "", "isCorrect": corr == "D"},
            ],
            "correctOption": corr or "A",
            "explanation": exp or f"To‘g‘ri javob: {corr} varianti.",
            "categoryType": qtype or "SINGLE_CHOICE",
            "gradeId": grade,
            "subjectId": subject_id(subject),
            "difficulty": diff or "MEDIUM",
            "sourceReference": src or "",
        })

    textbooks = [
        {
            "id": tb_id, "title": title, "gradeId": grade, "subjectId": sbj,
            "authors": authors, "year": year, "edition": edition,
            **({"coverAsset": cover} if cover else {}),
            "pdfAsset": pdf, "totalChapters": chapters,
            "totalTopics": topics, "totalQuestions": totals.get(tb_id, 0),
        }
        for tb_id, title, grade, sbj, authors, year, edition, cover, pdf, chapters, topics in TEXTBOOKS
    ]

    payload = {
        "textbooks": textbooks,
        "chapters": [
            {"id": f"ch_{g}", "textbookId": "uz_6" if g == 6 else f"uz_{g}", "number": g, "title": f"{g}-SINF TARIXI"}
            for g in range(6, 12)
        ],
        "topics": topics_payload,
        "questions": questions,
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(payload, fh, ensure_ascii=False, separators=(",", ":"))
    print(f"topics: {len(topics_payload)}, questions: {len(questions)}, "
          f"per topic: {args.per_topic}, file: {os.path.getsize(OUT) / 1e6:.1f} MB")
    print("totals:", dict(sorted(totals.items())))


if __name__ == "__main__":
    main()
