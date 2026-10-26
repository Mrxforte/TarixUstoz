import sqlite3
import json
import os

conn = sqlite3.connect('backend/db.sqlite3')
cursor = conn.cursor()

# 1. Fetch Grade 6 questions
cursor.execute("SELECT id, text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, grade, subject, difficulty FROM api_question WHERE grade=6")
rows_6 = cursor.fetchall()

# 2. Fetch Grade 7 Jahon questions (1500 sample questions for offline assets)
cursor.execute("SELECT id, text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, grade, subject, difficulty FROM api_question WHERE grade=7 AND (subject LIKE '%Jahon%' OR subject LIKE '%jahon%') LIMIT 1500")
rows_7_jahon = cursor.fetchall()

questions = []
seen = set()

def process_rows(rows, default_grade, default_subj):
    for r in rows:
        q_id, txt, a, b, c, d, corr, exp, src, grade, subj, diff = r
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

            questions.append({
                'id': f'q_{q_id}',
                'topicId': f'topic_{tb_id}_1',
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

# 11 Official Textbooks (with real counts for available ones, 0 for others)
textbooks = [
    {'id': 'uz_6', 'title': 'Tarix. Qadimgi dunyo', 'gradeId': 6, 'subjectId': 'uzbekiston', 'authors': 'A.S. Sagdullayev, V.A. Kostetskiy', 'year': 2017, 'edition': '5-nashr', 'coverAsset': 'file:///android_asset/images/uz_6_cover.png', 'pdfAsset': 'textbooks/6_sinf_tarix.pdf', 'totalChapters': 5, 'totalTopics': 44, 'totalQuestions': q6_count},
    {'id': 'jahon_7', 'title': 'Jahon tarixi (V–XV asr)', 'gradeId': 7, 'subjectId': 'jahon', 'authors': 'T.O‘. Salimov, F.E. Sultonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 40, 'totalQuestions': 100000},
    {'id': 'uz_7', 'title': 'O‘zbekiston tarixi (IV–XVI asr)', 'gradeId': 7, 'subjectId': 'uzbekiston', 'authors': 'A. Muhammadjonov', 'year': 2017, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/7-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 35, 'totalQuestions': 0},
    {'id': 'uz_8', 'title': 'O‘zbekiston tarixi (XVI–XIX asr I yarmi)', 'gradeId': 8, 'subjectId': 'uzbekiston', 'authors': 'U. Jo‘rayev va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 45, 'totalQuestions': 0},
    {'id': 'jahon_8', 'title': 'Jahon tarixi (XVI–XIX asr)', 'gradeId': 8, 'subjectId': 'jahon', 'authors': 'R. Farmonov, U. Jo‘rayev, Sh. Ergashev', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/8-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 35, 'totalQuestions': 0},
    {'id': 'uz_9', 'title': 'O‘zbekiston tarixi (XIX asr II yarmi – XX asr boshi)', 'gradeId': 9, 'subjectId': 'uzbekiston', 'authors': 'S. Tillaboyev, A. Zamonov', 'year': 2019, 'edition': '3-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 0},
    {'id': 'jahon_9', 'title': 'Jahon tarixi (XX asr boshi)', 'gradeId': 9, 'subjectId': 'jahon', 'authors': 'M. Lafasov va b.', 'year': 2019, 'edition': '4-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/9-sinf_Jahon_tarixi.pdf', 'totalChapters': 12, 'totalTopics': 40, 'totalQuestions': 0},
    {'id': 'uz_10', 'title': 'O‘zbekiston tarixi (1917–1991)', 'gradeId': 10, 'subjectId': 'uzbekiston', 'authors': 'Q. Rajabov, A. Zamonov', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 0},
    {'id': 'jahon_10', 'title': 'Jahon tarixi (1918–1991)', 'gradeId': 10, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2017, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/10-sinf_Jahon_tarixi.pdf', 'totalChapters': 10, 'totalTopics': 30, 'totalQuestions': 0},
    {'id': 'uz_11', 'title': 'O‘zbekiston tarixi (Mustaqillik davri)', 'gradeId': 11, 'subjectId': 'uzbekiston', 'authors': 'N. Jo‘rayev, A. Zamonov', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Ozbekiston_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 25, 'totalQuestions': 0},
    {'id': 'jahon_11', 'title': 'Jahon tarixi (1991–2017)', 'gradeId': 11, 'subjectId': 'jahon', 'authors': 'Sh. Ergashev, B. Xodjayev, J. Abdullayev', 'year': 2018, 'edition': '1-nashr', 'pdfAsset': 'http://10.0.2.2:8000/media/books/pdfs/11-sinf_Jahon_tarixi.pdf', 'totalChapters': 8, 'totalTopics': 25, 'totalQuestions': 0}
]

payload = {
    'textbooks': textbooks,
    'chapters': [
        {'id': 'ch_6', 'textbookId': 'uz_6', 'number': 1, 'title': '6-SINF QADIMGI DUNYO TARIXI'},
        {'id': 'ch_7_jahon', 'textbookId': 'jahon_7', 'number': 1, 'title': '7-SINF JAHON TARIXI'}
    ],
    'topics': [
        {'id': 'topic_uz_6_1', 'chapterId': 'ch_6', 'textbookId': 'uz_6', 'number': 1, 'title': '6-sinf Qadimgi dunyo tarixi barcha mavzular', 'summary': '6-sinf Tarixi'},
        {'id': 'topic_jahon_7_1', 'chapterId': 'ch_7_jahon', 'textbookId': 'jahon_7', 'number': 1, 'title': '7-sinf Jahon tarixi barcha mavzular', 'summary': '7-sinf Jahon Tarixi'}
    ],
    'questions': questions
}

os.makedirs('app/src/main/assets', exist_ok=True)
with open('app/src/main/assets/content.json', 'w', encoding='utf-8') as f:
    json.dump(payload, f, ensure_ascii=False, indent=2)

print('Exported content.json successfully!')

# Update backend JSON export files
tests_json_dir = 'backend/media/tests/json'
os.makedirs(tests_json_dir, exist_ok=True)

# Grade 6 export
q6_list = [q for q in questions if q["gradeId"] == 6]
with open(os.path.join(tests_json_dir, 'grade6_all_tests.json'), 'w', encoding='utf-8') as f:
    json.dump({'status': 'success', 'grade': 6, 'count': len(q6_list), 'questions': q6_list}, f, ensure_ascii=False, indent=2)

# Grade 7 Jahon export
q7_list = [q for q in questions if q["gradeId"] == 7 and q["subjectId"] == "jahon"]
with open(os.path.join(tests_json_dir, 'grade7_all_tests.json'), 'w', encoding='utf-8') as f:
    json.dump({'status': 'success', 'grade': 7, 'count': len(q7_list), 'questions': q7_list}, f, ensure_ascii=False, indent=2)

# Remove unused grade test files from backend media/tests/json
for g in [8, 9, 10, 11]:
    fpath = os.path.join(tests_json_dir, f'grade{g}_all_tests.json')
    if os.path.exists(fpath):
        os.remove(fpath)

print('Backend JSON exports updated!')
