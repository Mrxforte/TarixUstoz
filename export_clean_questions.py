import sqlite3
import json
import os

conn = sqlite3.connect('backend/db.sqlite3')
cursor = conn.cursor()

# Fetch all Topics
cursor.execute("SELECT id, document_id, chapter_id, title, summary, `order` FROM api_topic")
topic_rows = cursor.fetchall()

topics_payload = []
for t in topic_rows:
    t_id, doc_id, ch_id, title, summary, order = t
    topics_payload.append({
        'id': f'topic_{t_id}',
        'chapterId': f'ch_{ch_id}' if ch_id else 'ch_6',
        'textbookId': f'book_{doc_id}' if doc_id else 'uz_6',
        'number': order or 1,
        'title': title or 'Tarixiy mavzu',
        'summary': summary or ''
    })

print(f"Loaded {len(topics_payload)} topics from DB")

# Fetch Questions for Grade 6
cursor.execute("SELECT id, topic_id, text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, grade, subject, difficulty FROM api_question WHERE grade=6")
rows_6 = cursor.fetchall()

# Fetch Questions for Grade 7 Jahon
cursor.execute("SELECT id, topic_id, text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, grade, subject, difficulty FROM api_question WHERE grade=7 AND (subject LIKE '%Jahon%' OR subject LIKE '%jahon%') LIMIT 2000")
rows_7_jahon = cursor.fetchall()

questions = []
seen = set()

def process_rows(rows, default_grade, default_subj):
    for r in rows:
        q_id, t_id, txt, a, b, c, d, corr, exp, src, grade, subj, diff = r
        clean_txt = (txt or '').strip()
        if clean_txt and clean_txt not in seen:
            seen.add(clean_txt)
            g = grade or default_grade
            s_raw = subj or default_subj
            s_id = "jahon" if "jahon" in s_raw.lower() else "uzbekiston"
            if g == 6:
                s_id = "uzbekiston"
                tb_id = "uz_6"
            else:
                tb_id = f"{s_id}_{g}"

            topic_ref = f'topic_{t_id}' if t_id else f'topic_{tb_id}_1'

            questions.append({
                'id': f'q_{q_id}',
                'topicId': topic_ref,
                'chapterId': f'ch_{g}',
                'textbookId': tb_id,
                'text': clean_txt,
                'options': [
                    {'id': 'a', 'text': a or '', 'isCorrect': corr == 'A'},
                    {'id': 'b', 'text': b or '', 'isCorrect': corr == 'B'},
                    {'id': 'c', 'text': c or '', 'isCorrect': corr == 'C'},
                    {'id': 'd', 'text': d or '', 'isCorrect': corr == 'D'}
                ],
                'correctOption': corr or 'A',
                'explanation': exp or f'To‘g‘ri javob: {corr} varianti.',
                'categoryType': 'SINGLE_CHOICE',
                'gradeId': g,
                'subjectId': s_id,
                'difficulty': diff or 'EXAM',
                'sourceReference': src or ''
            })

process_rows(rows_6, 6, "Qadimgi dunyo tarixi")
process_rows(rows_7_jahon, 7, "Jahon tarixi")

q6_count = len([q for q in questions if q["gradeId"] == 6])
q7_jahon_count = len([q for q in questions if q["gradeId"] == 7 and q["subjectId"] == "jahon"])

print(f"Loaded Grade 6 questions: {q6_count}")
print(f"Loaded Grade 7 Jahon questions: {q7_jahon_count}")

# 11 Official Textbooks
textbooks = [
    {'id': 'uz_6', 'title': 'Tarix. Qadimgi dunyo', 'gradeId': 6, 'subjectId': 'uzbekiston', 'authors': 'A.S. Sagdullayev, V.A. Kostetskiy', 'year': 2017, 'edition': '5-nashr', 'coverAsset': 'file:///android_asset/images/uz_6_cover.png', 'pdfAsset': 'textbooks/6_sinf_tarix.pdf', 'totalChapters': 5, 'totalTopics': 44, 'totalQuestions': q6_count},
    {'id': 'jahon_7', 'title': 'Jahon tarixi (V–XV asr)', 'gradeId': 7, 'subjectId': 'jahon', 'authors': 'T.O‘. Salimov, F.E. Sultonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 40, 'totalQuestions': 99013},
    {'id': 'uz_7', 'title': 'O‘zbekiston tarixi (IV–XVI asr)', 'gradeId': 7, 'subjectId': 'uzbekiston', 'authors': 'A. Muhammadjonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 35, 'totalQuestions': 98935},
    {'id': 'uz_8', 'title': 'O‘zbekiston tarixi (XVI–XIX asr I yarmi)', 'gradeId': 8, 'subjectId': 'uzbekiston', 'authors': 'U. Jo‘rayev va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 45, 'totalQuestions': 98866},
    {'id': 'jahon_8', 'title': 'Jahon tarixi (XVI–XIX asr)', 'gradeId': 8, 'subjectId': 'jahon', 'authors': 'R. Farmonov, U. Jo‘rayev, Sh. Ergashev', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 35, 'totalQuestions': 99191},
    {'id': 'uz_9', 'title': 'O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)', 'gradeId': 9, 'subjectId': 'uzbekiston', 'authors': 'S. Tillaboyev, A. Zamonov', 'year': 2019, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 99977},
    {'id': 'jahon_9', 'title': 'Jahon tarixi (XX asr boshi)', 'gradeId': 9, 'subjectId': 'jahon', 'authors': 'M. Lafasov va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 40, 'totalQuestions': 99995},
    {'id': 'uz_10', 'title': 'O‘zbekiston tarixi (1917–1991)', 'gradeId': 10, 'subjectId': 'uzbekiston', 'authors': 'Q. Rajabov, A. Zamonov', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 99988},
    {'id': 'jahon_10', 'title': 'Jahon tarixi (1918–1991)', 'gradeId': 10, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 100000},
    {'id': 'uz_11', 'title': 'O‘zbekiston tarixi (Mustaqillik davri)', 'gradeId': 11, 'subjectId': 'uzbekiston', 'authors': 'N. Jo‘rayev, A. Zamonov', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 25, 'totalQuestions': 99994},
    {'id': 'jahon_11', 'title': 'Jahon tarixi (1991–2017)', 'gradeId': 11, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Jahon_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 25, 'totalQuestions': 100000}
]

payload = {
    'textbooks': textbooks,
    'chapters': [
        {'id': f'ch_{g}', 'textbookId': 'uz_6' if g==6 else f'uz_{g}', 'number': g, 'title': f'{g}-SINF TARIXI'} for g in range(6, 12)
    ],
    'topics': topics_payload,
    'questions': questions
}

os.makedirs('app/src/main/assets', exist_ok=True)
with open('app/src/main/assets/content.json', 'w', encoding='utf-8') as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)

print('Exported content.json successfully with matched topicIds!')
