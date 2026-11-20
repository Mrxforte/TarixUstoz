import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_801_900 = """
**801.** Mil. avv. 753-yilda qanday voqea yuz bergan?  
A) Tibr daryosi bo‘yida Rim shahriga asos solingan  
B) Rimda respublika e’lon qilingan  
C) Karfagen vayron etilgan  
D) Puni urushlari boshlangan  
**To‘g‘ri: A.** Manba: Qadimgi Italiya. **Izoh:** Rivoyatga ko‘ra, mil. avv. 753-yilda aka-uka Romul va Rem tomonidan Tibr daryosi bo‘yida Rim shahriga asos solingan.

**802.** Mil. avv. 509-yilda qanday voqea yuz bergan?  
A) Rimda respublika e’lon qilingan  
B) Rim shahriga asos solingan  
C) Karfagen vayron etilgan  
D) Puni urushlari boshlangan  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Mil. avv. 509-yilda Mag‘rur Tarkviniy quvg‘in qilinib, Rimda respublika boshqaruvi e’lon qilingan.

**803.** Mil. avv. 216-yilda qanday voqea yuz bergan?  
A) Kann jangida Rim armiyasi tor-mor etilgan  
B) Zama jangida Gannibal tor-mor etilgan  
C) Karfagen vayron etilgan  
D) Birinchi Puni urushi boshlangan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinidagi jangda Gannibal Rim armiyasini tor-mor etgan.

**804.** Mil. avv. 49-yilda qanday voqea yuz bergan?  
A) Yuliy Sezar Rubikon daryosidan o‘tgan  
B) Oktavian Avgust imperator bo‘lgan  
C) Spartak qo‘zg‘oloni boshlangan  
D) Sezar o‘ldirilgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Mil. avv. 49-yilda Sezar Rubikon daryosidan o‘tib, yakka hokimlik uchun ochiq kurash boshlagan.

**805.** Mil. avv. 44-yilda qanday voqea yuz bergan?  
A) Yuliy Sezar senatorlar fitnasi natijasida o‘ldirilgan  
B) Oktavian Avgust imperator bo‘lgan  
C) Spartak qo‘zg‘oloni boshlangan  
D) Sezar Rubikon daryosidan o‘tgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Mil. avv. 44-yilda Brut boshchiligidagi senatorlar fitnasi natijasida Yuliy Sezar o‘ldirilgan.

**806.** Rim shahriga qachon asos solingan?  
A) mil. avv. 753-yilda  
B) mil. avv. 751-yilda  
C) mil. avv. 755-yilda  
D) mil. avv. 757-yilda  
**To‘g‘ri: A.** Manba: Qadimgi Italiya. **Izoh:** Rim shahriga mil. avv. 753-yilda asos solingan.

**807.** Rimda respublika qachon e’lon qilingan?  
A) mil. avv. 509-yilda  
B) mil. avv. 507-yilda  
C) mil. avv. 511-yilda  
D) mil. avv. 513-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Rimda respublika mil. avv. 509-yilda e’lon qilingan.

**808.** Kann jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 216-yilda  
B) mil. avv. 214-yilda  
C) mil. avv. 218-yilda  
D) mil. avv. 220-yilda  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Kann jangi mil. avv. 216-yilda bo‘lib o‘tgan.

**809.** Yuliy Sezar Rubikon daryosidan qachon o‘tgan?  
A) mil. avv. 49-yilda  
B) mil. avv. 47-yilda  
C) mil. avv. 51-yilda  
D) mil. avv. 53-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar mil. avv. 49-yilda Rubikon daryosidan o‘tgan.

**810.** Yuliy Sezar qachon o‘ldirilgan?  
A) mil. avv. 44-yilda  
B) mil. avv. 42-yilda  
C) mil. avv. 46-yilda  
D) mil. avv. 48-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar mil. avv. 44-yilda o‘ldirilgan.

**811.** Romul kim?  
A) Rim shahriga asos solgan aka-ukalardan biri  
B) Rim respublikasiga asos solgan hukmdor  
C) Karfagen sarkardasi  
D) Spartak qo‘zg‘oloni rahbari  
**To‘g‘ri: A.** Manba: Qadimgi Italiya. **Izoh:** Romul aka-ukasi Rem bilan birga Rim shahriga asos solgan va shaharga o‘z nomini bergan.

**812.** Gannibal kim?  
A) Karfagen sarkardasi  
B) Rim imperatori  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Gannibal Karfagen armiyasiga boshchilik qilgan va Rimga qarshi kurashgan.

**813.** Ssipion kim?  
A) Rim sarkardasi  
B) Karfagen sarkardasi  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Ssipion Rim armiyasini boshqargan va Zama jangida Gannibalni tor-mor etgan.

**814.** Spartak kim?  
A) Gladiatorlar maktabidan qochgan frakiyalik qul  
B) Rim imperatori  
C) Karfagen sarkardasi  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak Kapuya gladiatorlik maktabidan qochib, qullar qo‘zg‘oloniga boshchilik qilgan.

**815.** Krass kim?  
A) Spartak qo‘zg‘olonini bostirgan Rim sarkardasi  
B) Karfagen sarkardasi  
C) Rim imperatori  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Krass boshchiligidagi Rim qo‘shini mil. avv. 71-yilda Spartak qo‘zg‘olonini bostirgan.

**816.** Rim shahriga asos solgan aka-ukalardan biri kim?  
A) Romul  
B) Gannibal  
C) Spartak  
D) Ssipion  
**To‘g‘ri: A.** Manba: Qadimgi Italiya. **Izoh:** Romul aka-ukasi Rem bilan birga Rim shahriga asos solgan.

**817.** Zama jangida Gannibalni tor-mor etgan Rim sarkardasi kim?  
A) Ssipion  
B) Krass  
C) Pompey  
D) Sezar  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Ssipion mil. avv. 202-yilda Zama jangida Gannibalni tor-mor etgan.

**818.** Spartak qo‘zg‘olonini bostirgan Rim sarkardasi kim?  
A) Krass  
B) Ssipion  
C) Pompey  
D) Sezar  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Krass boshchiligidagi Rim qo‘shini mil. avv. 71-yilda Spartak qo‘zg‘olonini bostirgan.

**819.** Rim imperiyasining asoschisi kim?  
A) Oktavian Avgust  
B) Yuliy Sezar  
C) Ssipion  
D) Krass  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Oktavian Avgust Rim imperiyasining asoschisi hisoblanadi.

**820.** Rimda Trayan ustuni qaysi imperator sharafiga qurilgan?  
A) Trayan  
B) Oktavian Avgust  
C) Neron  
D) Konstantin  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Trayan ustuni imperator Trayan sharafiga qurilgan.

**821.** Legion nima?  
A) Rim qo‘shinining asosiy bo‘linmasi  
B) Yunon harbiy kemasi  
C) Rimda xalq yig‘ilish maydoni  
D) Misrda kichik davlat  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim qo‘shinining asosiy bo‘linmasi legion deb atalgan.

**822.** Kogorta nima?  
A) Bir necha senturiydan iborat bo‘linma  
B) Rim qo‘shinining asosiy bo‘linmasi  
C) Yunon harbiy kemasi  
D) Rimda xalq yig‘ilish maydoni  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Bir necha senturiyalar kogortani tashkil etgan.

**823.** Provinsiya nima?  
A) Rim tomonidan bosib olingan hudud  
B) Rim qo‘shinining asosiy bo‘linmasi  
C) Yunon harbiy kemasi  
D) Rimda xalq yig‘ilish maydoni  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim tomonidan bosib olingan hududlar provinsiyalar deb atalgan.

**824.** Forum nima?  
A) Rimda xalq yig‘ilishlari va savdo-sotiq maydoni  
B) Rim qo‘shinining asosiy bo‘linmasi  
C) Yunon harbiy kemasi  
D) Misrda kichik davlat  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Forum Rimda xalq yig‘ilishlari va savdo-sotiq maydoni bo‘lib xizmat qilgan shahar markazi.

**825.** Akveduk nima?  
A) Rimda shaharga suv keltiruvchi baland ko‘prik  
B) Rim qo‘shinining asosiy bo‘linmasi  
C) Yunon harbiy kemasi  
D) Misrda kichik davlat  
**To‘g‘ri: A.** Manba: Rim respublikasining hayoti. **Izoh:** Akveduk — Rim davrida shaharga suv keltirish uchun qurilgan va ustida ariq bo‘lgan baland ko‘prik.

**826.** Rim qo‘shinining asosiy bo‘linmasi nima deb ataladi?  
A) legion  
B) kogorta  
C) senturiya  
D) falanga  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim qo‘shinining asosiy bo‘linmasi legion deb ataladi.

**827.** Bir necha senturiydan iborat bo‘linma nima deb ataladi?  
A) kogorta  
B) legion  
C) senturiya  
D) falanga  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Bir necha senturiyalar kogortani tashkil etgan.

**828.** Rim tomonidan bosib olingan hudud nima deb ataladi?  
A) provinsiya  
B) koloniya  
C) polis  
D) nom  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim tomonidan bosib olingan hudud provinsiya deb ataladi.

**829.** Rimda xalq yig‘ilishlari va savdo-sotiq maydoni nima deb ataladi?  
A) forum  
B) agora  
C) akropol  
D) senat  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Forum Rimda xalq yig‘ilishlari va savdo-sotiq maydoni bo‘lib xizmat qilgan.

**830.** Rimda shaharga suv keltiruvchi baland ko‘prik nima deb ataladi?  
A) akveduk  
B) forum  
C) terma  
D) panteon  
**To‘g‘ri: A.** Manba: Rim respublikasining hayoti. **Izoh:** Akveduk — Rimda shaharga suv keltirish uchun qurilgan baland ko‘prik.

**831.** Quyidagi Rim lavozimlarini vazifalari bilan moslashtiring:  
1) Konsul — a) oliy mansabdor shaxs  
2) Senat — b) oqsoqollar kengashi  
3) Xalq tribuni — c) qashshoqlar himoyachisi  
4) Diktator — d) urush davrida cheklanmagan hokimiyat  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Konsul — oliy mansabdor, Senat — oqsoqollar, Xalq tribuni — qashshoqlar himoyachisi, Diktator — urush davri hukmdori.

**832.** Quyidagi shaxslarni voqealari bilan moslashtiring:  
1) Romul — a) Rimga asos solgan  
2) Gannibal — b) Kann jangi  
3) Ssipion — c) Zama jangi  
4) Spartak — d) qullar qo‘zg‘oloni  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Romul — Rimga asos solgan, Gannibal — Kann jangi, Ssipion — Zama jangi, Spartak — qullar qo‘zg‘oloni.

**833.** Quyidagi Rim voqealarini sanalari bilan moslashtiring:  
1) Rimga asos solinishi — a) mil. avv. 753-yil  
2) Respublika e’lon qilinishi — b) mil. avv. 509-yil  
3) Kann jangi — c) mil. avv. 216-yil  
4) Karfagenning vayron etilishi — d) mil. avv. 146-yil  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Rim (753) → Respublika (509) → Kann (216) → Karfagen (146).

**834.** Quyidagi atamalarni ta’riflari bilan moslashtiring:  
1) Legion — a) qo‘shin bo‘linmasi  
2) Kogorta — b) bir necha senturiya  
3) Provinsiya — c) bosib olingan hudud  
4) Forum — d) shahar markazi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Legion — bo‘linma, Kogorta — senturiyalar yig‘indisi, Provinsiya — hudud, Forum — markaz.

**835.** Quyidagi Rim imperatorlarini faoliyatlari bilan moslashtiring:  
1) Oktavian Avgust — a) imperiya asoschisi  
2) Trayan — b) Trayan ustuni qurilgan  
3) Konstantin — c) xristianlikni teng din qilgan  
4) Odoakr — d) so‘nggi imperatorni taxtdan ag‘dargan  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Oktavian — asoschi, Trayan — ustun, Konstantin — xristianlik, Odoakr — so‘nggi imperatorni ag‘dargan.

**836.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Rimga asos solinishi  
2) Respublika e’lon qilinishi  
3) Kann jangi  
4) Karfagenning vayron etilishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Rim (753) → Respublika (509) → Kann (216) → Karfagen (146).

**837.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Spartak qo‘zg‘oloni  
2) Sezar diktaturasi  
3) Oktavian Avgust hukmronligi  
4) G‘arbiy Rim imperiyasining qulashi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Spartak (74–71) → Sezar (49) → Oktavian (29) → G‘arbiy Rim qulashi (476).

**838.** Quyidagi Rim imperatorlarini xronologik tartibga soling:  
1) Oktavian Avgust  
2) Trayan  
3) Konstantin  
4) Odoakr  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Oktavian (mil. avv. 29) → Trayan (milodiy 98) → Konstantin (milodiy 313) → Odoakr (milodiy 476).

**839.** Quyidagi O‘rta Osiyo voqealarini xronologik tartibga soling:  
1) Kir II massagetlar ustiga yurishi  
2) Aleksandr Maroqandani bosib olishi  
3) Spitaman qo‘zg‘oloni  
4) Yunon-Baqtriya davlatining tashkil topishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Kir II (530) → Maroqanda (329) → Spitaman (328) → Yunon-Baqtriya (250).

**840.** Quyidagi Kushon davlati voqealarini xronologik tartibga soling:  
1) Yuechjilar Yunon-Baqtriyani bosib olgan  
2) Kushon davlati tashkil topgan  
3) Kudzula Kadfiz hukmronligi  
4) Kanishka hukmronligi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** Yuechjilar (140–130) → Kushon davlati (mil. I asr) → Kudzula Kadfiz → Kanishka.

**841.** Quyidagi gap to‘g‘rimi? «Rimga mil. avv. 753-yilda asos solingan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 751-yilda asos solingan  
C) Noto‘g‘ri, chunki 755-yilda asos solingan  
D) Noto‘g‘ri, chunki 757-yilda asos solingan  
**To‘g‘ri: A.** Manba: Qadimgi Italiya. **Izoh:** Rimga mil. avv. 753-yilda asos solingan.

**842.** Quyidagi gap to‘g‘rimi? «Kann jangida Rim armiyasi g‘alaba qozongan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki Rim armiyasi tor-mor etilgan  
C) Noto‘g‘ri, chunki jang bo‘lib o‘tmagan  
D) Noto‘g‘ri, chunki Gannibal taslim bo‘lgan  
**To‘g‘ri: B.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Kann jangida Rim armiyasi Gannibal tomonidan tor-mor etilgan.

**843.** Quyidagi gap to‘g‘rimi? «Zama jangida Gannibal tor-mor etilgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki Ssipion tor-mor etilgan  
C) Noto‘g‘ri, chunki jang bo‘lib o‘tmagan  
D) Noto‘g‘ri, chunki Gannibal g‘alaba qozongan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Zama jangida Gannibal armiyasi rimliklar tomonidan tor-mor etilgan.

**844.** Quyidagi gap to‘g‘rimi? «Spartak qo‘zg‘oloni mil. avv. 74–71-yillarda bo‘lib o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 74–70-yillarda bo‘lgan  
C) Noto‘g‘ri, chunki 73–71-yillarda bo‘lgan  
D) Noto‘g‘ri, chunki Spartak qo‘zg‘oloni bo‘lmagan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘oloni mil. avv. 74–71-yillarda bo‘lib o‘tgan.

**845.** Quyidagi gap to‘g‘rimi? «G‘arbiy Rim imperiyasi milodiy 476-yilda qulagan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 474-yilda qulagan  
C) Noto‘g‘ri, chunki 478-yilda qulagan  
D) Noto‘g‘ri, chunki u qulmagan  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** G‘arbiy Rim imperiyasi milodiy 476-yilda qulagan.

**846.** Nega Rimda qullar mehnati samarasiz bo‘lgan?  
A) Qullarga haq to‘lanmagan va ular manfaatdor bo‘lmagan  
B) Qullar juda ko‘p bo‘lgan  
C) Qullar kasal bo‘lgan  
D) Qullar qochib ketgan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Qullarga haq to‘lanmaganligi sababli ular mehnatdan manfaatdor bo‘lmagan.

**847.** Nega Rimda kolonlar paydo bo‘lgan?  
A) Qullar mehnati samarasiz bo‘lib, yer ijarachilari kerak bo‘lgan  
B) Qullar qochib ketgan  
C) Yerlar bo‘sh qolgan  
D) Savdo rivojlangan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Qullar mehnati samarasiz bo‘lganligi sababli yerlar kolonlarga ijaraga berilgan.

**848.** Nega Rim imperiyasi G‘arbiy va Sharqiyga bo‘lingan?  
A) Imperiyani bir markazdan boshqarish qiyin bo‘lgan  
B) Rimda fuqarolar urushi boshlangan  
C) G‘arbiy Rim mustaqillik e’lon qilgan  
D) Sharqiy Rim imperiyasi kuchliroq bo‘lgan  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ulkan imperiyani Konstantinopoldan turib boshqarish qiyin bo‘lganligi sababli 395-yilda imperiya ikkiga bo‘lingan.

**849.** Nega G‘arbiy Rim imperiyasi qulagan?  
A) Ichki inqiroz va varvarlar hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi G‘arbiy Rimning qulashiga olib kelgan.

**850.** Nega Kushon davlati inqirozga yuz tutgan?  
A) To‘xtovsiz urushlar  
B) Savdoning rivojlanishi  
C) Buddaviylikning tarqalishi  
D) Xitoyning kuchayishi  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** To‘xtovsiz urushlar Kushon davlatining inqiroziga olib kelgan.

**851.** Rim respublikasining qulashiga sabab nima?  
A) Ichki ziddiyatlar va harbiy diktaturaning kuchayishi  
B) Karfagenning kuchayishi  
C) Yunonistonning hujumi  
D) Savdoning kamayishi  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ichki ziddiyatlar va harbiy diktaturaning kuchayishi respublikaning qulashiga olib kelgan.

**852.** Karfagenning vayron etilishiga sabab nima?  
A) Uchinchi Puni urushi va Rimning g‘alabasi  
B) Birinchi Puni urushi  
C) Ikkinchi Puni urushi  
D) Gannibalning taslim bo‘lishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Uchinchi Puni urushi natijasida mil. avv. 146-yilda Karfagen vayron etilgan.

**853.** G‘arbiy Rim imperiyasining qulashiga sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**854.** Misrning forslar tomonidan bosib olinishiga sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Mil. avv. 525-yilda fors shohi Kambiz II kuchli lashkar bilan Misrni bosib olgan.

**855.** Ossuriyaning qulashiga sabab nima?  
A) Bobil va Midiya tomonidan tor-mor etilishi  
B) Misr tomonidan bosib olinishi  
C) Forslar tomonidan bosib olinishi  
D) Ichki urushlar natijasida  
**To‘g‘ri: A.** Manba: Old Osiyo davlatlari. **Izoh:** Mil. avv. 605-yilda Ossuriya Bobil va Midiya tomonidan tor-mor etilgan.

**856.** Rim va Yunoniston xudolarining o‘xshash jihati nima?  
A) Ikkalasi ham ko‘p xudolikka ishonishgan  
B) Ikkalasi ham bir xil nomdagi xudolarga sig‘ingan  
C) Ikkalasi ham bir vaqtda vujudga kelgan  
D) Ikkalasi ham bir xil ibodatxonalarga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston afsonalari. **Izoh:** Rim ham, Yunoniston ham ko‘p xudolikka ishonishgan va xudolarga sig‘inishgan.

**857.** Rim va Karfagenning o‘xshash jihati nima?  
A) Ikkalasi ham O‘rtayer dengizida hukmronlik uchun kurashgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim va Karfagen O‘rtayer dengizida hukmronlik uchun kurashgan.

**858.** Rim va Yunoniston madaniyatlarining o‘xshash jihati nima?  
A) Ikkalasi ham teatr va me’morchilikni rivojlantirgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Ikkala madaniyatda ham teatr, me’morchilik va haykaltaroshlik rivojlangan.

**859.** Oktavian Avgust va Yuliy Sezarning o‘xshash jihati nima?  
A) Ikkalasi ham Rimda yakka hokimlik o‘rnatgan  
B) Ikkalasi ham bir vaqtda yashagan  
C) Ikkalasi ham Karfagenni bosib olgan  
D) Ikkalasi ham bir xil lavozimda bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ikkalasi ham Rimda yakka hokimlik o‘rnatgan.

**860.** Kushon va Yunon-Baqtriya davlatlarining o‘xshash jihati nima?  
A) Ikkalasi ham O‘rta Osiyo hududida tashkil topgan  
B) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
C) Ikkalasi ham bir vaqtda tashkil topgan  
D) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Ikkalasi ham O‘rta Osiyo hududida tashkil topgan.

**861.** Rim respublikasi va Rim imperiyasining farqi nimada?  
A) Respublikada konsullar saylangan, imperiyada yakka hukmdor bo‘lgan  
B) Respublikada imperator, imperiyada konsullar bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Respublikada podsho, imperiyada senat bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasi, Rim imperiyasining qulashi. **Izoh:** Respublikada konsullar saylangan, imperiyada esa yakka hukmdor (imperator) bo‘lgan.

**862.** Rim va Yunoniston madaniyatlarining farqi nimada?  
A) Rim me’morchiligida beton va gumbaz ishlatilgan, Yunonistonda ustunlar  
B) Yunonistonda beton, Rimda ustunlar ishlatilgan  
C) Ikkalasi ham bir xil me’morchilikka ega  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Rim me’morchiligida beton va gumbaz, Yunonistonda ustunlar keng ishlatilgan.

**863.** Oktavian Avgust va Yuliy Sezarning farqi nimada?  
A) Sezar diktator bo‘lgan, Oktavian imperator unvonini olgan  
B) Oktavian diktator bo‘lgan, Sezar imperator unvonini olgan  
C) Ikkalasi ham bir xil lavozimda bo‘lgan  
D) Ikkalasi ham bir vaqtda yashagan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar diktator bo‘lgan, Oktavian esa imperator unvonini olgan.

**864.** Kushon va Yunon-Baqtriya davlatlarining farqi nimada?  
A) Yunon-Baqtriya ellinistik madaniyatga, Kushon buddaviylikka asoslangan  
B) Yunon-Baqtriya buddaviylikka, Kushon ellinistik madaniyatga asoslangan  
C) Ikkalasi ham bir xil madaniyatga ega  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Yunon-Baqtriya ellinistik madaniyatga, Kushon davlati buddaviylik diniga asoslangan.

**865.** Rim va Karfagenning farqi nimada?  
A) Rim Italiyada, Karfagen Shimoliy Afrikada joylashgan  
B) Rim Shimoliy Afrikada, Karfagen Italiyada joylashgan  
C) Ikkalasi ham bir xil hududda joylashgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Qadimgi Italiya, O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim Italiyada, Karfagen Shimoliy Afrikada joylashgan.

**866.** «U 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.» Bu kim haqida?  
A) Zardusht  
B) Budda  
C) Iso  
D) Suqrot  
**To‘g‘ri: A.** Manba: Zardushtiylik. **Izoh:** Zardusht 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.

**867.** «U o‘zining dono hikmatlari tufayli shaharning eng aqlli kishisi deb e’lon qilingan edi.» Bu kim haqida?  
A) Suqrot  
B) Aflotun  
C) Arastu  
D) Diogen  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Suqrot o‘zining dono hikmatlari tufayli shaharning eng aqlli kishisi deb e’lon qilingan.

**868.** «Uning qo‘shini 5400 nafar jangchidan iborat bo‘lgan va u jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan.» Bu kim haqida?  
A) Sargon I  
B) Hammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**869.** «Ular uzum novdalaridan narvon to‘qib, ko‘rinmaydigan tarafdan cho‘qqidan pastga tushib oldilar.» Bu qaysi voqea haqida?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘olonida qullar Vezuviy tog‘idan uzum novdalaridan narvon to‘qib tushgan.

**870.** «Ular otlar qo‘shilgan jangovar aravalardan foydalanganlar.» Bu qaysi xalq haqida?  
A) giksoslar  
B) misrliklar  
C) shumerlar  
D) rimliklar  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Giksoslar otlar qo‘shilgan jangovar aravalardan foydalanganlar.

**871.** Nil daryosi qaysi dengizga quyiladi?  
A) O‘rtayer dengiziga  
B) Qizil dengizga  
C) Arabiston dengiziga  
D) Qora dengizga  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil daryosi O‘rtayer dengiziga quyiladi.

**872.** Dajla va Frot daryolari orasidagi hudud qanday ataladi?  
A) Misr  
B) Mesopotamiya  
C) Hindiston  
D) Xitoy  
**To‘g‘ri: B.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Dajla va Frot oralig‘i Mesopotamiya deb ataladi.

**873.** Behistun qoyalari qayerda joylashgan?  
A) Surxondaryoda  
B) Erondagi Kirmonshoh yaqinida  
C) Misrda Memfis yaqinida  
D) Bobilda  
**To‘g‘ri: B.** Manba: Qadimgi tarix bo‘yicha manbalar. **Izoh:** Behistun qoyalari Eronning Kirmonshoh shahri yaqinida.

**874.** Teshiktosh g‘ori qayerda joylashgan?  
A) Boysun tog‘larida  
B) Toshkent vohasida  
C) Farg‘ona vodiysida  
D) Boshqirdistonda  
**To‘g‘ri: A.** Manba: Eng qadimgi odamlarning mashg‘ulotlari. **Izoh:** Teshiktosh g‘ori Boysun tog‘larida joylashgan.

**875.** Qadimgi Yunonistonning eng baland tog‘i qaysi?  
A) Olimp  
B) Peloponnes  
C) Atos  
D) Parnas  
**To‘g‘ri: A.** Manba: Antik tarixning boshlanishi. **Izoh:** Yunonistonning eng baland tog‘i Olimp.

**876.** Quyidagilardan qaysi biri Misr fir’avni emas?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Hammurapi  
**To‘g‘ri: D.** Manba: Qadimgi Misr, Bobil podsholigi. **Izoh:** Hammurapi Bobil podshosi, qolganlar Misr fir’avnlari.

**877.** Quyidagilardan qaysi biri Mesopotamiya shahri emas?  
A) Uruk  
B) Ur  
C) Lagash  
D) Memfis  
**To‘g‘ri: D.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Memfis Misr shahri, qolganlar Mesopotamiya shaharlari.

**878.** Quyidagilardan qaysi biri yunon faylasufi emas?  
A) Geraklit  
B) Demokrit  
C) Suqrot  
D) Gannibal  
**To‘g‘ri: D.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Gannibal Karfagen sarkardasi, qolganlar yunon faylasuflari.

**879.** Quyidagilardan qaysi biri Rim shahri emas?  
A) Rim  
B) Pompeya  
C) Karfagen  
D) Neapol  
**To‘g‘ri: C.** Manba: Qadimgi Rim. **Izoh:** Karfagen Shimoliy Afrikadagi shahar, qolganlar Italiyadagi shaharlar.

**880.** Quyidagilardan qaysi biri O‘rta Osiyo manzilgohi emas?  
A) Teshiktosh  
B) Selungur  
C) Ko‘lbu loq  
D) Altamir  
**To‘g‘ri: D.** Manba: Eng qadimgi odamlar. **Izoh:** Altamir Ispaniyadagi g‘or, qolganlar O‘rta Osiyo manzilgohlari.

**881.** Misrni yagona davlatga birlashtirgan birinchi fir’avn kim?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Tutanxamon  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Menes Misrni birlashtirgan birinchi fir’avn.

**882.** Temirdan birinchi bo‘lib foydalangan xalq qaysi?  
A) xettlar  
B) misrliklar  
C) shumerlar  
D) yunonlar  
**To‘g‘ri: A.** Manba: Temir davriga o‘tish. **Izoh:** Temirdan birinchi bo‘lib Kichik Osiyodagi xettlar foydalangan.

**883.** Misr iyerogliflarini birinchi bo‘lib o‘qigan olim kim?  
A) Jak-Fransua Shampolyon  
B) Genrix Shlimann  
C) Gerodot  
D) Strabon  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati. **Izoh:** Shampolyon 1822-yilda iyerogliflarni birinchi bo‘lib o‘qigan.

**884.** Jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan shoh kim?  
A) Sargon I  
B) Hammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**885.** Birinchi Olimpiya o‘yinlari qachon o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**886.** Rim imperiyasining qulashiga eng muhim sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Savdoning rivojlanishi  
C) Sharqiy Rimning kuchayishi  
D) Rimda tinchlik o‘rnatilishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**887.** Misrning forslar tomonidan bosib olinishiga eng muhim sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Misrning zaiflashishi va fors lashkarining kuchliligi asosiy sabab.

**888.** Ossuriyaning qulashiga eng muhim sabab nima?  
A) Bobil va Midiya tomonidan tor-mor etilishi  
B) Misr tomonidan bosib olinishi  
C) Forslar tomonidan bosib olinishi  
D) Ichki urushlar natijasida  
**To‘g‘ri: A.** Manba: Old Osiyo davlatlari. **Izoh:** Bobil va Midiya tomonidan tor-mor etilishi asosiy sabab.

**889.** Yunon koloniyalarining tashkil topishiga eng muhim sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz va qarzdor yunonlarning vatanini tark etishi asosiy sabab.

**890.** Buyuk Ipak yo‘lining vujudga kelishiga eng muhim sabab nima?  
A) Sharq va G‘arb o‘rtasida savdo va madaniy aloqalar zarurati  
B) Xitoy imperatorining sayohat istagi  
C) Rim imperiyasining savdo talabi  
D) O‘rta Osiyoning mustaqilligi  
**To‘g‘ri: A.** Manba: Buyuk Ipak yo‘li. **Izoh:** Savdo va madaniy aloqalar zarurati asosiy sabab.

**891.** Nil daryosining uzunligi qancha?  
A) 4 ming km  
B) 5 ming km  
C) 6 ming km  
D) 7 ming km  
**To‘g‘ri: C.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil 6 ming kilometr uzunlikda.

**892.** Xufu piramidasining balandligi qancha?  
A) 100 metr  
B) 147 metr  
C) 200 metr  
D) 250 metr  
**To‘g‘ri: B.** Manba: Piramidalar va daxmalar. **Izoh:** Xufu piramidasi balandligi 147 metr.

**893.** Mumiyolash qancha kun davom etgan?  
A) 40 kun  
B) 50 kun  
C) 70 kun  
D) 90 kun  
**To‘g‘ri: C.** Manba: Piramidalar va daxmalar. **Izoh:** Mumiyolash qariyb 70 kun davom etgan.

**894.** Misr iyerogliflarining soni nechta?  
A) 500 ta  
B) 600 ta  
C) 750 ta  
D) 1000 ta  
**To‘g‘ri: C.** Manba: Qadimgi Misr madaniyati. **Izoh:** Misr alifbosi 750 iyeroglifdan iborat bo‘lgan.

**895.** Rim senati necha kishidan iborat bo‘lgan?  
A) 100 kishi  
B) 200 kishi  
C) 300 kishi  
D) 500 kishi  
**To‘g‘ri: C.** Manba: Rim respublikasi. **Izoh:** Rim senati 300 kishidan iborat bo‘lgan.

**896.** Vezuviy tog‘i qaysi voqea bilan bog‘liq?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak va uning tarafdorlari Vezuviy tog‘iga chiqib olgan.

**897.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**898.** Zama shahri qaysi voqea bilan bog‘liq?  
A) Gannibalning tor-mor etilishi  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 202-yilda Zama yaqinida Gannibal armiyasi tor-mor etilgan.

**899.** Kann shahri qaysi voqea bilan bog‘liq?  
A) Rim armiyasining tor-mor etilishi  
B) Karfagenning vayron etilishi  
C) Zama jangi  
D) Puni urushlari boshlanishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinida Rim armiyasi tor-mor etilgan.

**900.** Maroqanda shahri qaysi voqea bilan bog‘liq?  
A) Aleksandr tomonidan bosib olinishi  
B) Spitamanning halok bo‘lishi  
C) Yunon-Baqtriya davlatining tashkil topishi  
D) Kushon davlatining tashkil topishi  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Mil. avv. 329-yilda Aleksandr Maroqandani bosib olgan.
"""

class Command(BaseCommand):
    help = "801-900 testlarni bazaga va JSON keshiga biriktirish"

    def handle(self, *args, **options):
        self.stdout.write("801-900 testlarni tahlil qilish va bazaga joylashtirish boshlandi...")

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\*\*\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\*\s*To‘g‘ri:\s*([A-D])\.\*\*\s*Manba:\s*(.*?)\.\s*\*\*\s*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|\n\n###|$)',
            USER_TEXT_801_900,
            re.DOTALL
        )

        self.stdout.write(f"Topilgan to'liq bloklar soni: {len(blocks)}")

        book = PdfBook.objects.filter(grade=6).first()
        topic = Topic.objects.filter(document=book).first() if book else None
        exam_cat = ExamCategory.objects.filter(code="PRACTICE").first()

        created_count = 0
        updated_count = 0

        for num_str, q_text, opt_a, opt_b, opt_c, opt_d, correct, source, explanation in blocks:
            num = int(num_str)

            q_clean = q_text.strip()
            a_clean = opt_a.strip()
            b_clean = opt_b.strip()
            c_clean = opt_c.strip()
            d_clean = opt_d.strip()
            correct_clean = correct.strip().upper()
            source_clean = source.strip()
            exp_clean = explanation.strip()

            c_hash = hashlib.md5(f"user_q6_v6_{num}_{q_clean}".encode('utf-8')).hexdigest()

            q, created = Question.objects.update_or_create(
                content_hash=c_hash,
                defaults={
                    'topic': topic,
                    'grade': 6,
                    'subject': "Qadimgi dunyo tarixi",
                    'exam_category': exam_cat,
                    'question_type': 'SINGLE_CHOICE',
                    'difficulty': 'HARD',
                    'text': q_clean,
                    'option_a': a_clean,
                    'option_b': b_clean,
                    'option_c': c_clean,
                    'option_d': d_clean,
                    'correct_option': correct_clean,
                    'explanation': exp_clean,
                    'short_explanation': exp_clean[:250],
                    'source_citation': source_clean,
                    'verification_status': 'VERIFIED',
                    'is_official_sample': True,
                    'is_generated_practice': False
                }
            )

            if created:
                created_count += 1
            else:
                updated_count += 1

        self.stdout.write(self.style.SUCCESS(f"\nMuvaffaqiyatli saqlandi! {created_count} ta yangi testlar bazaga qo'shildi, {updated_count} ta test yangilandi."))
