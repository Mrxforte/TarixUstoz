import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_601_700 = """
**601.** Mil. avv. 490-yilda qanday voqea yuz bergan?  
A) Marafon tekisligida yunonlar va forslar o‘rtasida jang bo‘lib o‘tgan  
B) Fermopil darasida spartaliklar forslarga qarshi kurashgan  
C) Salamin bo‘g‘ozida yunon floti forslarni tor-mor etgan  
D) Plateya shahri yaqinida yunonlar forslar ustidan g‘alaba qozongan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Mil. avv. 490-yilda Marafon tekisligida yunonlar forslarni tor-mor etib, ularni kemalariga qarab chekinishga majbur qilganlar.

**602.** Mil. avv. 480-yilda qanday voqea yuz bergan?  
A) Fermopil jangi va Salamin dengiz jangi bo‘lib o‘tgan  
B) Marafon jangi va Plateya jangi bo‘lib o‘tgan  
C) Xeroniya jangi va Korinf ittifoqi tuzilgan  
D) Aleksandr Fors davlatini bosib olgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Mil. avv. 480-yilda Fermopil darasida 300 spartalik jasorati ko‘rsatilgan va xuddi shu yili Salamin bo‘g‘ozida hal qiluvchi dengiz jangi bo‘lib o‘tgan.

**603.** Mil. avv. 443–429-yillarda qanday voqea yuz bergan?  
A) Perikl Afinada strateg lavozimida hukmronlik qilgan  
B) Solon Afinada islohotlar o‘tkazgan  
C) Drakont qonunlari qabul qilingan  
D) Marafon jangi bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Mil. avv. 443–429-yillarda Perikl 15 marta strateg etib saylangan va uning davrida Afina eng qudratli davlatga aylangan.

**604.** Mil. avv. 338-yilda qanday voqea yuz bergan?  
A) Xeroniya yaqinida yunonlar makedonlardan mag‘lub bo‘lgan  
B) Korinf ittifoqi tuzilgan  
C) Aleksandr Sharqqa yurish boshlagan  
D) Filipp II vafot etgan  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Mil. avv. 338-yilda Xeroniya yaqinidagi jangda yunonlar makedonlar tomonidan tor-mor etilgan va Yunoniston Makedoniyaga qaram bo‘lgan.

**605.** Mil. avv. 330-yilda qanday voqea yuz bergan?  
A) Aleksandr Fors davlatini bosib olgan  
B) Aleksandr Maroqandani egallagan  
C) Spitaman halok bo‘lgan  
D) Yunon-Baqtriya davlati tashkil topgan  
**To‘g‘ri: A.** Manba: Ahamoniylar davlati. **Izoh:** Mil. avv. 330-yilda Makedoniyalik Aleksandr Ahamoniylar sulolasining oxirgi shohi Doro III qo‘shinlarini tor-mor etib, Fors davlatini bosib olgan.

**606.** Marafon jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 490-yilda  
B) mil. avv. 480-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangi mil. avv. 490-yilda bo‘lib o‘tgan.

**607.** Fermopil jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 480-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangi mil. avv. 480-yilda bo‘lib o‘tgan.

**608.** Salamin jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 480-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin dengiz jangi mil. avv. 480-yilda bo‘lib o‘tgan.

**609.** Plateya jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 479-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 480-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Plateya shahri yaqinidagi jang mil. avv. 479-yilda bo‘lib o‘tgan.

**610.** Xeroniya jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 338-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 480-yilda  
D) mil. avv. 479-yilda  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Xeroniya jangi mil. avv. 338-yilda bo‘lib o‘tgan.

**611.** Miltiad kim?  
A) Marafon jangida yunon qo‘shinlarini boshqargan sarkarda  
B) Fermopil jangida spartaliklarni boshqargan podsho  
C) Salamin jangida yunon flotini boshqargan sarkarda  
D) Plateya jangida yunonlarni boshqargan sarkarda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunon qo‘shinlariga Miltiad boshchilik qilgan.

**612.** Leonid kim?  
A) Fermopil jangida spartaliklarni boshqargan podsho  
B) Marafon jangida yunonlarni boshqargan sarkarda  
C) Salamin jangida yunon flotini boshqargan sarkarda  
D) Plateya jangida yunonlarni boshqargan sarkarda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida spartaliklar podsho Leonid boshchiligida forslarga qarshi kurashgan.

**613.** Themistokl kim?  
A) Salamin jangida yunon flotini boshqargan sarkarda  
B) Fermopil jangida spartaliklarni boshqargan podsho  
C) Marafon jangida yunonlarni boshqargan sarkarda  
D) Plateya jangida yunonlarni boshqargan sarkarda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin dengiz jangida yunon flotini Themistokl boshqargan.

**614.** Demosfen kim?  
A) Yunon notig‘i, makedonlarga qarshi kurashga da’vat etgan  
B) Makedoniya podshosi, Yunonistonni bosib olgan  
C) Baqtriya satrapi, Aleksandrga qarshi kurashgan  
D) So‘g‘diyona qo‘zg‘oloni rahbari  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Demosfen Ellada shaharlarini kezib, aholini makedonlarga qarshi birlashishga da’vat etgan.

**615.** Filipp II kim?  
A) Makedoniya podshosi, Yunonistonni bosib olgan  
B) Yunon notig‘i, makedonlarga qarshi kurashgan  
C) Baqtriya satrapi, Aleksandrga qarshi kurashgan  
D) So‘g‘diyona qo‘zg‘oloni rahbari  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Filipp II Makedoniya podshosi bo‘lib, mil. avv. 338-yilda Xeroniya jangida yunonlarni tor-mor etgan.

**616.** Marafon jangida yunon qo‘shinlarini boshqargan sarkarda kim?  
A) Miltiad  
B) Leonid  
C) Themistokl  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunon qo‘shinlariga Miltiad boshchilik qilgan.

**617.** Fermopil jangida spartaliklarni boshqargan podsho kim?  
A) Leonid  
B) Miltiad  
C) Themistokl  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida spartaliklarni podsho Leonid boshqargan.

**618.** Yunonistonni bosib olgan Makedoniya podshosi kim?  
A) Filipp II  
B) Aleksandr  
C) Demosfen  
D) Bess  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Filipp II Yunonistonni bosib olgan Makedoniya podshosi.

**619.** Makedonlarga qarshi kurashga da’vat etgan yunon notig‘i kim?  
A) Demosfen  
B) Filipp II  
C) Aleksandr  
D) Bess  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Demosfen yunon shaharlari aholisini makedonlarga qarshi kurashga da’vat etgan.

**620.** Salamin jangida yunon flotini boshqargan sarkarda kim?  
A) Themistokl  
B) Miltiad  
C) Leonid  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin dengiz jangida yunon flotini Themistokl boshqargan.

**621.** Falanga nima?  
A) Piyodalarning 16 qator bo‘lib zich joylashuvi  
B) Yunon harbiy kemasining nomi  
C) Afinada xalq yig‘ilishlari maydoni  
D) Yunonistondagi qal’a va yuqori shahar  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Falanga — piyodalarning 16 qator bo‘lib zich joylashuvi bo‘lib, bu tizimda jangchilar bir-birini himoya qilgan.

**622.** Triyera nima?  
A) Yunon harbiy kemasining nomi  
B) Piyodalarning zich saf tizilishi  
C) Afinada xalq yig‘ilishlari maydoni  
D) Yunonistondagi qal’a va yuqori shahar  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Triyera — yunonlarning harbiy kemasi bo‘lib, u qoyalar orasidan va sayoz joylardan oson suzib o‘ta olgan.

**623.** Strateg nima?  
A) Afinada harbiy va davlat arbobi  
B) Yunon harbiy kemasining nomi  
C) Piyodalarning zich saf tizilishi  
D) Yunonistondagi qal’a va yuqori shahar  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Strateg — Afinada saylanadigan harbiy va davlat arbobi bo‘lib, Perikl bu lavozimga 15 marta saylangan.

**624.** Akropol nima?  
A) «Yuqori shahar», qal’a  
B) Yunon harbiy kemasining nomi  
C) Piyodalarning zich saf tizilishi  
D) Afinada xalq yig‘ilishlari maydoni  
**To‘g‘ri: A.** Manba: Qadimgi Yunonistonning yuksalishi. **Izoh:** Akropol — «Yuqori shahar» ma’nosini anglatuvchi qal’a bo‘lib, Afina shahri aynan shu qal’a atrofida vujudga kelgan.

**625.** Agora nima?  
A) Afinada xalq yig‘ilishlari maydoni  
B) Yunon harbiy kemasining nomi  
C) Piyodalarning zich saf tizilishi  
D) Yunonistondagi qal’a va yuqori shahar  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Agora — Afina shahrining markaziy maydoni bo‘lib, unda xalq yig‘ilishlari o‘tkazilgan.

**626.** Piyodalarning 16 qator bo‘lib zich joylashuvi nima deb ataladi?  
A) falanga  
B) triyera  
C) agora  
D) akropol  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Piyodalarning 16 qator bo‘lib zich joylashuvi falanga deb ataladi.

**627.** Yunon harbiy kemasi nima deb ataladi?  
A) triyera  
B) falanga  
C) agora  
D) akropol  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunon harbiy kemasi triyera deb ataladi.

**628.** Afinada xalq yig‘ilishlari maydoni nima deb ataladi?  
A) agora  
B) falanga  
C) triyera  
D) akropol  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Afinada xalq yig‘ilishlari maydoni agora deb ataladi.

**629.** Afinadagi «Yuqori shahar» qal’asi nima deb ataladi?  
A) akropol  
B) falanga  
C) triyera  
D) agora  
**To‘g‘ri: A.** Manba: Qadimgi Yunonistonning yuksalishi. **Izoh:** Afinadagi «Yuqori shahar» qal’asi akropol deb ataladi.

**630.** Yunonistonda xalq hokimiyati nima deb ataladi?  
A) demokratiya  
B) aristokratiya  
C) monarxiya  
D) o‘zini o‘zi boshqarish  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Yunonistonda xalq hokimiyati demokratiya deb ataladi.

**631.** Quyidagi janglarni yillari bilan moslashtiring:  
1) Marafon jangi — a) mil. avv. 490-yil  
2) Fermopil jangi — b) mil. avv. 480-yil  
3) Salamin jangi — c) mil. avv. 480-yil  
4) Plateya jangi — d) mil. avv. 479-yil  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon — 490, Fermopil — 480, Salamin — 480, Plateya — 479.

**632.** Quyidagi shaxslarni faoliyatlari bilan moslashtiring:  
1) Miltiad — a) Marafon jangi  
2) Leonid — b) Fermopil jangi  
3) Themistokl — c) Salamin jangi  
4) Filipp II — d) Xeroniya jangi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari, Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Miltiad — Marafon, Leonid — Fermopil, Themistokl — Salamin, Filipp II — Xeroniya.

**633.** Quyidagi atamalarni ta’riflari bilan moslashtiring:  
1) Falanga — a) zich saf tizilishi  
2) Triyera — b) harbiy kema  
3) Agora — c) xalq yig‘ilish maydoni  
4) Akropol — d) yuqori shahar qal’asi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston tarixi. **Izoh:** Falanga — zich saf, triyera — kema, agora — maydon, akropol — qal’a.

**634.** Quyidagi yunon xudolarini vazifalari bilan moslashtiring:  
1) Zevs — a) osmon va momaqaldiroq xudosi  
2) Poseydon — b) dengiz xudosi  
3) Aid — c) marhumlar saltanati xudosi  
4) Ares — d) urush xudosi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston afsonalari. **Izoh:** Zevs — osmon, Poseydon — dengiz, Aid — marhumlar saltanati, Ares — urush.

**635.** Quyidagi yunon faylasuflarini fikrlari bilan moslashtiring:  
1) Geraklit — a) hamma narsa olovdan  
2) Demokrit — b) hamma narsa atomlardan  
3) Diogen — c) ehtiyojlarsiz yashash  
4) Suqrot — d) «O‘z-o‘zingni angla»  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Geraklit — olov, Demokrit — atomlar, Diogen — ehtiyojsizlik, Suqrot — «O‘z-o‘zingni angla».

**636.** Quyidagi yunon-fors urushlaridagi janglarni xronologik tartibga soling:  
1) Marafon jangi  
2) Fermopil jangi  
3) Salamin jangi  
4) Plateya jangi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon (490) → Fermopil (480) → Salamin (480) → Plateya (479).

**637.** Quyidagi Yunoniston tarixidagi voqealarni xronologik tartibga soling:  
1) Solon islohotlari  
2) Perikl hukmronligi  
3) Xeroniya jangi  
4) Aleksandrning Forsga yurishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston tarixi. **Izoh:** Solon (594) → Perikl (443–429) → Xeroniya (338) → Aleksandr (334–330).

**638.** Quyidagi Rim tarixidagi voqealarni xronologik tartibga soling:  
1) Rimda respublika e’lon qilinishi  
2) Puni urushlari boshlanishi  
3) Yuliy Sezar diktaturasi  
4) G‘arbiy Rim imperiyasining qulashi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Respublika (509) → Puni urushlari (264) → Sezar (49) → G‘arbiy Rim qulashi (476).

**639.** Quyidagi O‘rta Osiyo tarixidagi voqealarni xronologik tartibga soling:  
1) Ahamoniylar hukmronligi  
2) Aleksandrning O‘rta Osiyoga yurishi  
3) Yunon-Baqtriya davlatining tashkil topishi  
4) Kushon davlatining tashkil topishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Ahamoniylar (VI asr) → Aleksandr (329) → Yunon-Baqtriya (250) → Kushon (milodiy I asr).

**640.** Quyidagi tosh davri bosqichlarini xronologik tartibga soling:  
1) Paleolit  
2) Mezolit  
3) Neolit  
4) Eneolit  
A) 1-2-3-4  
B) 1-3-2-4  
C) 2-1-3-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Tosh davri. **Izoh:** Paleolit → mezolit → neolit → eneolit.

**641.** Quyidagi gap to‘g‘rimi? «Marafon jangi mil. avv. 480-yilda bo‘lib o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 490-yilda bo‘lib o‘tgan  
C) Noto‘g‘ri, chunki 479-yilda bo‘lib o‘tgan  
D) Noto‘g‘ri, chunki 338-yilda bo‘lib o‘tgan  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangi mil. avv. 490-yilda bo‘lib o‘tgan, 480-yil esa Fermopil va Salamin janglarining yili hisoblanadi.

**642.** Quyidagi gap to‘g‘rimi? «Fermopil jangida 300 spartalik jasorat ko‘rsatgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 300 afinalik jasorat ko‘rsatgan  
C) Noto‘g‘ri, chunki 300 korinfik jasorat ko‘rsatgan  
D) Noto‘g‘ri, chunki Fermopil jangida spartaliklar qatnashmagan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida Leonid boshchiligidagi 300 spartalik qahramonlarcha halok bo‘lgan va bu voqea «Uch yuz spartalik jasorati» deb tarixda qolgan.

**643.** Quyidagi gap to‘g‘rimi? «Salamin jangi mil. avv. 479-yilda bo‘lib o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 480-yilda bo‘lib o‘tgan  
C) Noto‘g‘ri, chunki 490-yilda bo‘lib o‘tgan  
D) Noto‘g‘ri, chunki 338-yilda bo‘lib o‘tgan  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Salamin jangi mil. avv. 480-yilda bo‘lib o‘tgan.

**644.** Quyidagi gap to‘g‘rimi? «Xeroniya jangida yunonlar makedonlarni tor-mor etgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki yunonlar mag‘lub bo‘lgan  
C) Noto‘g‘ri, chunki jang bo‘lib o‘tmagan  
D) Noto‘g‘ri, chunki makedonlar taslim bo‘lgan  
**To‘g‘ri: B.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Xeroniya jangida yunonlar mag‘lub bo‘lib, Yunoniston Makedoniyaga qaram bo‘lib qolgan.

**645.** Quyidagi gap to‘g‘rimi? «Perikl 15 marta strateg etib saylangan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 10 marta saylangan  
C) Noto‘g‘ri, chunki 5 marta saylangan  
D) Noto‘g‘ri, chunki u strateg bo‘lmagan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Perikl mil. avv. 443–429-yillarda 15 marta strateg etib saylangan.

**646.** Yunon-fors urushlari boshlanishiga sabab nima?  
A) Forslarning yangi hududlarni bosib olishga intilishi  
B) Yunonlarning Forsga hujum qilishi  
C) Misrning Yunonistonga yurishi  
D) Karfagenning yunon koloniyalariga hujumi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fors davlati yangi-yangi mamlakatlarni bosib olishga intilgan va bu Yunonistonga tahdid solgan.

**647.** Marafon jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunonlar qoyali balandliklarga o‘rnashib, forslarning otliq qo‘shinidan foydalana olmasligi  
B) Forslar jangdan oldin taslim bo‘lishi  
C) Yunonlarga Karfagen yordam berishi  
D) Fors qo‘mondoni jangda halok bo‘lishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlar forslar uchun noqulay bo‘lgan qoyali balandliklarga o‘rnashib olgan va bu forslarning otliq qo‘shinidan foydalanishiga to‘sqinlik qilgan.

**648.** Fermopil jangida yunonlarning mag‘lubiyatiga sabab nima?  
A) Bir sotqinning forslarga tog‘lar orqali boshqa yo‘lni ko‘rsatib berishi  
B) Yunonlarning qurolsiz bo‘lishi  
C) Leonidning taslim bo‘lishi  
D) Forslarning dengizdan hujum qilishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Bir sotqin forslarga tog‘lar orqali o‘tgan boshqa yo‘lni ko‘rsatib bergan va forslar yunonlarni orqadan o‘rab olganlar.

**649.** Salamin jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunon kemalarining qoyalar orasidan va sayoz joylardan oson suzib o‘ta olishi  
B) Fors kemalarining katta va og‘ir bo‘lishi  
C) Yunonlarning ko‘p sonli flotga ega bo‘lishi  
D) Fors flotining jangsiz taslim bo‘lishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlarning triyeralari qoyalar orasidan va sayoz joylardan oson suzib o‘tgan, forslarning katta kemalari esa bu sharoitda harakatlana olmagan.

**650.** Yunonistonning Makedoniya tomonidan bosib olinishiga sabab nima?  
A) Yunon shahar-davlatlarining o‘zaro urushlari va tarqoqligi  
B) Yunonlarning makedonlar bilan ittifoqi  
C) Forslarning Yunonistonga yurishi  
D) Yunonistonda fuqarolar urushi  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Yunon shahar-davlatlari bir-birlari bilan urushayotgan paytda Makedoniya podshosi Filipp II Yunonistonni bosib olishni ko‘zlagan.

**651.** Forslarning Yunonistonga yurishlarining muvaffaqiyatsizlikka uchrashiga sabab nima?  
A) Yunonlarning vatanini himoya qilishdagi jasorati va birlashuvi  
B) Fors qo‘shinining soni kamligi  
C) Yunonlarning forslar bilan ittifoq tuzishi  
D) Forsda fuqarolar urushi boshlanishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlar vatanini himoya qilishda jasorat ko‘rsatib, forslarga qarshi birlashganlar.

**652.** Plateya jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunon shahar-davlatlarining birlashgan lashkari forslardan kuchliroq bo‘lgan  
B) Forslar jangsiz taslim bo‘lgan  
C) Yunonlarga Karfagen yordam bergan  
D) Fors qo‘mondoni jangda halok bo‘lgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Mil. avv. 479-yilda Plateya yaqinida yunonlarning birlashgan lashkari fors qo‘shinlarini tor-mor etgan.

**653.** Aleksandrning Fors davlatini bosib olishiga sabab nima?  
A) Fors davlatining zaiflashganligi va Aleksandrning kuchli qo‘shini  
B) Fors shohining taslim bo‘lishi  
C) Yunonlarning Forsga qarshi ittifoqi  
D) Misrning Forsga hujumi  
**To‘g‘ri: A.** Manba: Ahamoniylar davlati. **Izoh:** Mil. avv. 330-yilda Aleksandr Fors davlatining oxirgi shohi Doro III qo‘shinlarini tor-mor etgan.

**654.** Rim imperiyasining qulashiga sabab nima?  
A) Ichki inqiroz va varvar qabilalarining hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va german qabilalarining hujumi G‘arbiy Rimning qulashiga olib kelgan.

**655.** Yunon koloniyalarining tashkil topishiga sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz, qarzdor yunonlar vatanini tark etib, yangi yerlarda koloniya tuzganlar.

**656.** Afina va Sparta boshqaruvining o‘xshash jihati nima?  
A) Ikkalasi ham shahar-davlat bo‘lgan  
B) Ikkalasi ham demokratiya bo‘lgan  
C) Ikkalasi ham monarxiya bo‘lgan  
D) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Qadimgi Yunonistonning yuksalishi. **Izoh:** Afina ham, Sparta ham shahar-davlat bo‘lgan.

**657.** Marafon va Salamin janglarining o‘xshash jihati nima?  
A) Ikkalasi ham yunonlarning g‘alabasi bilan tugagan  
B) Ikkalasi ham dengizda bo‘lib o‘tgan  
C) Ikkalasi ham bir xil sarkarda boshchiligida bo‘lgan  
D) Ikkalasi ham bir xil yilda bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida ham, Salamin jangida ham yunonlar forslar ustidan g‘alaba qozonganlar.

**658.** Solon va Periklning o‘xshash jihati nima?  
A) Ikkalasi ham Afinada demokratiyani rivojlantirgan  
B) Ikkalasi ham bir xil lavozimda bo‘lgan  
C) Ikkalasi ham qonunlar tuzgan  
D) Ikkalasi ham bir vaqtda yashagan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Solon o‘z islohotlari bilan demokratiya asoslarini yaratgan, Perikl esa uning davrida demokratiyani yuksak cho‘qqiga olib chiqqan.

**659.** Misr va Mesopotamiya sivilizatsiyalarining o‘xshash jihati nima?  
A) Ikkalasi ham daryo vodiylarida vujudga kelgan  
B) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
C) Ikkalasi ham bir xil xudolarga sig‘ingan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Nil vodiysi, Mesopotamiya sivilizatsiyalari. **Izoh:** Misr Nil vodiysida, Mesopotamiya Dajla va Frot oralig‘ida vujudga kelgan.

**660.** Rim va Yunoniston madaniyatlarining o‘xshash jihati nima?  
A) Ikkalasi ham ko‘p xudolikka ishonishgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Rim ham, Yunoniston ham ko‘p xudolikka ishonishgan va xudolarga sig‘inishgan.

**661.** Afina va Sparta boshqaruvining farqi nimada?  
A) Afinada demokratiya, Spartada harbiy tuzum hukmron bo‘lgan  
B) Afinada monarxiya, Spartada demokratiya bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Afinada harbiy tuzum, Spartada demokratiya bo‘lgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya, Qadimgi Yunonistonning yuksalishi. **Izoh:** Afinada demokratiya rivojlangan bo‘lsa, Sparta harbiy tuzumga asoslangan shahar-davlat bo‘lgan.

**662.** Marafon va Fermopil janglarining farqi nimada?  
A) Marafonda yunonlar g‘alaba qozongan, Fermopolda mag‘lub bo‘lgan  
B) Marafonda yunonlar mag‘lub bo‘lgan, Fermopolda g‘alaba qozongan  
C) Ikkalasi ham bir xil natija bilan tugagan  
D) Ikkalasi ham dengizda bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunonlar forslarni tor-mor etgan, Fermopil jangida esa 300 spartalik qahramonlarcha halok bo‘lgan.

**663.** Solon va Drakont qonunlarining farqi nimada?  
A) Solon qonunlari yumshoqroq, Drakont qonunlari qattiqroq bo‘lgan  
B) Solon qonunlari qattiqroq, Drakont qonunlari yumshoqroq bo‘lgan  
C) Ikkalasi ham bir xil qonunlar  
D) Ikkalasi ham qullikni bekor qilgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Drakont qonunlari shu qadar qattiq va ayovsiz bo‘lganki, afinaliklar ularga «siyoh qolib, qon bilan yozilgan» deb ta’rif berishgan. Solon esa qarzlarni va qarzdorlarni qul qilishni bekor qilgan.

**664.** Misr va Mesopotamiya yozuvlarining farqi nimada?  
A) Misrda iyeroglif, Mesopotamiyada mixxat ishlatilgan  
B) Misrda mixxat, Mesopotamiyada iyeroglif ishlatilgan  
C) Ikkalasi ham bir xil yozuv ishlatgan  
D) Misrda yozuv bo‘lmagan  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati, Mesopotamiya sivilizatsiyalari. **Izoh:** Misrda iyeroglif yozuvi, Mesopotamiyada mixxat yozuvi ishlatilgan.

**665.** Rim respublikasi va Rim imperiyasining farqi nimada?  
A) Respublikada konsullar saylangan, imperiyada yakka hukmdor bo‘lgan  
B) Respublikada imperator, imperiyada konsullar bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Respublikada podsho, imperiyada senat bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasi, Rim imperiyasining qulashi. **Izoh:** Respublikada konsullar saylangan, imperiyada esa yakka hukmdor (imperator) bo‘lgan.

**666.** «U forslarning hujumlarini qaytarib turgan. Biroq bir sotqin forslarga tog‘lar orqali o‘tgan boshqa yo‘lni ko‘rsatib bergan.» Bu qaysi jang haqida?  
A) Fermopil jangi  
B) Marafon jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida Leonid forslarning hujumlarini qaytarib turgan, biroq bir sotqin forslarga tog‘lar orqali boshqa yo‘lni ko‘rsatib bergan.

**667.** «U 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.» Bu kim haqida?  
A) Zardusht  
B) Budda  
C) Iso  
D) Suqrot  
**To‘g‘ri: A.** Manba: Zardushtiylik. **Izoh:** Zardusht 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.

**668.** «Uning qo‘shini 5400 nafar jangchidan iborat bo‘lgan va u jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan.» Bu kim haqida?  
A) Sargon I  
B) Hammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**669.** «Ular otlar qo‘shilgan jangovar aravalardan foydalanganlar.» Bu qaysi xalq haqida?  
A) giksoslar  
B) misrliklar  
C) shumerlar  
D) rimliklar  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Giksoslar otlar qo‘shilgan jangovar aravalardan foydalanganlar.

**670.** «Ular uzum novdalaridan narvon to‘qib, ko‘rinmaydigan tarafdan cho‘qqidan pastga tushib oldilar.» Bu qaysi voqea haqida?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘olonida qullar Vezuviy tog‘idan uzum novdalaridan narvon to‘qib tushgan.

**671.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) 300 spartalik jasorati  
B) Marafon jangi  
C) Salamin dengiz jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**672.** Marafon tekisligi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon tekisligida mil. avv. 490-yilda yunonlar forslarni tor-mor etgan.

**673.** Salamin bo‘g‘ozi qaysi voqea bilan bog‘liq?  
A) Salamin dengiz jangi  
B) Marafon jangi  
C) Fermopil jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin bo‘g‘ozida mil. avv. 480-yilda hal qiluvchi dengiz jangi bo‘lib o‘tgan.

**674.** Plateya shahri qaysi voqea bilan bog‘liq?  
A) Yunonlarning forslar ustidan g‘alabasi  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Mil. avv. 479-yilda Plateya yaqinida yunonlarning birlashgan lashkari forslarni tor-mor etgan.

**675.** Xeroniya shahri qaysi voqea bilan bog‘liq?  
A) Yunonlarning makedonlardan mag‘lubiyati  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Mil. avv. 338-yilda Xeroniya yaqinida yunonlar makedonlardan mag‘lub bo‘lgan.

**676.** Quyidagilardan qaysi biri yunon-fors urushlaridagi jang emas?  
A) Xeroniya jangi  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Xeroniya jangi Makedoniya bilan bo‘lgan, qolganlar yunon-fors urushlaridagi janglar.

**677.** Quyidagilardan qaysi biri yunon faylasufi emas?  
A) Gannibal  
B) Geraklit  
C) Demokrit  
D) Suqrot  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Gannibal Karfagen sarkardasi, qolganlar yunon faylasuflari.

**678.** Quyidagilardan qaysi biri Rim imperatori emas?  
A) Perikl  
B) Oktavian Avgust  
C) Trayan  
D) Neron  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi, Afinada demokratiya. **Izoh:** Perikl Afina strategi, qolganlar Rim imperatorlari.

**679.** Quyidagilardan qaysi biri O‘rta Osiyo shahri emas?  
A) Memfis  
B) Maroqanda  
C) Baqtra  
D) Nautaka  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Memfis Misr shahri, qolganlar O‘rta Osiyo shaharlari.

**680.** Quyidagilardan qaysi biri Misr fir’avni emas?  
A) Hammurapi  
B) Menes  
C) Yaxmos  
D) Tutmos III  
**To‘g‘ri: A.** Manba: Qadimgi Misr, Bobil podsholigi. **Izoh:** Hammurapi Bobil podshosi, qolganlar Misr fir’avnlari.

**681.** Marafon jangida yunonlarni boshqargan birinchi sarkarda kim?  
A) Miltiad  
B) Leonid  
C) Themistokl  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunon qo‘shinlariga Miltiad boshchilik qilgan.

**682.** Misrni yagona davlatga birlashtirgan birinchi fir’avn kim?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Tutanxamon  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Menes Misrni birlashtirgan birinchi fir’avn.

**683.** Temirdan birinchi bo‘lib foydalangan xalq qaysi?  
A) xettlar  
B) misrliklar  
C) shumerlar  
D) yunonlar  
**To‘g‘ri: A.** Manba: Temir davriga o‘tish. **Izoh:** Temirdan birinchi bo‘lib Kichik Osiyodagi xettlar foydalangan.

**684.** Misr iyerogliflarini birinchi bo‘lib o‘qigan olim kim?  
A) Jak-Fransua Shampolyon  
B) Genrix Shlimann  
C) Gerodot  
D) Strabon  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati. **Izoh:** Shampolyon 1822-yilda iyerogliflarni birinchi bo‘lib o‘qigan.

**685.** Birinchi Olimpiya o‘yinlari qachon o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**686.** Yunon-fors urushlarida yunonlarning g‘alaba qozonishiga eng muhim sabab nima?  
A) Yunonlarning vatanini himoya qilishdagi birligi va jasorati  
B) Fors qo‘shinining soni kamligi  
C) Yunonlarning forslar bilan ittifoq tuzishi  
D) Forsda fuqarolar urushi boshlanishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlarning vatanini himoya qilishdagi birligi va jasorati g‘alabaning asosiy sababi bo‘lgan.

**687.** Rim imperiyasining qulashiga eng muhim sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Savdoning rivojlanishi  
C) Sharqiy Rimning kuchayishi  
D) Rimda tinchlik o‘rnatilishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**688.** Aleksandrning Forsni bosib olishiga eng muhim sabab nima?  
A) Fors davlatining zaiflashishi  
B) Fors shohining taslim bo‘lishi  
C) Yunonlarning Forsga qarshi ittifoqi  
D) Misrning Forsga hujumi  
**To‘g‘ri: A.** Manba: Ahamoniylar davlati. **Izoh:** Fors davlatining zaiflashishi va Aleksandrning kuchli qo‘shini asosiy sabab bo‘lgan.

**689.** Misrning forslar tomonidan bosib olinishiga eng muhim sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Misrning zaiflashishi va fors lashkarining kuchliligi asosiy sabab.

**690.** Yunon koloniyalarining tashkil topishiga eng muhim sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz va qarzdor yunonlarning vatanini tark etishi asosiy sabab.

**691.** Marafon jangi qaysi yilda bo‘lib o‘tgan?  
A) mil. avv. 490-yilda  
B) mil. avv. 480-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangi mil. avv. 490-yilda bo‘lib o‘tgan.

**692.** Salamin jangi qaysi yilda bo‘lib o‘tgan?  
A) mil. avv. 480-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin dengiz jangi mil. avv. 480-yilda bo‘lib o‘tgan.

**693.** Plateya jangi qaysi yilda bo‘lib o‘tgan?  
A) mil. avv. 479-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 480-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Plateya jangi mil. avv. 479-yilda bo‘lib o‘tgan.

**694.** Xeroniya jangi qaysi yilda bo‘lib o‘tgan?  
A) mil. avv. 338-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 480-yilda  
D) mil. avv. 479-yilda  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Xeroniya jangi mil. avv. 338-yilda bo‘lib o‘tgan.

**695.** Birinchi Olimpiya o‘yinlari qaysi yilda o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**696.** Marafon tekisligi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon tekisligida mil. avv. 490-yilda yunonlar forslarni tor-mor etgan.

**697.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) Fermopil jangi  
B) Marafon jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**698.** Salamin bo‘g‘ozi qaysi voqea bilan bog‘liq?  
A) Salamin dengiz jangi  
B) Marafon jangi  
C) Fermopil jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Salamin bo‘g‘ozida mil. avv. 480-yilda hal qiluvchi dengiz jangi bo‘lib o‘tgan.

**699.** Plateya shahri qaysi voqea bilan bog‘liq?  
A) Plateya jangi  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Plateya shahri yaqinida mil. avv. 479-yilda yunonlar forslar ustidan g‘alaba qozongan.

**700.** Xeroniya shahri qaysi voqea bilan bog‘liq?  
A) Xeroniya jangi  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Xeroniya shahri yaqinida mil. avv. 338-yilda yunonlar makedonlardan mag‘lub bo‘lgan.
"""

class Command(BaseCommand):
    help = "601-700 testlarni bazaga va JSON keshiga biriktirish"

    def handle(self, *args, **options):
        self.stdout.write("601-700 testlarni tahlil qilish va bazaga joylashtirish boshlandi...")

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\*\*\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\*\s*To‘g‘ri:\s*([A-D])\.\*\*\s*Manba:\s*(.*?)\.\s*\*\*\s*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|\n\n###|$)',
            USER_TEXT_601_700,
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

            c_hash = hashlib.md5(f"user_q6_v5_{num}_{q_clean}".encode('utf-8')).hexdigest()

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
