import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_1001_1100 = """
**1001.** Mil. avv. 776-yilda qanday voqea yuz bergan?  
A) Ilk bor Olimpiya o‘yinlari o‘tkazilgan  
B) Rim shahriga asos solingan  
C) Marafon jangi bo‘lib o‘tgan  
D) Solon islohotlari boshlangan  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Mil. avv. 776-yilda ilk bor Olimpiya o‘yinlari o‘tkazilgan va shu sanadan boshlab har to‘rt yilda bir marta o‘tkazilib turilgan.

**1002.** Mil. avv. 594-yilda qanday voqea yuz bergan?  
A) Solon islohotlari o‘tkazilgan  
B) Drakont qonunlari qabul qilingan  
C) Perikl strateg etib saylangan  
D) Marafon jangi bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Mil. avv. 594-yilda Solon davlatni idora qilishning oldingi aristokratiya tizimini demokratiyaga almashtirgan.

**1003.** Milodiy 313-yilda qanday voqea yuz bergan?  
A) Konstantin xristianlikni teng din deb e’lon qilgan  
B) Rim imperiyasi G‘arbiy va Sharqiyga bo‘lingan  
C) Konstantinopol poytaxt deb e’lon qilingan  
D) G‘arbiy Rim imperiyasi qulagan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati. **Izoh:** Milodiy 313-yilda imperator Konstantin xristianlikni boshqa dinlarga teng deb e’lon qilgan va bu farmon xristianlarga oshkora ibodat qilish imkonini bergan.

**1004.** Milodiy 410-yilda qanday voqea yuz bergan?  
A) Alarix boshchiligidagi gotlar Rimni talagan  
B) Attila boshchiligidagi xunnlar Italiyaga kirgan  
C) G‘arbiy Rim imperiyasi qulagan  
D) Rim imperiyasi ikkiga bo‘lingan  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Milodiy 410-yilda Alarix boshchiligidagi gotlar Rimni qurshab olib, shaharni uch kun davomida talaganlar.

**1005.** Mil. avv. 338-yilda qanday voqea yuz bergan?  
A) Xeroniya yaqinida yunonlar makedonlardan mag‘lub bo‘lgan  
B) Korinf ittifoqi tuzilgan  
C) Aleksandr Sharqqa yurish boshlagan  
D) Filipp II vafot etgan  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Mil. avv. 338-yilda Xeroniya yaqinidagi jangda yunonlar makedonlar tomonidan tor-mor etilgan va Yunoniston Makedoniyaga qaram bo‘lgan.

**1006.** Solon islohotlari qachon o‘tkazilgan?  
A) mil. avv. 594-yilda  
B) mil. avv. 591-yilda  
C) mil. avv. 597-yilda  
D) mil. avv. 600-yilda  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Solon islohotlari mil. avv. 594-yilda o‘tkazilgan.

**1007.** Drakont qonunlari qachon qabul qilingan?  
A) mil. avv. 621-yilda  
B) mil. avv. 618-yilda  
C) mil. avv. 624-yilda  
D) mil. avv. 627-yilda  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Drakont qonunlari mil. avv. 621-yilda qabul qilingan.

**1008.** Fermopil jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 480-yilda  
B) mil. avv. 490-yilda  
C) mil. avv. 479-yilda  
D) mil. avv. 338-yilda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangi mil. avv. 480-yilda bo‘lib o‘tgan.

**1009.** Konstantinopol poytaxt deb qachon e’lon qilingan?  
A) milodiy 330-yilda  
B) milodiy 328-yilda  
C) milodiy 332-yilda  
D) milodiy 334-yilda  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Milodiy 330-yilda Konstantinopol Rim imperiyasining poytaxti deb e’lon qilingan.

**1010.** G‘arbiy Rim imperiyasi qachon qulagan?  
A) milodiy 476-yilda  
B) milodiy 474-yilda  
C) milodiy 478-yilda  
D) milodiy 480-yilda  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Milodiy 476-yilda Odoakr so‘nggi imperator Romulni taxtdan ag‘darib, G‘arbiy Rim imperiyasining qulashiga olib kelgan.

**1011.** Miltiad kim?  
A) Marafon jangida yunon qo‘shinlarini boshqargan sarkarda  
B) Fermopil jangida spartaliklarni boshqargan podsho  
C) Salamin jangida yunon flotini boshqargan sarkarda  
D) Plateya jangida yunonlarni boshqargan sarkarda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunon qo‘shinlariga Miltiad boshchilik qilgan.

**1012.** Leonid kim?  
A) Fermopil jangida spartaliklarni boshqargan podsho  
B) Marafon jangida yunonlarni boshqargan sarkarda  
C) Salamin jangida yunon flotini boshqargan sarkarda  
D) Plateya jangida yunonlarni boshqargan sarkarda  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida spartaliklar podsho Leonid boshchiligida forslarga qarshi kurashgan.

**1013.** Demosfen kim?  
A) Yunon notig‘i, makedonlarga qarshi kurashga da’vat etgan  
B) Makedoniya podshosi, Yunonistonni bosib olgan  
C) Baqtriya satrapi, Aleksandrga qarshi kurashgan  
D) So‘g‘diyona qo‘zg‘oloni rahbari  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Demosfen Ellada shaharlarini kezib, aholini makedonlarga qarshi birlashishga da’vat etgan.

**1014.** Filipp II kim?  
A) Makedoniya podshosi, Yunonistonni bosib olgan  
B) Yunon notig‘i, makedonlarga qarshi kurashgan  
C) Baqtriya satrapi, Aleksandrga qarshi kurashgan  
D) So‘g‘diyona qo‘zg‘oloni rahbari  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Filipp II Makedoniya podshosi bo‘lib, mil. avv. 338-yilda Xeroniya jangida yunonlarni tor-mor etgan.

**1015.** Spitaman kim?  
A) So‘g‘diyona xalqining yunon-makedonlarga qarshi qo‘zg‘oloni rahbari  
B) Makedoniya podshosi  
C) Baqtriya satrapi  
D) Yunon faylasufi  
**To‘g‘ri: A.** Manba: O‘rta Osiyo xalqlarining yunon-makedon istilochilariga qarshi kurashi. **Izoh:** Spitaman so‘g‘diylar, baqtriyaliklar va saklarning yunon-makedonlarga qarshi qo‘zg‘oloniga boshchilik qilgan.

**1016.** Marafon jangida yunon qo‘shinlarini boshqargan sarkarda kim?  
A) Miltiad  
B) Leonid  
C) Themistokl  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunon qo‘shinlariga Miltiad boshchilik qilgan.

**1017.** Fermopil jangida spartaliklarni boshqargan podsho kim?  
A) Leonid  
B) Miltiad  
C) Themistokl  
D) Perikl  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida spartaliklarni podsho Leonid boshqargan.

**1018.** Yunonistonni bosib olgan Makedoniya podshosi kim?  
A) Filipp II  
B) Aleksandr  
C) Demosfen  
D) Bess  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Filipp II Yunonistonni bosib olgan Makedoniya podshosi.

**1019.** Makedonlarga qarshi kurashga da’vat etgan yunon notig‘i kim?  
A) Demosfen  
B) Filipp II  
C) Aleksandr  
D) Bess  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Demosfen yunon shaharlari aholisini makedonlarga qarshi kurashga da’vat etgan.

**1020.** So‘g‘diyona qo‘zg‘oloniga boshchilik qilgan shaxs kim?  
A) Spitaman  
B) Filipp II  
C) Aleksandr  
D) Bess  
**To‘g‘ri: A.** Manba: O‘rta Osiyo xalqlarining yunon-makedon istilochilariga qarshi kurashi. **Izoh:** Spitaman so‘g‘diyona qo‘zg‘oloniga boshchilik qilgan.

**1021.** Iyeroglif nima?  
A) Qadimgi Misrda ishlatilgan yozuv belgisi  
B) Mesopotamiyada ishlatilgan loy taxtacha  
C) Yunonistonda ishlatilgan yozuv  
D) Rimda ishlatilgan mum taxtacha  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati. **Izoh:** Iyeroglif — qadimgi Misrda ishlatilgan yozuv belgisi bo‘lib, u «toshga chekilgan muqaddas bitiklar» ma’nosini anglatadi.

**1022.** Mixxat nima?  
A) Mesopotamiyada ishlatilgan yozuv  
B) Misrda ishlatilgan yozuv  
C) Yunonistonda ishlatilgan yozuv  
D) Rimda ishlatilgan yozuv  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Mixxat — Mesopotamiyada shumerlar tomonidan ixtiro qilingan yozuv bo‘lib, uchi o‘tkirlangan tayoqchalar bilan loy taxtachalarga yozilgan.

**1023.** Papirus nima?  
A) Qadimgi Misrda yozuv yozish uchun ishlatilgan material  
B) Mesopotamiyada ishlatilgan loy taxtacha  
C) Yunonistonda ishlatilgan pergament  
D) Rimda ishlatilgan mum taxtacha  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Papirus — qadimgi misrliklar yozuv uchun foydalangan qamish poyasidan ishlangan qog‘oz.

**1024.** Kasta nima?  
A) Hindistondagi ijtimoiy tabaqa  
B) Xitoydagi harbiy bo‘linma  
C) Rimdagi mansab  
D) Misrdagi diniy lavozim  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Kasta (varna) — qadimgi hindlarning kasb-hunariga qarab bo‘lingan to‘rt guruhli ijtimoiy tabaqasi.

**1025.** Sanskrt nima?  
A) Qadimgi hind adabiy tili  
B) Misr yozuvi  
C) Mesopotamiya yozuvi  
D) Yunon tili  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Sanskrit — qadimiy hind tillari asosida vujudga kelgan adabiy til bo‘lib, «Mahabharat» va «Ramayana» dostonlari shu tilda yozilgan.

**1026.** Qadimgi Misrda ishlatilgan yozuv belgisi nima deb ataladi?  
A) iyeroglif  
B) mixxat  
C) runik  
D) oromiy  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati. **Izoh:** Qadimgi Misrda ishlatilgan yozuv belgilari iyerogliflar deb ataladi.

**1027.** Mesopotamiyada ishlatilgan yozuv nima deb ataladi?  
A) mixxat  
B) iyeroglif  
C) runik  
D) oromiy  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Mesopotamiyada ishlatilgan yozuv mixxat deb ataladi.

**1028.** Hindistondagi ijtimoiy tabaqa nima deb ataladi?  
A) kasta  
B) falanga  
C) kohin  
D) nom  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Hindistondagi ijtimoiy tabaqa kasta (varna) deb ataladi.

**1029.** Qadimgi hind adabiy tili nima deb ataladi?  
A) sanskrit  
B) iyeroglif  
C) mixxat  
D) yunon  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Qadimgi hind adabiy tili sanskrit deb ataladi.

**1030.** Xitoyda ishlatilgan yozuv belgisi nima deb ataladi?  
A) iyeroglif  
B) mixxat  
C) runik  
D) oromiy  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Xitoyda so‘z va jumlalarni ifodalagan iyeroglif yozuvi ishlatilgan.

**1031.** Quyidagi yozuvlarni davlatlar bilan moslashtiring:  
1) Iyeroglif — a) Misr  
2) Mixxat — b) Mesopotamiya  
3) Sanskrit — c) Hindiston  
4) Oromiy — d) O‘rta Osiyo  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Sharq madaniyati. **Izoh:** Iyeroglif — Misr, Mixxat — Mesopotamiya, Sanskrit — Hindiston, Oromiy — O‘rta Osiyo (Kushon davri).

**1032.** Quyidagi hukmdorlarni davlatlari bilan moslashtiring:  
1) Salavk — a) Salavkiylar davlati  
2) Diodot — b) Yunon-Baqtriya davlati  
3) Kudzula Kadfiz — c) Kushon davlati  
4) Kanishka — d) Kushon podsholigi cho‘qqisi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Salavk — Salavkiylar, Diodot — Yunon-Baqtriya, Kudzula Kadfiz — Kushon asoschisi, Kanishka — Kushon cho‘qqisi.

**1033.** Quyidagi atamalarni ta’riflari bilan moslashtiring:  
1) Nmana — a) katta patriarxal oila  
2) Vis — b) urug‘ jamoasi  
3) Varzana — c) hududiy qo‘shnichilik jamoasi  
4) Dax’yu — d) qabilalar ittifoqi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Temir davriga o‘tishda O‘rta Osiyoning rivojlanishi. **Izoh:** Nmana — oila, Vis — urug‘, Varzana — qo‘shnichilik jamoasi, Dax’yu — qabilalar ittifoqi.

**1034.** Quyidagi Rim lavozimlarini vazifalari bilan moslashtiring:  
1) Konsul — a) oliy mansabdor shaxs  
2) Senat — b) oqsoqollar kengashi  
3) Xalq tribuni — c) qashshoqlar himoyachisi  
4) Diktator — d) urush davrida cheklanmagan hokimiyat  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Konsul — oliy mansabdor, Senat — oqsoqollar, Xalq tribuni — qashshoqlar himoyachisi, Diktator — urush davri hukmdori.

**1035.** Quyidagi yunon xudolarini vazifalari bilan moslashtiring:  
1) Zevs — a) osmon va momaqaldiroq xudosi  
2) Poseydon — b) dengiz xudosi  
3) Aid — c) marhumlar saltanati xudosi  
4) Ares — d) urush xudosi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston afsonalari. **Izoh:** Zevs — osmon, Poseydon — dengiz, Aid — marhumlar saltanati, Ares — urush.

**1036.** Quyidagi jahon tarixi voqealarini xronologik tartibga soling:  
1) Misrning yagona davlatga birlashishi  
2) Bobilning yagona davlatga birlashishi  
3) Ossuriyaning qulashi  
4) Ahamoniylar davlatining zabt etilishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Qadimgi Sharq tarixi. **Izoh:** Misr (mil. avv. 3000) → Bobil (mil. avv. XVIII asr) → Ossuriya qulashi (mil. avv. 605) → Ahamoniylar zabt etilishi (mil. avv. 330).

**1037.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Rimda respublika e’lon qilinishi  
2) Birinchi Puni urushi boshlanishi  
3) Yuliy Sezar diktaturasi  
4) Oktavian Avgust imperator bo‘lishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Respublika (mil. avv. 509) → Birinchi Puni urushi (mil. avv. 264) → Sezar (mil. avv. 49) → Oktavian (mil. avv. 29).

**1038.** Quyidagi O‘rta Osiyo voqealarini xronologik tartibga soling:  
1) Ahamoniylar hukmronligi  
2) Aleksandrning O‘rta Osiyoga yurishi  
3) Yunon-Baqtriya davlatining tashkil topishi  
4) Kushon davlatining tashkil topishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Ahamoniylar (VI asr) → Aleksandr (329) → Yunon-Baqtriya (250) → Kushon (milodiy I asr).

**1039.** Quyidagi tosh davri bosqichlarini xronologik tartibga soling:  
1) Paleolit  
2) Mezolit  
3) Neolit  
4) Eneolit  
A) 1-2-3-4  
B) 1-3-2-4  
C) 2-1-3-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Tosh davri. **Izoh:** Paleolit → mezolit → neolit → eneolit.

**1040.** Quyidagi Misr voqealarini xronologik tartibga soling:  
1) Menes Misrni birlashtirgan  
2) Giksoslar Misrni bosib olgan  
3) Yaxmos giksoslarni haydab chiqargan  
4) Kambiz II Misrni bosib olgan  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Menes (mil. avv. 3000) → giksoslar (mil. avv. XVIII asr) → Yaxmos → Kambiz II (mil. avv. 525).

**1041.** Quyidagi gap to‘g‘rimi? «Solon islohotlari mil. avv. 621-yilda o‘tkazilgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 594-yilda o‘tkazilgan  
C) Noto‘g‘ri, chunki 610-yilda o‘tkazilgan  
D) Noto‘g‘ri, chunki Solon islohotlari bo‘lmagan  
**To‘g‘ri: B.** Manba: Afinada demokratiya. **Izoh:** Solon islohotlari mil. avv. 594-yilda o‘tkazilgan, 621-yil esa Drakont qonunlarining yili.

**1042.** Quyidagi gap to‘g‘rimi? «Drakont qonunlari shu qadar qattiq bo‘lganki, afinaliklar ularga “siyoh qolib, qon bilan yozilgan” deb ta’rif berishgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki qonunlar yumshoq bo‘lgan  
C) Noto‘g‘ri, chunki Drakont qonunlari bo‘lmagan  
D) Noto‘g‘ri, chunki ular Solon qonunlari  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Drakont qonunlari shu qadar qattiq va ayovsiz bo‘lganki, afinaliklar ularga «siyoh qolib, qon bilan yozilgan» deb ta’rif berishgan.

**1043.** Quyidagi gap to‘g‘rimi? «Perikl 15 marta strateg etib saylangan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 10 marta saylangan  
C) Noto‘g‘ri, chunki 5 marta saylangan  
D) Noto‘g‘ri, chunki u strateg bo‘lmagan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Perikl mil. avv. 443–429-yillarda 15 marta strateg etib saylangan.

**1044.** Quyidagi gap to‘g‘rimi? «Fermopil jangida yunonlar g‘alaba qozongan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 300 spartalik halok bo‘lgan  
C) Noto‘g‘ri, chunki jang bo‘lib o‘tmagan  
D) Noto‘g‘ri, chunki Leonid taslim bo‘lgan  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida 300 spartalik qahramonlarcha halok bo‘lgan va bu voqea «Uch yuz spartalik jasorati» deb tarixda qolgan.

**1045.** Quyidagi gap to‘g‘rimi? «Marafon jangida yunonlar forslarni tor-mor etgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki yunonlar mag‘lub bo‘lgan  
C) Noto‘g‘ri, chunki jang bo‘lib o‘tmagan  
D) Noto‘g‘ri, chunki forslar taslim bo‘lgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunonlar forslarni tor-mor etib, ularni kemalariga qarab chekinishga majbur qilganlar.

**1046.** Yunon-fors urushlari boshlanishiga sabab nima?  
A) Forslarning yangi hududlarni bosib olishga intilishi  
B) Yunonlarning Forsga hujum qilishi  
C) Misrning Yunonistonga yurishi  
D) Karfagenning yunon koloniyalariga hujumi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fors davlati yangi-yangi mamlakatlarni bosib olishga intilgan va bu Yunonistonga tahdid solgan.

**1047.** Marafon jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunonlar qoyali balandliklarga o‘rnashib, forslarning otliq qo‘shinidan foydalana olmasligi  
B) Forslar jangdan oldin taslim bo‘lishi  
C) Yunonlarga Karfagen yordam berishi  
D) Fors qo‘mondoni jangda halok bo‘lishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlar forslar uchun noqulay bo‘lgan qoyali balandliklarga o‘rnashib olgan va bu forslarning otliq qo‘shinidan foydalanishiga to‘sqinlik qilgan.

**1048.** Fermopil jangida yunonlarning mag‘lubiyatiga sabab nima?  
A) Bir sotqinning forslarga tog‘lar orqali boshqa yo‘lni ko‘rsatib berishi  
B) Yunonlarning qurolsiz bo‘lishi  
C) Leonidning taslim bo‘lishi  
D) Forslarning dengizdan hujum qilishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Bir sotqin forslarga tog‘lar orqali o‘tgan boshqa yo‘lni ko‘rsatib bergan va forslar yunonlarni orqadan o‘rab olganlar.

**1049.** Salamin jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunon kemalarining qoyalar orasidan va sayoz joylardan oson suzib o‘ta olishi  
B) Fors kemalarining katta va og‘ir bo‘lishi  
C) Yunonlarning ko‘p sonli flotga ega bo‘lishi  
D) Fors flotining jangsiz taslim bo‘lishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlarning triyeralari qoyalar orasidan va sayoz joylardan oson suzib o‘tgan, forslarning katta kemalari esa bu sharoitda harakatlana olmagan.

**1050.** Yunonistonning Makedoniya tomonidan bosib olinishiga sabab nima?  
A) Yunon shahar-davlatlarining o‘zaro urushlari va tarqoqligi  
B) Yunonlarning makedonlar bilan ittifoqi  
C) Forslarning Yunonistonga yurishi  
D) Yunonistonda fuqarolar urushi  
**To‘g‘ri: A.** Manba: Yunonistonning Makedoniya tomonidan bosib olinishi. **Izoh:** Yunon shahar-davlatlari bir-birlari bilan urushayotgan paytda Makedoniya podshosi Filipp II Yunonistonni bosib olishni ko‘zlagan.

**1051.** Forslarning Yunonistonga yurishlarining muvaffaqiyatsizlikka uchrashiga sabab nima?  
A) Yunonlarning vatanini himoya qilishdagi jasorati va birlashuvi  
B) Fors qo‘shinining soni kamligi  
C) Yunonlarning forslar bilan ittifoq tuzishi  
D) Forsda fuqarolar urushi boshlanishi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Yunonlar vatanini himoya qilishda jasorat ko‘rsatib, forslarga qarshi birlashganlar.

**1052.** Plateya jangida yunonlarning g‘alaba qozonishiga sabab nima?  
A) Yunon shahar-davlatlarining birlashgan lashkari forslardan kuchliroq bo‘lgan  
B) Forslar jangsiz taslim bo‘lgan  
C) Yunonlarga Karfagen yordam bergan  
D) Fors qo‘mondoni jangda halok bo‘lgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Mil. avv. 479-yilda Plateya yaqinida yunonlarning birlashgan lashkari fors qo‘shinlarini tor-mor etgan.

**1053.** Aleksandrning Fors davlatini bosib olishiga sabab nima?  
A) Fors davlatining zaiflashganligi va Aleksandrning kuchli qo‘shini  
B) Fors shohining taslim bo‘lishi  
C) Yunonlarning Forsga qarshi ittifoqi  
D) Misrning Forsga hujumi  
**To‘g‘ri: A.** Manba: Ahamoniylar davlati. **Izoh:** Mil. avv. 330-yilda Aleksandr Fors davlatining oxirgi shohi Doro III qo‘shinlarini tor-mor etgan.

**1054.** Rim imperiyasining qulashiga sabab nima?  
A) Ichki inqiroz va varvar qabilalarining hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va german qabilalarining hujumi G‘arbiy Rimning qulashiga olib kelgan.

**1055.** Yunon koloniyalarining tashkil topishiga sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz, qarzdor yunonlar vatanini tark etib, yangi yerlarda koloniya tuzganlar.

**1056.** Afina va Sparta boshqaruvining o‘xshash jihati nima?  
A) Ikkalasi ham shahar-davlat bo‘lgan  
B) Ikkalasi ham demokratiya bo‘lgan  
C) Ikkalasi ham monarxiya bo‘lgan  
D) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Qadimgi Yunonistonning yuksalishi. **Izoh:** Afina ham, Sparta ham shahar-davlat bo‘lgan.

**1057.** Marafon va Salamin janglarining o‘xshash jihati nima?  
A) Ikkalasi ham yunonlarning g‘alabasi bilan tugagan  
B) Ikkalasi ham dengizda bo‘lib o‘tgan  
C) Ikkalasi ham bir xil sarkarda boshchiligida bo‘lgan  
D) Ikkalasi ham bir xil yilda bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida ham, Salamin jangida ham yunonlar forslar ustidan g‘alaba qozonganlar.

**1058.** Solon va Periklning o‘xshash jihati nima?  
A) Ikkalasi ham Afinada demokratiyani rivojlantirgan  
B) Ikkalasi ham bir xil lavozimda bo‘lgan  
C) Ikkalasi ham qonunlar tuzgan  
D) Ikkalasi ham bir vaqtda yashagan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Solon o‘z islohotlari bilan demokratiya asoslarini yaratgan, Perikl esa uning davrida demokratiyani yuksak cho‘qqiga olib chiqqan.

**1059.** Misr va Mesopotamiya sivilizatsiyalarining o‘xshash jihati nima?  
A) Ikkalasi ham daryo vodiylarida vujudga kelgan  
B) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
C) Ikkalasi ham bir xil xudolarga sig‘ingan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Nil vodiysi, Mesopotamiya sivilizatsiyalari. **Izoh:** Misr Nil vodiysida, Mesopotamiya Dajla va Frot oralig‘ida vujudga kelgan.

**1060.** Rim va Yunoniston madaniyatlarining o‘xshash jihati nima?  
A) Ikkalasi ham ko‘p xudolikka ishonishgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Rim ham, Yunoniston ham ko‘p xudolikka ishonishgan va xudolarga sig‘inishgan.

**1061.** Afina va Sparta boshqaruvining farqi nimada?  
A) Afinada demokratiya, Spartada harbiy tuzum hukmron bo‘lgan  
B) Afinada monarxiya, Spartada demokratiya bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Afinada harbiy tuzum, Spartada demokratiya bo‘lgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya, Qadimgi Yunonistonning yuksalishi. **Izoh:** Afinada demokratiya rivojlangan bo‘lsa, Sparta harbiy tuzumga asoslangan shahar-davlat bo‘lgan.

**1062.** Marafon va Fermopil janglarining farqi nimada?  
A) Marafonda yunonlar g‘alaba qozongan, Fermopolda mag‘lub bo‘lgan  
B) Marafonda yunonlar mag‘lub bo‘lgan, Fermopolda g‘alaba qozongan  
C) Ikkalasi ham bir xil natija bilan tugagan  
D) Ikkalasi ham dengizda bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Marafon jangida yunonlar forslarni tor-mor etgan, Fermopil jangida esa 300 spartalik qahramonlarcha halok bo‘lgan.

**1063.** Solon va Drakont qonunlarining farqi nimada?  
A) Solon qonunlari yumshoqroq, Drakont qonunlari qattiqroq bo‘lgan  
B) Solon qonunlari qattiqroq, Drakont qonunlari yumshoqroq bo‘lgan  
C) Ikkalasi ham bir xil qonunlar  
D) Ikkalasi ham qullikni bekor qilgan  
**To‘g‘ri: A.** Manba: Afinada demokratiya. **Izoh:** Drakont qonunlari shu qadar qattiq va ayovsiz bo‘lganki, afinaliklar ularga «siyoh qolib, qon bilan yozilgan» deb ta’rif berishgan. Solon esa qarzlarni va qarzdorlarni qul qilishni bekor qilgan.

**1064.** Misr va Mesopotamiya yozuvlarining farqi nimada?  
A) Misrda iyeroglif, Mesopotamiyada mixxat ishlatilgan  
B) Misrda mixxat, Mesopotamiyada iyeroglif ishlatilgan  
C) Ikkalasi ham bir xil yozuv ishlatgan  
D) Misrda yozuv bo‘lmagan  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati, Mesopotamiya sivilizatsiyalari. **Izoh:** Misrda iyeroglif yozuvi, Mesopotamiyada mixxat yozuvi ishlatilgan.

**1065.** Rim respublikasi va Rim imperiyasining farqi nimada?  
A) Respublikada konsullar saylangan, imperiyada yakka hukmdor bo‘lgan  
B) Respublikada imperator, imperiyada konsullar bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Respublikada podsho, imperiyada senat bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasi, Rim imperiyasining qulashi. **Izoh:** Respublikada konsullar saylangan, imperiyada esa yakka hukmdor (imperator) bo‘lgan.

**1066.** «U forslarning hujumlarini qaytarib turgan. Biroq bir sotqin forslarga tog‘lar orqali o‘tgan boshqa yo‘lni ko‘rsatib bergan.» Bu qaysi jang haqida?  
A) Fermopil jangi  
B) Marafon jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: A.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil jangida Leonid forslarning hujumlarini qaytarib turgan, biroq bir sotqin forslarga tog‘lar orqali boshqa yo‘lni ko‘rsatib bergan.

**1067.** «U 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.» Bu kim haqida?  
A) Zardusht  
B) Budda  
C) Iso  
D) Suqrot  
**To‘g‘ri: A.** Manba: Zardushtiylik. **Izoh:** Zardusht 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.

**1068.** «Uning qo‘shini 5400 nafar jangchidan iborat bo‘lgan va u jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan.» Bu kim haqida?  
A) Sargon I  
B) Hammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**1069.** «Ular otlar qo‘shilgan jangovar aravalardan foydalanganlar.» Bu qaysi xalq haqida?  
A) giksoslar  
B) misrliklar  
C) shumerlar  
D) rimliklar  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Giksoslar otlar qo‘shilgan jangovar aravalardan foydalanganlar.

**1070.** «Ular uzum novdalaridan narvon to‘qib, ko‘rinmaydigan tarafdan cho‘qqidan pastga tushib oldilar.» Bu qaysi voqea haqida?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘olonida qullar Vezuviy tog‘idan uzum novdalaridan narvon to‘qib tushgan.

**1071.** Nil daryosi qaysi dengizga quyiladi?  
A) O‘rtayer dengiziga  
B) Qizil dengizga  
C) Arabiston dengiziga  
D) Qora dengizga  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil daryosi O‘rtayer dengiziga quyiladi.

**1072.** Dajla va Frot daryolari orasidagi hudud qanday ataladi?  
A) Misr  
B) Mesopotamiya  
C) Hindiston  
D) Xitoy  
**To‘g‘ri: B.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Dajla va Frot oralig‘i Mesopotamiya deb ataladi.

**1073.** Behistun qoyalari qayerda joylashgan?  
A) Surxondaryoda  
B) Erondagi Kirmonshoh yaqinida  
C) Misrda Memfis yaqinida  
D) Bobilda  
**To‘g‘ri: B.** Manba: Qadimgi tarix bo‘yicha manbalar. **Izoh:** Behistun qoyalari Eronning Kirmonshoh shahri yaqinida.

**1074.** Teshiktosh g‘ori qayerda joylashgan?  
A) Boysun tog‘larida  
B) Toshkent vohasida  
C) Farg‘ona vodiysida  
D) Boshqirdistonda  
**To‘g‘ri: A.** Manba: Eng qadimgi odamlarning mashg‘ulotlari. **Izoh:** Teshiktosh g‘ori Boysun tog‘larida joylashgan.

**1075.** Qadimgi Yunonistonning eng baland tog‘i qaysi?  
A) Olimp  
B) Peloponnes  
C) Atos  
D) Parnas  
**To‘g‘ri: A.** Manba: Antik tarixning boshlanishi. **Izoh:** Yunonistonning eng baland tog‘i Olimp.

**1076.** Quyidagilardan qaysi biri Misr fir’avni emas?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Hammurapi  
**To‘g‘ri: D.** Manba: Qadimgi Misr, Bobil podsholigi. **Izoh:** Hammurapi Bobil podshosi, qolganlar Misr fir’avnlari.

**1077.** Quyidagilardan qaysi biri Mesopotamiya shahri emas?  
A) Uruk  
B) Ur  
C) Lagash  
D) Memfis  
**To‘g‘ri: D.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Memfis Misr shahri, qolganlar Mesopotamiya shaharlari.

**1078.** Quyidagilardan qaysi biri yunon faylasufi emas?  
A) Geraklit  
B) Demokrit  
C) Suqrot  
D) Gannibal  
**To‘g‘ri: D.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Gannibal Karfagen sarkardasi, qolganlar yunon faylasuflari.

**1079.** Quyidagilardan qaysi biri Rim shahri emas?  
A) Rim  
B) Pompeya  
C) Karfagen  
D) Neapol  
**To‘g‘ri: C.** Manba: Qadimgi Rim. **Izoh:** Karfagen Shimoliy Afrikadagi shahar, qolganlar Italiyadagi shaharlar.

**1080.** Quyidagilardan qaysi biri O‘rta Osiyo manzilgohi emas?  
A) Teshiktosh  
B) Selungur  
C) Ko‘lbu loq  
D) Altamir  
**To‘g‘ri: D.** Manba: Eng qadimgi odamlar. **Izoh:** Altamir Ispaniyadagi g‘or, qolganlar O‘rta Osiyo manzilgohlari.

**1081.** Misrni yagona davlatga birlashtirgan birinchi fir’avn kim?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Tutanxamon  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Menes Misrni birlashtirgan birinchi fir’avn.

**1082.** Temirdan birinchi bo‘lib foydalangan xalq qaysi?  
A) xettlar  
B) misrliklar  
C) shumerlar  
D) yunonlar  
**To‘g‘ri: A.** Manba: Temir davriga o‘tish. **Izoh:** Temirdan birinchi bo‘lib Kichik Osiyodagi xettlar foydalangan.

**1083.** Misr iyerogliflarini birinchi bo‘lib o‘qigan olim kim?  
A) Jak-Fransua Shampolyon  
B) Genrix Shlimann  
C) Gerodot  
D) Strabon  
**To‘g‘ri: A.** Manba: Qadimgi Misr madaniyati. **Izoh:** Shampolyon 1822-yilda iyerogliflarni birinchi bo‘lib o‘qigan.

**1084.** Jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan shoh kim?  
A) Sargon I  
B) Hammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**1085.** Birinchi Olimpiya o‘yinlari qachon o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**1086.** Rim imperiyasining qulashiga eng muhim sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Savdoning rivojlanishi  
C) Sharqiy Rimning kuchayishi  
D) Rimda tinchlik o‘rnatilishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**1087.** Misrning forslar tomonidan bosib olinishiga eng muhim sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Misrning zaiflashishi va fors lashkarining kuchliligi asosiy sabab.

**1088.** Ossuriyaning qulashiga eng muhim sabab nima?  
A) Bobil va Midiya tomonidan tor-mor etilishi  
B) Misr tomonidan bosib olinishi  
C) Forslar tomonidan bosib olinishi  
D) Ichki urushlar natijasida  
**To‘g‘ri: A.** Manba: Old Osiyo davlatlari. **Izoh:** Bobil va Midiya tomonidan tor-mor etilishi asosiy sabab.

**1089.** Yunon koloniyalarining tashkil topishiga eng muhim sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz va qarzdor yunonlarning vatanini tark etishi asosiy sabab.

**1090.** Buyuk Ipak yo‘lining vujudga kelishiga eng muhim sabab nima?  
A) Sharq va G‘arb o‘rtasida savdo va madaniy aloqalar zarurati  
B) Xitoy imperatorining sayohat istagi  
C) Rim imperiyasining savdo talabi  
D) O‘rta Osiyoning mustaqilligi  
**To‘g‘ri: A.** Manba: Buyuk Ipak yo‘li. **Izoh:** Savdo va madaniy aloqalar zarurati asosiy sabab.

**1091.** Nil daryosining uzunligi qancha?  
A) 4 ming km  
B) 5 ming km  
C) 6 ming km  
D) 7 ming km  
**To‘g‘ri: C.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil 6 ming kilometr uzunlikda.

**1092.** Xufu piramidasining balandligi qancha?  
A) 100 metr  
B) 147 metr  
C) 200 metr  
D) 250 metr  
**To‘g‘ri: B.** Manba: Piramidalar va daxmalar. **Izoh:** Xufu piramidasi balandligi 147 metr.

**1093.** Mumiyolash qancha kun davom etgan?  
A) 40 kun  
B) 50 kun  
C) 70 kun  
D) 90 kun  
**To‘g‘ri: C.** Manba: Piramidalar va daxmalar. **Izoh:** Mumiyolash qariyb 70 kun davom etgan.

**1094.** Misr iyerogliflarining soni nechta?  
A) 500 ta  
B) 600 ta  
C) 750 ta  
D) 1000 ta  
**To‘g‘ri: C.** Manba: Qadimgi Misr madaniyati. **Izoh:** Misr alifbosi 750 iyeroglifdan iborat bo‘lgan.

**1095.** Rim senati necha kishidan iborat bo‘lgan?  
A) 100 kishi  
B) 200 kishi  
C) 300 kishi  
D) 500 kishi  
**To‘g‘ri: C.** Manba: Rim respublikasi. **Izoh:** Rim senati 300 kishidan iborat bo‘lgan.

**1096.** Vezuviy tog‘i qaysi voqea bilan bog‘liq?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak va uning tarafdorlari Vezuviy tog‘iga chiqib olgan.

**1097.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**1098.** Zama shahri qaysi voqea bilan bog‘liq?  
A) Gannibalning tor-mor etilishi  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 202-yilda Zama yaqinida Gannibal armiyasi tor-mor etilgan.

**1099.** Kann shahri qaysi voqea bilan bog‘liq?  
A) Rim armiyasining tor-mor etilishi  
B) Karfagenning vayron etilishi  
C) Zama jangi  
D) Puni urushlari boshlanishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinida Rim armiyasi tor-mor etilgan.

**1100.** Maroqanda shahri qaysi voqea bilan bog‘liq?  
A) Aleksandr tomonidan bosib olinishi  
B) Spitamanning halok bo‘lishi  
C) Yunon-Baqtriya davlatining tashkil topishi  
D) Kushon davlatining tashkil topishi  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Mil. avv. 329-yilda Aleksandr Maroqandani bosib olgan.
"""

class Command(BaseCommand):
    help = "1001-1100 testlarni bazaga va JSON keshiga biriktirish"

    def handle(self, *args, **options):
        self.stdout.write("1001-1100 testlarni tahlil qilish va bazaga joylashtirish boshlandi...")

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\*\*\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\*\s*To‘g‘ri:\s*([A-D])\.\*\*\s*Manba:\s*(.*?)\.\s*\*\*\s*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|\n\n###|$)',
            USER_TEXT_1001_1100,
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

            c_hash = hashlib.md5(f"user_q6_v7_{num}_{q_clean}".encode('utf-8')).hexdigest()

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
