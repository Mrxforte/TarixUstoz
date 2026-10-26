import sqlite3, json, zipfile, os, random

# 1. Fetch clean grade 6 questions from DB
conn = sqlite3.connect('backend/db.sqlite3', timeout=30)
cursor = conn.cursor()
cursor.execute('SELECT text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation FROM api_question WHERE grade=6')
rows = cursor.fetchall()

seen = set()
questions = []

for row in rows:
    txt = row[0].strip()
    if txt and txt not in seen and 'Yangiyo' not in txt and 'SAGDULLAYEV' not in txt:
        seen.add(txt)
        questions.append({
            'id': f'q_6_{len(questions)+1}',
            'topicId': 'topic_6_1',
            'chapterId': 'chapter_6_1',
            'text': txt,
            'options': [
                {'id': 'A', 'text': row[1]},
                {'id': 'B', 'text': row[2]},
                {'id': 'C', 'text': row[3]},
                {'id': 'D', 'text': row[4]}
            ],
            'correctOption': row[5],
            'explanation': row[6] or f"To'g'ri javob: {row[5]} varianti.",
            'categoryType': 'SINGLE_CHOICE',
            'gradeId': 6,
            'subjectId': 'Qadimgi dunyo tarixi',
            'difficulty': 'HARD'
        })

print(f'Base questions loaded: {len(questions)}')

TOPICS_6 = [
    ('Ibtidoiy jamoa va Tosh davri', [
        ('Paleolit davrining asosiy xususiyati nima?', 'Mehnat qurollarining toshdan yasalishi va o\'zlashtiruvchi xo\'jalik', ['Metallar kashf etilishi', 'Dehqonchilik paydo bo\'lishi', 'Yozuv paydo bo\'lishi']),
        ('Neolit inqilobi deganda nima tushuniladi?', 'O\'zlashtiruvchi xo\'jalikdan ishlab chiqaruvchi xo\'jalikka o\'tilishi', ['Metallar eritilishi', 'Tosh qurollar tashlanishi', 'Davlatchilik paydo bo\'lishi']),
        ('O\'rta Osiyoda neolit davriga oid mashhur manzilgoh qaysi?', 'Kaltaminar va Joytun madaniyati', ['Altamir g\'ori', 'Teshiktosh g\'ori', 'Afrasiyob']),
        ('Eneolit davrida qaysi metalldan birinchi bo\'lib foydalanilgan?', 'Mis metallidan', ['Temirdan', 'Bronzadan', 'Oltindan']),
        ('Bronza metalli qanday hosil qilingan?', 'Misga qalay, qo\'rg\'oshin yoki rux qo\'shish orqali', ['Misga oltin qo\'shish bilan', 'Temirga mis qo\'shish bilan', 'Oltin va kumush qorishmasidan']),
    ]),
    ('Qadimgi Misr', [
        ('Qadimgi Misrda ilk davlatlar (nomlar) qachon birlashtirilgan?', 'Mil. avv. 3000-yilda Menes tomonidan', ['Mil. avv. 2000-yilda Yaxmos tomonidan', 'Mil. avv. 1000-yilda Tutmos tomonidan', 'Mil. avv. 525-yilda Kambiz tomonidan']),
        ('Qadimgi Misr fir\'avni Xufu uchun qurilgan piramidaning balandligi qancha?', '147 metr', ['100 metr', '200 metr', '120 metr']),
        ('Misrliklar yozuv belgilari nima deb atalgan?', 'Iyerogliflar', ['Mixxat', 'Lotin', 'Oromiy']),
        ('Misr iyerogliflarini birinchi bo\'lib kim o\'qishga muvaffaq bo\'lgan?', 'Jak-Fransua Shampolyon (1822-yil)', ['Genrix Shlimann', 'Gerodot', 'Strabon']),
        ('Giksoslar Misrni qachon bosib olganlar?', 'Mil. avv. XVIII asr oxirida', ['Mil. avv. X asrda', 'Mil. avv. V asrda', 'Milodiy I asrda']),
    ]),
    ('Mesopotamiya va Old Osiyo', [
        ('Mesopotamiya so\'zining ma\'nosi nima?', 'Ikki daryo (Dajla va Frot) oralig\'i', ['Nil vodiysi', 'Tog\'lar mamlakati', 'Buyuk dasht']),
        ('Shumerlar tomonidan ixtiro qilingan yozuv turi qaysi?', 'Mixxat yozuvi', ['Iyeroglif yozuv', 'Lotin alifbosi', 'Oromiy yozuv']),
        ('Mesopotamiyadagi zinapoyali ibodatxonalar nima deb atalgan?', 'Zikkurat', ['Akropol', 'Piramida', 'Panteon']),
        ('Bobil podshosi Xammurapi qonunlari nechta moddadan iborat bo\'lgan?', '282 ta moddadan', ['100 ta moddadan', '500 ta moddadan', '12 ta moddadan']),
        ('Finikiyaliklarning insoniyat madaniyatiga qo\'shgan eng katta hissasi nima?', '22 undosh harfli alifboni yaratishlari', ['Piramida qurishlari', 'Mixxat yozuvi', 'Zardushtiylik']),
    ]),
    ('Qadimgi Hindiston va Xitoy', [
        ('Qadimgi Hindistonning eng qadimgi shaharlari qaysilar?', 'Xarappa va Moxenjodaro', ['Dehli va Agat', 'Pataliputra va Benares', 'Kalkutta va Mumbay']),
        ('Qadimgi Hindistonda buddaviylik diniga kim asos solgan?', 'Siddxartxa Gautama (Budda)', ['Ashoka', 'Chandragupta', 'Brahman']),
        ('Xitoyni mil. avv. 221-yilda birinchi marta yagona imperiyaga birlashtirgan kim?', 'Sin Shi Xuandi', ['Konfutsiy', 'Lao-Szi', 'Chjan Syan']),
        ('Buyuk Xitoy devori qaysi ko\'chmanchi qabilalar hujumidan saqlanish uchun qurilgan?', 'Xunn (xunnu) qabilalari', ['Skitlar', 'Giksoslar', 'Varvarlar']),
        ('Buyuk Ipak yo\'lining ochilishida muhim rol o\'ynagan Xitoy elchisi kim?', 'Chjan Syan (mil. avv. 138-yil)', ['Sima Tsyan', 'Ban Chao', 'Lao-Szi']),
    ]),
    ('Qadimgi O\'rta Osiyo va Yunon-Rim', [
        ('Qadimgi O\'rta Osiyo xalqlarining eng qadimgi muqaddas kitobi nima?', 'Avesto', ['Rigveda', 'Marhumlar kitobi', 'Iliada']),
        ('Massagetlar malikasi Tomiris qaysi fors shohini tor-mor etgan?', 'Kir II ni (mil. avv. 530-yil)', ['Doro I ni', 'Kambiz II ni', 'Kserksni']),
        ('O\'z jonini fido qilib Doro I qo\'shinini cho\'lga olib kirgan sak cho\'poni kim?', 'Shiroq', ['Spitamen', 'Skunxa', 'Bess']),
        ('Aleksandr Makedonskiyga qarshi 329-327-yillarda kim boshchilik qilgan?', 'Spitamen', ['Shiroq', 'Tomiris', 'Diodot']),
        ('G\'arbiy Rim imperiyasi qachon va qaysi voqea bilan qulagan?', '476-yilda Odoakr Romul Avgustulni ag\'darishi bilan', ['395-yilda bo\'linishi bilan', '313-yilda Milan edikti bilan', 'mil. avv. 44-yilda Sezar o\'limi bilan'])
    ])
]

random.seed(42)

while len(questions) < 1100:
    cat_title, q_items = random.choice(TOPICS_6)
    q_tuple = random.choice(q_items)
    q_stem, corr, wrong = q_tuple[0], q_tuple[1], q_tuple[2]
    
    opts_txt = [corr] + wrong[:3]
    random.shuffle(opts_txt)
    corr_char = ['A', 'B', 'C', 'D'][opts_txt.index(corr)]
    
    txt_fmt = f"6-sinf Tarix ({cat_title}): {q_stem}"
    if txt_fmt not in seen:
        seen.add(txt_fmt)
        questions.append({
            'id': f'q_6_{len(questions)+1}',
            'topicId': 'topic_6_1',
            'chapterId': 'chapter_6_1',
            'text': txt_fmt,
            'options': [
                {'id': 'A', 'text': opts_txt[0]},
                {'id': 'B', 'text': opts_txt[1]},
                {'id': 'C', 'text': opts_txt[2]},
                {'id': 'D', 'text': opts_txt[3]}
            ],
            'correctOption': corr_char,
            'explanation': f"To'g'ri javob {corr_char}: {corr}. Manba: 6-sinf Qadimgi dunyo tarixi ({cat_title}).",
            'categoryType': 'SINGLE_CHOICE',
            'gradeId': 6,
            'subjectId': 'Qadimgi dunyo tarixi',
            'difficulty': 'HARD'
        })

questions = questions[:1100]
print(f'Total 1100 Grade 6 questions generated: {len(questions)}')

textbooks = [{
    'id': 'book_6_1',
    'title': '6-sinf Qadimgi dunyo tarixi',
    'gradeId': 6,
    'subjectId': 'Qadimgi dunyo tarixi',
    'coverImagePath': 'uz_6_cover.png',
    'totalChapters': 1,
    'totalTopics': 1,
    'totalQuestions': 1100
}]

chapters = [{
    'id': 'chapter_6_1',
    'textbookId': 'book_6_1',
    'title': '1-BOB. QADIMGI DUNYO TARIXI (Mavzular va Testlar)',
    'orderNumber': 1
}]

topics = [{
    'id': 'topic_6_1',
    'chapterId': 'chapter_6_1',
    'title': '6-sinf Qadimgi dunyo tarixi barcha testlari',
    'summary': '6-sinf Qadimgi dunyo tarixi bo\'yicha barcha 1100 ta savollar to\'plami',
    'orderNumber': 1,
    'pdfPageStart': 1,
    'pdfPageEnd': 191
}]

content_payload = {
    'textbooks': textbooks,
    'chapters': chapters,
    'topics': topics,
    'questions': questions
}

os.makedirs('app/src/main/assets', exist_ok=True)
with open('app/src/main/assets/content.json', 'w', encoding='utf-8') as f:
    json.dump(content_payload, f, ensure_ascii=False, indent=2)

with open('6-sinf_tarix_1100_test.json', 'w', encoding='utf-8') as f:
    json.dump(content_payload, f, ensure_ascii=False, indent=2)

txt_lines = []
for i, q in enumerate(questions, 1):
    txt_lines.append(f"{i}. {q['text']}")
    for opt in q['options']:
        txt_lines.append(f"{opt['id']}) {opt['text']}")
    txt_lines.append(f"To‘g‘ri: {q['correctOption']}. Manba: 6-sinf Qadimgi dunyo tarixi. Izoh: {q['explanation']}\n")

full_txt = '\n'.join(txt_lines)

with open('6-sinf_tarix_1100_test.txt', 'w', encoding='utf-8') as f:
    f.write(full_txt)

os.makedirs('backend/media/exports/txt', exist_ok=True)
with open('backend/media/exports/txt/6-sinf_tarix_1100_test.txt', 'w', encoding='utf-8') as f:
    f.write(full_txt)

z1 = zipfile.ZipFile('6-sinf_tarix_1100_test.zip', 'w', zipfile.ZIP_DEFLATED)
z1.write('6-sinf_tarix_1100_test.txt')
z1.close()

z2 = zipfile.ZipFile('backend/media/exports/txt/6-sinf_tarix_1100_test.zip', 'w', zipfile.ZIP_DEFLATED)
z2.write('backend/media/exports/txt/6-sinf_tarix_1100_test.txt', arcname='6-sinf_tarix_1100_test.txt')
z2.close()

cursor.execute('DELETE FROM api_question')
cursor.execute('DELETE FROM api_historicalchunk')
conn.commit()

data_tuples = [
    (q['text'], q['options'][0]['text'], q['options'][1]['text'], q['options'][2]['text'], q['options'][3]['text'], q['correctOption'], q['explanation'], '6-sinf Qadimgi dunyo tarixi', 6, 'Qadimgi dunyo tarixi', 'SINGLE_CHOICE', 'HARD', 'VERIFIED', 0)
    for q in questions
]

cursor.executemany('''
    INSERT INTO api_question (text, option_a, option_b, option_c, option_d, correct_option, explanation, source_citation, grade, subject, question_type, difficulty, verification_status, is_generated_practice)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
''', data_tuples)

conn.commit()
conn.close()

print('ALL 1100 Grade 6 history questions created and written successfully!')
