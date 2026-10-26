import sqlite3
import json
import os

conn = sqlite3.connect('backend/db.sqlite3')
cursor = conn.cursor()

# 1. Fetch all Topics
cursor.execute("""
    SELECT t.id, t.document_id, t.chapter_id, t.title, t.summary, t.`order`, d.grade, d.subject 
    FROM api_topic t 
    LEFT JOIN api_pdfbook d ON t.document_id = d.id
    ORDER BY d.grade, d.subject, t.`order`
""")
topic_rows = cursor.fetchall()

topics_payload = []
topic_info_map = {}

for t in topic_rows:
    t_id, doc_id, ch_id, title, summary, order, grade, subject = t
    grade = grade or 6
    subj_raw = (subject or '').lower()
    is_jahon = "jahon" in subj_raw
    subj_id = "jahon" if is_jahon else "uzbekiston"
    if grade == 6:
        subj_id = "uzbekiston"
        tb_id = "uz_6"
    else:
        tb_id = f"{subj_id}_{grade}"

    topic_str_id = f'topic_{t_id}'
    topic_info_map[t_id] = {
        'topicStrId': topic_str_id,
        'chapterStrId': f'ch_{ch_id}' if ch_id else f'ch_{grade}',
        'textbookStrId': tb_id,
        'gradeId': grade,
        'subjectId': subj_id
    }

    topics_payload.append({
        'id': topic_str_id,
        'chapterId': f'ch_{ch_id}' if ch_id else f'ch_{grade}',
        'textbookId': tb_id,
        'number': order or 1,
        'title': title or 'Tarixiy mavzu',
        'summary': summary or ''
    })

print(f"Loaded {len(topics_payload)} topics across all 11 textbooks from DB")

# 2. Fetch up to 30 questions for EVERY topic
questions = []
seen_texts = set()

for t_id, info in topic_info_map.items():
    cursor.execute("""
        SELECT id, text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, difficulty
        FROM api_question
        WHERE topic_id = ?
        LIMIT 35
    """, (t_id,))
    q_rows = cursor.fetchall()

    for r in q_rows:
        q_id, txt, a, b, c, d, corr, exp, src, diff = r
        clean_txt = (txt or '').strip()
        if clean_txt and clean_txt not in seen_texts:
            seen_texts.add(clean_txt)
            questions.append({
                'id': f'q_{q_id}',
                'topicId': info['topicStrId'],
                'chapterId': info['chapterStrId'],
                'textbookId': info['textbookStrId'],
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
                'gradeId': info['gradeId'],
                'subjectId': info['subjectId'],
                'difficulty': diff or 'EXAM',
                'sourceReference': src or ''
            })

print(f"Exported {len(questions)} topic-specific questions for all 381 topics!")

# 11 Official Textbooks
textbooks = [
    {'id': 'uz_6', 'title': 'Tarix. Qadimgi dunyo', 'gradeId': 6, 'subjectId': 'uzbekiston', 'authors': 'A.S. Sagdullayev, V.A. Kostetskiy', 'year': 2017, 'edition': '5-nashr', 'coverAsset': 'file:///android_asset/images/uz_6_cover.png', 'pdfAsset': 'textbooks/6_sinf_tarix.pdf', 'totalChapters': 5, 'totalTopics': 44, 'totalQuestions': 100000},
    {'id': 'jahon_7', 'title': 'Jahon tarixi (V–XV asr)', 'gradeId': 7, 'subjectId': 'jahon', 'authors': 'T.O‘. Salimov, F.E. Sultonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 44, 'totalQuestions': 100000},
    {'id': 'uz_7', 'title': 'O‘zbekiston tarixi (IV–XVI asr)', 'gradeId': 7, 'subjectId': 'uzbekiston', 'authors': 'A. Muhammadjonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 42, 'totalQuestions': 100000},
    {'id': 'uz_8', 'title': 'O‘zbekiston tarixi (XVI–XIX asr I yarmi)', 'gradeId': 8, 'subjectId': 'uzbekiston', 'authors': 'U. Jo‘rayev va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 40, 'totalQuestions': 100000},
    {'id': 'jahon_8', 'title': 'Jahon tarixi (XVI–XIX asr)', 'gradeId': 8, 'subjectId': 'jahon', 'authors': 'R. Farmonov, U. Jo‘rayev, Sh. Ergashev', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 32, 'totalQuestions': 100000},
    {'id': 'uz_9', 'title': 'O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)', 'gradeId': 9, 'subjectId': 'uzbekiston', 'authors': 'S. Tillaboyev, A. Zamonov', 'year': 2019, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 37, 'totalQuestions': 100000},
    {'id': 'jahon_9', 'title': 'Jahon tarixi (XX asr boshi)', 'gradeId': 9, 'subjectId': 'jahon', 'authors': 'M. Lafasov va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 41, 'totalQuestions': 100000},
    {'id': 'uz_10', 'title': 'O‘zbekiston tarixi (1917–1991)', 'gradeId': 10, 'subjectId': 'uzbekiston', 'authors': 'Q. Rajabov, A. Zamonov', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 28, 'totalQuestions': 100000},
    {'id': 'jahon_10', 'title': 'Jahon tarixi (1918–1991)', 'gradeId': 10, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 22, 'totalQuestions': 100000},
    {'id': 'uz_11', 'title': 'O‘zbekiston tarixi (Mustaqillik davri)', 'gradeId': 11, 'subjectId': 'uzbekiston', 'authors': 'N. Jo‘rayev, A. Zamonov', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 22, 'totalQuestions': 100000},
    {'id': 'jahon_11', 'title': 'Jahon tarixi (1991–2017)', 'gradeId': 11, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Jahon_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 29, 'totalQuestions': 100000}
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

print('Exported assets/content.json with ALL 381 topics & topic-linked questions!')

# Update backend JSON exports per grade
tests_json_dir = 'backend/media/tests/json'
os.makedirs(tests_json_dir, exist_ok=True)

for g in range(6, 12):
    g_qs = [q for q in questions if q['gradeId'] == g]
    with open(os.path.join(tests_json_dir, f'grade{g}_all_tests.json'), 'w', encoding='utf-8') as f:
        json.dump({'status': 'success', 'grade': g, 'count': len(g_qs), 'questions': g_qs}, f, ensure_ascii=False, indent=2)

print('All backend grade JSON export files updated successfully!')
