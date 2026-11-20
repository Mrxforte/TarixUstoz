import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_V3 = """
**201.** Mil. avv. 327-yilda qanday voqea yuz bergan?  
A) Aleksandr Hindistonga yurish qilgan  
B) Chandragupta Maurya davlati tashkil topgan  
C) Ashoka taxtga chiqqan  
D) Budda vafot etgan  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Mil. avv. 327-yilda Makedoniyalik Aleksandr Hindistonga yurish qilgan.

**202.** Mil. avv. 246-yilda qanday voqea yuz bergan?  
A) Sin Shixuandi Xitoyni birlashtirgan  
B) Lyu Ban qo‘zg‘oloni boshlangan  
C) Xan sulolasi qulatilgan  
D) Buyuk Ipak yo‘li vujudga kelgan  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Mil. avv. 246-yilda Sin Shixuandi Xitoyni o‘z hokimiyati ostida birlashtirgan.

**203.** Mil. avv. 206-yilda qanday voqea yuz bergan?  
A) Lyu Ban boshchiligida qo‘zg‘olon boshlangan  
B) Sin Shixuandi Xitoyni birlashtirgan  
C) Buyuk Xitoy devori qurilgan  
D) Xan sulolasi tugatilgan  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Mil. avv. 206-yilda Lyu Ban boshchiligida Sin sulolasiga qarshi qo‘zg‘olon boshlangan.

**204.** Mil. avv. 202-yilda qanday voqea yuz bergan?  
A) Gannibal Zama yaqinida tor-mor etilgan  
B) Kann jangi bo‘lib o‘tgan  
C) Karfagen vayron etilgan  
D) Puni urushlari boshlangan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 202-yilda Zama shahri yaqinida Gannibal armiyasi tor-mor etilgan.

**205.** Mil. avv. 146-yilda qanday voqea yuz bergan?  
A) Karfagen butunlay vayron etilgan  
B) Zama jangi bo‘lib o‘tgan  
C) Kann jangi bo‘lib o‘tgan  
D) Puni urushlari boshlangan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 146-yilda Karfagen butunlay vayron etilgan.

**206.** Aleksandr Hindistonga qachon yurish qilgan?  
A) mil. avv. 334-yilda  
B) mil. avv. 330-yilda  
C) mil. avv. 327-yilda  
D) mil. avv. 323-yilda  
**To‘g‘ri: C.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Aleksandr mil. avv. 327-yilda Hindistonga yurish qilgan.

**207.** Sin Shixuandi Xitoyni qachon birlashtirgan?  
A) mil. avv. 246-yilda  
B) mil. avv. 221-yilda  
C) mil. avv. 206-yilda  
D) mil. avv. 210-yilda  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Mil. avv. 246-yilda Sin Shixuandi Xitoyni birlashtirgan.

**208.** Lyu Ban qo‘zg‘oloni qachon boshlangan?  
A) mil. avv. 210-yilda  
B) mil. avv. 206-yilda  
C) mil. avv. 202-yilda  
D) mil. avv. 200-yilda  
**To‘g‘ri: B.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Lyu Ban qo‘zg‘oloni mil. avv. 206-yilda boshlangan.

**209.** Zama jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 216-yilda  
B) mil. avv. 210-yilda  
C) mil. avv. 202-yilda  
D) mil. avv. 196-yilda  
**To‘g‘ri: C.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Zama jangi mil. avv. 202-yilda bo‘lib o‘tgan.

**210.** Gannibal armiyasi Zama yaqinida qachon tor-mor etilgan?  
A) mil. avv. 206-yilda  
B) mil. avv. 202-yilda  
C) mil. avv. 198-yilda  
D) mil. avv. 194-yilda  
**To‘g‘ri: B.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Gannibal armiyasi mil. avv. 202-yilda Zama yaqinida tor-mor etilgan.

**211.** Chandragupta kim?  
A) Maurya davlatiga asos solgan hind sarkardasi  
B) Dehli sultonligini tuzgan hukmdor  
C) Budda diniga asos solgan shahzoda  
D) Xitoyni birlashtirgan imperator  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Chandragupta yunon-makedonlarga qarshi kurashib, Maurya davlatiga asos solgan.

**212.** Ashoka kim?  
A) Maurya davlatiga asos solgan sarkarda  
B) Maurya davlatini gullab-yashnagan hukmdor  
C) Budda diniga asos solgan shahzoda  
D) Xitoyni birlashtirgan imperator  
**To‘g‘ri: B.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Ashoka hukmronligi davrida Maurya davlati yuksak taraqqiyotga erishgan.

**213.** Budda kim?  
A) Maurya davlatiga asos solgan sarkarda  
B) Maurya davlatini gullab-yashnagan hukmdor  
C) Budda diniga asos solgan shahzoda Siddhartha Gautama  
D) Xitoyni birlashtirgan imperator  
**To‘g‘ri: C.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Budda diniga Siddhartha Gautama asos solgan.

**214.** Sin Shixuandi kim?  
A) Maurya davlatiga asos solgan sarkarda  
B) Budda diniga asos solgan shahzoda  
C) Xitoyni birlashtirgan imperator  
D) Xan sulolasiga asos solgan hukmdor  
**To‘g‘ri: C.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin Shixuandi Xitoyni birlashtirgan imperator.

**215.** Lyu Ban kim?  
A) Sin sulolasiga qarshi qo‘zg‘olonga boshchilik qilgan  
B) Xitoyni birlashtirgan imperator  
C) Budda diniga asos solgan shahzoda  
D) Maurya davlatiga asos solgan sarkarda  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Lyu Ban Sin sulolasiga qarshi dehqonlar qo‘zg‘oloniga boshchilik qilgan.

**216.** Maurya davlatiga asos solgan sarkarda kim?  
A) Ashoka  
B) Chandragupta  
C) Budda  
D) Sin Shixuandi  
**To‘g‘ri: B.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Chandragupta Maurya davlatiga asos solgan.

**217.** Budda diniga asos solgan shahzoda kim?  
A) Ashoka  
B) Chandragupta  
C) Siddhartha Gautama  
D) Sin Shixuandi  
**To‘g‘ri: C.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Siddhartha Gautama Budda diniga asos solgan.

**218.** Xitoyni birlashtirgan imperator kim?  
A) Lyu Ban  
B) Sin Shixuandi  
C) U-Di  
D) Chandragupta  
**To‘g‘ri: B.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin Shixuandi Xitoyni birlashtirgan.

**219.** Sin sulolasiga qarshi qo‘zg‘olonga boshchilik qilgan shaxs kim?  
A) Lyu Ban  
B) Sin Shixuandi  
C) U-Di  
D) Ashoka  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Lyu Ban Sin sulolasiga qarshi qo‘zg‘olonga boshchilik qilgan.

**220.** Xan sulolasi davrida Xitoyni eng qudratli davlatga aylantirgan hukmdor kim?  
A) Lyu Ban  
B) Sin Shixuandi  
C) U-Di  
D) Ashoka  
**To‘g‘ri: C.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** U-Di hukmronligi davrida Xan sulolasi davlati eng qudratli davlatga aylangan.

**221.** Kasta nima?  
A) Hindistondagi ijtimoiy tabaqa  
B) Xitoydagi harbiy bo‘linma  
C) Rimdagi mansab  
D) Misrdagi diniy lavozim  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Kasta (varna) — Hindistondagi ijtimoiy tabaqa.

**222.** Braxmanlar kimlar?  
A) Hindistondagi jangchilar tabaqasi  
B) Hindistondagi kohinlar tabaqasi  
C) Hindistondagi dehqonlar tabaqasi  
D) Hindistondagi xizmatkorlar tabaqasi  
**To‘g‘ri: B.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Braxmanlar — hind ruhoniylari, kohinlar tabaqasi.

**223.** Kshatriylar kimlar?  
A) Hindistondagi kohinlar tabaqasi  
B) Hindistondagi jangchilar tabaqasi  
C) Hindistondagi dehqonlar tabaqasi  
D) Hindistondagi xizmatkorlar tabaqasi  
**To‘g‘ri: B.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Kshatriylar — jangchilar tabaqasi.

**224.** Shudralar kimlar?  
A) Hindistondagi kohinlar tabaqasi  
B) Hindistondagi jangchilar tabaqasi  
C) Hindistondagi dehqonlar tabaqasi  
D) Hindistondagi xizmatkorlar va qullar tabaqasi  
**To‘g‘ri: D.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Shudralar — xizmatkorlar va qullar tabaqasi.

**225.** «Chandallar» kimlar?  
A) Hindistondagi kohinlar  
B) Hech bir tabaqaga mansub bo‘lmaganlar  
C) Hindistondagi jangchilar  
D) Hindistondagi savdogarlar  
**To‘g‘ri: B.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** «Chandallar» — hech bir tabaqaga mansub bo‘lmagan, «hazar qilinadiganlar».

**226.** Hindistondagi kohinlar tabaqasi nima deb atalgan?  
A) kshatriylar  
B) braxmanlar  
C) vayshilar  
D) shudralar  
**To‘g‘ri: B.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Kohinlar tabaqasi braxmanlar deb atalgan.

**227.** Hindistondagi jangchilar tabaqasi nima deb atalgan?  
A) kshatriylar  
B) braxmanlar  
C) vayshilar  
D) shudralar  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Jangchilar tabaqasi kshatriylar deb atalgan.

**228.** Hindistondagi dehqon, hunarmand va savdogarlar tabaqasi nima deb atalgan?  
A) kshatriylar  
B) braxmanlar  
C) vayshilar  
D) shudralar  
**To‘g‘ri: C.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Dehqon, hunarmand va savdogarlar tabaqasi vayshilar deb atalgan.

**229.** Hindistondagi xizmatkorlar va qullar tabaqasi nima deb atalgan?  
A) kshatriylar  
B) braxmanlar  
C) vayshilar  
D) shudralar  
**To‘g‘ri: D.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Xizmatkorlar va qullar tabaqasi shudralar deb atalgan.

**230.** Hech bir tabaqaga mansub bo‘lmaganlar nima deb atalgan?  
A) chandallar  
B) braxmanlar  
C) vayshilar  
D) kshatriylar  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Hech bir tabaqaga mansub bo‘lmaganlar «chandallar» deb atalgan.

**231.** Hindiston tabaqalarini ularning vazifalari bilan moslashtiring:  
1) Braxmanlar — a) jangchilar  
2) Kshatriylar — b) kohinlar  
3) Vayshilar — c) dehqon, hunarmand, savdogar  
4) Shudralar — d) xizmatkor va qullar  
A) 1-b, 2-a, 3-c, 4-d  
B) 1-a, 2-b, 3-c, 4-d  
C) 1-b, 2-c, 3-a, 4-d  
D) 1-d, 2-a, 3-c, 4-b  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Braxmanlar — kohinlar, kshatriylar — jangchilar, vayshilar — dehqon/hunarmand/savdogar, shudralar — xizmatkor/qullar.

**232.** Xitoy ixtirollarini ularning mazmuni bilan moslashtiring:  
1) Iyeroglif — a) yozuv belgisi  
2) Kompas — b) janubni ko‘rsatuvchi asbob  
3) Seysmograf — c) zilzilani aniqlovchi asbob  
4) Qog‘oz — d) yozuv materiali  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Iyeroglif — yozuv belgisi, kompas — janubni ko‘rsatuvchi, seysmograf — zilzilani aniqlovchi, qog‘oz — yozuv materiali.

**233.** Xitoy sulolalarini davrlari bilan moslashtiring:  
1) Sin — a) mil. avv. 221–206-yillar  
2) Xan — b) mil. avv. 206–milodiy 220-yillar  
3) Shan — c) mil. avv. 2-ming yillik  
4) Chjou — d) mil. avv. 11–3-asrlar  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qadimgi Xitoy sivilizatsiyalari, Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin — mil. avv. 221–206, Xan — mil. avv. 206–milodiy 220, Shan — mil. avv. 2-ming yillik, Chjou — mil. avv. 11–3-asrlar.

**234.** Hindiston davlatlarini asoschilari bilan moslashtiring:  
1) Maurya — a) Chandragupta  
2) Dehli sultonligi — b) Qutbiddin Oyboq  
3) Xiljiylar sulolasi — c) Jaloliddin Feruzshoh  
4) Sayyidlar sulolasi — d) Xizr Xon  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston, Dehli sultonligi. **Izoh:** Maurya — Chandragupta, Dehli sultonligi — Qutbiddin Oyboq, Xiljiylar — Jaloliddin Feruzshoh, Sayyidlar — Xizr Xon.

**235.** Xitoy ixtirollarini ixtirochilari bilan moslashtiring:  
1) Qog‘oz — a) Tsai Lun  
2) Kompas — b) qadimgi xitoyliklar  
3) Seysmograf — c) Chjan Xen  
4) Ipak — d) qadimgi xitoyliklar  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Qog‘oz — Tsai Lun, kompas va ipak — qadimgi xitoyliklar, seysmograf — Chjan Xen.

**236.** Quyidagi Hindiston voqealarini xronologik tartibga soling:  
1) Maurya davlati tashkil topgan  
2) Ashoka hukmronligi  
3) Budda diniga asos solingan  
4) Aleksandr Hindistonga yurish qilgan  
A) 3-4-1-2  
B) 4-3-1-2  
C) 3-1-4-2  
D) 1-3-4-2  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston. **Izoh:** Budda (mil. avv. VI) → Aleksandr (327) → Maurya (321) → Ashoka (mil. avv. III asr).

**237.** Quyidagi Xitoy voqealarini xronologik tartibga soling:  
1) Sin Shixuandi Xitoyni birlashtirgan  
2) Lyu Ban qo‘zg‘oloni  
3) Xan sulolasi tashkil topgan  
4) «Sariq ro‘mollilar» qo‘zg‘oloni  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin Shixuandi (246) → Lyu Ban (206) → Xan sulolasi → «Sariq ro‘mollilar» (milodiy II asr).

**238.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Puni urushlari boshlanishi  
2) Kann jangi  
3) Zama jangi  
4) Karfagenning vayron etilishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Puni urushlari → Kann (216) → Zama (202) → Karfagen vayron etilishi (146).

**239.** Quyidagi O‘rta Osiyo voqealarini xronologik tartibga soling:  
1) Kir II massagetlar ustiga yurishi  
2) Aleksandr Maroqandani bosib olishi  
3) Spitaman qo‘zg‘oloni  
4) Yunon-Baqtriya davlatining tashkil topishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Kir II (530) → Maroqanda (329) → Spitaman (328) → Yunon-Baqtriya (250).

**240.** Quyidagi Kushon davlati voqealarini xronologik tartibga soling:  
1) Yuechjilar Yunon-Baqtriyani bosib olgan  
2) Kushon davlati tashkil topgan  
3) Kudzula Kadfiz hukmronligi  
4) Kanishka hukmronligi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** Yuechjilar (140–130) → Kushon davlati (mil. I asr) → Kudzula Kadfiz → Kanishka.

**241.** Quyidagi gap to‘g‘rimi? «Chandragupta Maurya davlatiga asos solgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki Ashoka asos solgan  
C) Noto‘g‘ri, chunki Budda asos solgan  
D) Noto‘g‘ri, chunki Maurya davlati bo‘lmagan  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Chandragupta Maurya davlatiga asos solgan.

**242.** Quyidagi gap to‘g‘rimi? «Sin Shixuandi mil. avv. 206-yilda Xitoyni birlashtirgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 246-yilda birlashtirgan  
C) Noto‘g‘ri, chunki 221-yilda birlashtirgan  
D) Noto‘g‘ri, chunki Xitoyni Lyu Ban birlashtirgan  
**To‘g‘ri: B.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin Shixuandi mil. avv. 246-yilda Xitoyni birlashtirgan.

**243.** Quyidagi gap to‘g‘rimi? «Budda diniga Siddhartha Gautama asos solgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki Ashoka asos solgan  
C) Noto‘g‘ri, chunki Chandragupta asos solgan  
D) Noto‘g‘ri, chunki Budda dini bo‘lmagan  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Budda diniga Siddhartha Gautama asos solgan.

**244.** Quyidagi gap to‘g‘rimi? «Gannibal mil. avv. 216-yilda Zama yaqinida tor-mor etilgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 202-yilda tor-mor etilgan  
C) Noto‘g‘ri, chunki 146-yilda tor-mor etilgan  
D) Noto‘g‘ri, chunki Gannibal tor-mor etilmagan  
**To‘g‘ri: B.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Gannibal mil. avv. 202-yilda Zama yaqinida tor-mor etilgan.

**245.** Quyidagi gap to‘g‘rimi? «Kushon davlati milodiy I asrda tashkil topgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki mil. avv. I asrda tashkil topgan  
C) Noto‘g‘ri, chunki milodiy II asrda tashkil topgan  
D) Noto‘g‘ri, chunki Kushon davlati bo‘lmagan  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** Kushon davlati milodiy I asrda tashkil topgan.

**246.** Nega Aleksandr Hindistondan qaytishga majbur bo‘lgan?  
A) Mahalliy aholining qattiq qarshiligi va qo‘shinning charchashi  
B) Hindistonning iqlimi sovuq bo‘lgan  
C) Hindistonda oziq-ovqat bo‘lmagan  
D) Fors qo‘shini hujum qilgan  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Panjob aholisining qattiq qarshiligi va qo‘shinning charchashi Aleksandrni qaytishga majbur qilgan.

**247.** Nega Maurya davlati zaiflashgan?  
A) Ichki nizolar va hukmdorlarning kuchsizligi  
B) Yunon-makedonlar hujumi  
C) Budda dinining tarqalishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Ichki nizolar va kuchsiz hukmdorlar Maurya davlatining zaiflashishiga olib kelgan.

**248.** Nega Sin sulolasi qulagan?  
A) Dehqonlar qo‘zg‘oloni va ichki zulm  
B) Forslar hujumi  
C) Buyuk Ipak yo‘lining yopilishi  
D) Xan sulolasining kuchayishi  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Dehqonlar qo‘zg‘oloni va ichki zulm Sin sulolasining qulashiga olib kelgan.

**249.** Nega Rimda qullar soni ko‘paygan?  
A) Zafarli yurishlar natijasida asirlar ko‘paygan  
B) Qullar tug‘ilish hisobiga ko‘paygan  
C) Savdo orqali qullar keltirilgan  
D) Qullar ozod qilingan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Zafarli yurishlar natijasida Rimda qullar soni keskin ko‘paygan.

**250.** Nega Rimda kolonlar paydo bo‘lgan?  
A) Qullar mehnati samarasiz bo‘lib, yer ijarachilari kerak bo‘lgan  
B) Qullar qochib ketgan  
C) Yerlar bo‘sh qolgan  
D) Savdo rivojlangan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Qullar mehnati samarasiz bo‘lganligi sababli yerlar kolonlarga ijaraga berilgan.

**251.** Maurya davlatining gullab-yashnashiga sabab nima?  
A) Ashoka hukmronligi  
B) Chandragupta hukmronligi  
C) Budda dinining tarqalishi  
D) Yunon-makedonlar hujumi  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Ashoka hukmronligi davrida Maurya davlati yuksak taraqqiyotga erishgan.

**252.** Sin sulolasining qulashiga sabab nima?  
A) Dehqonlar qo‘zg‘oloni  
B) Forslar hujumi  
C) Xan sulolasining kuchayishi  
D) Budda dinining tarqalishi  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Dehqonlar qo‘zg‘oloni Sin sulolasining qulashiga olib kelgan.

**253.** Xitoyda «Sariq ro‘mollilar» qo‘zg‘oloni boshlanishiga sabab nima?  
A) Og‘ir soliqlar va zulm  
B) Forslar hujumi  
C) Budda dinining tarqalishi  
D) Savdoning kamayishi  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Og‘ir soliqlar va zulm xalq noroziligiga sabab bo‘lgan.

**254.** Karfagenning vayron etilishiga sabab nima?  
A) Uchinchi Puni urushi va Rimning g‘alabasi  
B) Birinchi Puni urushi  
C) Ikkinchi Puni urushi  
D) Gannibalning taslim bo‘lishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Uchinchi Puni urushi natijasida mil. avv. 146-yilda Karfagen vayron etilgan.

**255.** Rim respublikasining qulashiga sabab nima?  
A) Ichki ziddiyatlar va harbiy diktaturaning kuchayishi  
B) Karfagenning kuchayishi  
C) Yunonistonning hujumi  
D) Savdoning kamayishi  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ichki ziddiyatlar va harbiy diktaturaning kuchayishi respublikaning qulashiga olib kelgan.

**256.** Hindiston va Xitoy sivilizatsiyalarining o‘xshash jihati nima?  
A) Ikkalasi ham daryo vodiylarida vujudga kelgan  
B) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
C) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston, Qadimgi Xitoy. **Izoh:** Hindiston Hind va Gang vodiysida, Xitoy Xuanxe va Yantszi vodiysida vujudga kelgan.

**257.** Maurya va Sin davlatlarining o‘xshash jihati nima?  
A) Ikkalasi ham kuchli markazlashgan davlat bo‘lgan  
B) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
C) Ikkalasi ham bir vaqtda tashkil topgan  
D) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston, Qadimgi Xitoy. **Izoh:** Ikkalasi ham kuchli markazlashgan davlat bo‘lgan.

**258.** Braxmanlar va kshatriylarning o‘xshash jihati nima?  
A) Ikkalasi ham yuqori tabaqaga mansub bo‘lgan  
B) Ikkalasi ham dehqonchilik bilan shug‘ullangan  
C) Ikkalasi ham xizmatkor bo‘lgan  
D) Ikkalasi ham savdo qilgan  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Braxmanlar va kshatriylar yuqori tabaqalarga mansub bo‘lgan.

**259.** Rim va Karfagenning o‘xshash jihati nima?  
A) Ikkalasi ham O‘rtayer dengizida hukmronlik uchun kurashgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim va Karfagen O‘rtayer dengizida hukmronlik uchun kurashgan.

**260.** Kushon va Yunon-Baqtriya davlatlarining o‘xshash jihati nima?  
A) Ikkalasi ham O‘rta Osiyo hududida tashkil topgan  
B) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
C) Ikkalasi ham bir vaqtda tashkil topgan  
D) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Ikkalasi ham O‘rta Osiyo hududida tashkil topgan.

**261.** Hindiston va Xitoy yozuvlarining farqi nimada?  
A) Hindistonda sanskrit, Xitoyda iyeroglif ishlatilgan  
B) Hindistonda iyeroglif, Xitoyda sanskrit ishlatilgan  
C) Ikkalasi ham bir xil yozuv ishlatgan  
D) Hindistonda yozuv bo‘lmagan  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston, Qadimgi Xitoy. **Izoh:** Hindistonda sanskrit tili va yozuvi, Xitoyda iyeroglif yozuvi ishlatilgan.

**262.** Maurya va Sin davlatlarining farqi nimada?  
A) Maurya Hindistonda, Sin Xitoyda tashkil topgan  
B) Maurya Xitoyda, Sin Hindistonda tashkil topgan  
C) Ikkalasi ham bir xil hududda tashkil topgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston, Qadimgi Xitoy. **Izoh:** Maurya Hindistonda, Sin Xitoyda tashkil topgan.

**263.** Braxmanlar va shudralarning farqi nimada?  
A) Braxmanlar kohinlar, shudralar xizmatkorlar  
B) Braxmanlar xizmatkorlar, shudralar kohinlar  
C) Ikkalasi ham bir xil tabaqa  
D) Ikkalasi ham jangchilar  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Braxmanlar kohinlar, shudralar xizmatkorlar va qullar tabaqasi.

**264.** Rim va Yunoniston madaniyatlarining farqi nimada?  
A) Rim me’morchiligida beton va gumbaz ishlatilgan, Yunonistonda ustunlar  
B) Yunonistonda beton, Rimda ustunlar ishlatilgan  
C) Ikkalasi ham bir xil me’morchilikka ega  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Rim me’morchiligida beton va gumbaz, Yunonistonda ustunlar keng ishlatilgan.

**265.** Kushon va Yunon-Baqtriya davlatlarining farqi nimada?  
A) Yunon-Baqtriya ellinistik madaniyatga, Kushon buddaviylikka asoslangan  
B) Yunon-Baqtriya buddaviylikka, Kushon ellinistik madaniyatga asoslangan  
C) Ikkalasi ham bir xil madaniyatga ega  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Yunon-Baqtriya ellinistik madaniyatga, Kushon davlati buddaviylik diniga asoslangan.

**266.** «U o‘z xalqini dehqonchilikka o‘rgatgan.» Bu qaysi xudo haqida?  
A) Osiris  
B) Amon-Ra  
C) Ptax  
D) Tot  
**To‘g‘ri: A.** Manba: Qadimgi Misrda din. **Izoh:** Rivoyatga ko‘ra, Osiris misrliklarni dehqonchilikka o‘rgatgan.

**267.** «Uning jasadi qabrga ko‘milgan, atrofiga tog‘ echkisi shoxlari qadab chiqilgan.» Bu qaysi topilma haqida?  
A) Teshiktosh neandertal bolasi  
B) Xufu piramidasi  
C) Tutanxamon maqbarasi  
D) Zamonbobo manzilgohi  
**To‘g‘ri: A.** Manba: Eng qadimgi odamlarning mashg‘ulotlari. **Izoh:** Teshiktosh g‘orida 8–9 yashar neandertal bolaning jasadi shu tarzda dafn etilgan.

**268.** «U 20 yoshga to‘lguniga qadar hukmdor otasi saroyida betashvish kun kechirgan.» Bu kim haqida?  
A) Siddhartha Gautama  
B) Zardusht  
C) Chandragupta  
D) Ashoka  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Siddhartha Gautama 20 yoshga to‘lguniga qadar saroyda yashagan.

**269.** «U jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan.» Bu kim haqida?  
A) Sargon I  
B) Xammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**270.** «Ular uzum novdalaridan narvon to‘qib, ko‘rinmaydigan tarafdan cho‘qqidan pastga tushib oldilar.» Bu qaysi voqea haqida?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘oloni davrida qullar Vezuviy tog‘idan uzum novdalaridan narvon to‘qib tushgan.

**271.** Hind daryosi qaysi dengizga quyiladi?  
A) Arabiston dengiziga  
B) Bengal ko‘rfaziga  
C) O‘rtayer dengiziga  
D) Qizil dengizga  
**To‘g‘ri: A.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Hind daryosi Arabiston dengiziga quyiladi.

**272.** Gang daryosi qaysi ko‘rfazga quyiladi?  
A) Arabiston dengiziga  
B) Bengaliya ko‘rfaziga  
C) O‘rtayer dengiziga  
D) Qizil dengizga  
**To‘g‘ri: B.** Manba: Qadimgi Hindiston sivilizatsiyasi. **Izoh:** Gang daryosi Bengaliya ko‘rfaziga quyiladi.

**273.** Xuanxe va Yantszi daryolari qaysi mamlakatda?  
A) Hindiston  
B) Xitoy  
C) Misr  
D) Mesopotamiya  
**To‘g‘ri: B.** Manba: Qadimgi Xitoy sivilizatsiyalari. **Izoh:** Xuanxe va Yantszi daryolari Xitoyda.

**274.** Maroqanda shahri hozirgi qaysi shahar o‘rnida?  
A) Buxoro  
B) Samarqand  
C) Toshkent  
D) Termiz  
**To‘g‘ri: B.** Manba: O‘rta Osiyo tarixi. **Izoh:** Maroqanda hozirgi Samarqand shahri o‘rnida joylashgan.

**275.** Baqtra shahri qaysi davlatning poytaxti bo‘lgan?  
A) Yunon-Baqtriya  
B) Kushon  
C) Xorazm  
D) Qang‘  
**To‘g‘ri: A.** Manba: Salavkiylar davlati. **Izoh:** Baqtra Yunon-Baqtriya davlatining poytaxti bo‘lgan.

**276.** Quyidagilardan qaysi biri Hindiston tabaqasi emas?  
A) braxmanlar  
B) kshatriylar  
C) vayshilar  
D) patritsiylar  
**To‘g‘ri: D.** Manba: Qadimgi Hindiston, Rim. **Izoh:** Patritsiylar Rimning tub aholisi, qolganlar Hindiston tabaqalari.

**277.** Quyidagilardan qaysi biri Xitoy ixtirosi emas?  
A) qog‘oz  
B) kompas  
C) seysmograf  
D) iyeroglif  
**To‘g‘ri: D.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Iyeroglif yozuvi Misrda ham bo‘lgan, lekin Xitoyda ham ishlatilgan.

**278.** Quyidagilardan qaysi biri Kushon hukmdori emas?  
A) Kudzula Kadfiz  
B) Vima Kadfiz  
C) Kanishka  
D) Chandragupta  
**To‘g‘ri: D.** Manba: Kushon davlati, Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Chandragupta Maurya davlatiga asos solgan.

**279.** Quyidagilardan qaysi biri O‘rta Osiyo shahri emas?  
A) Maroqanda  
B) Baqtra  
C) Nautaka  
D) Memfis  
**To‘g‘ri: D.** Manba: O‘rta Osiyo tarixi. **Izoh:** Memfis Misr shahri, qolganlar O‘rta Osiyo shaharlari.

**280.** Quyidagilardan qaysi biri Rim imperatori emas?  
A) Oktavian Avgust  
B) Trayan  
C) Neron  
D) Perikl  
**To‘g‘ri: D.** Manba: Rim imperiyasining qulashi, Afinada demokratiya. **Izoh:** Perikl Afina strategi, qolganlar Rim imperatorlari.

**281.** Maurya davlatiga birinchi bo‘lib asos solgan kim?  
A) Ashoka  
B) Chandragupta  
C) Budda  
D) Sin Shixuandi  
**To‘g‘ri: B.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Chandragupta Maurya davlatiga asos solgan.

**282.** Budda diniga birinchi bo‘lib asos solgan kim?  
A) Ashoka  
B) Chandragupta  
C) Siddhartha Gautama  
D) Sin Shixuandi  
**To‘g‘ri: C.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Siddhartha Gautama Budda diniga asos solgan.

**283.** Xitoyni birinchi bo‘lib birlashtirgan imperator kim?  
A) Lyu Ban  
B) Sin Shixuandi  
C) U-Di  
D) Chandragupta  
**To‘g‘ri: B.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Sin Shixuandi Xitoyni birinchi bo‘lib birlashtirgan.

**284.** Karfagen bilan birinchi Puni urushini boshlagan davlat qaysi?  
A) Rim  
B) Yunoniston  
C) Misr  
D) Makedoniya  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim Karfagen bilan birinchi Puni urushini boshlagan.

**285.** Birinchi Olimpiya o‘yinlari qachon o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**286.** Maurya davlatining gullab-yashnashiga eng muhim sabab nima?  
A) Ashoka hukmronligi  
B) Chandragupta hukmronligi  
C) Budda dinining tarqalishi  
D) Yunon-makedonlar hujumi  
**To‘g‘ri: A.** Manba: Miloddan avvalgi I ming yillikda Hindiston. **Izoh:** Ashoka hukmronligi Maurya davlatining gullab-yashnashiga asosiy sabab bo‘lgan.

**287.** Sin sulolasining qulashiga eng muhim sabab nima?  
A) Dehqonlar qo‘zg‘oloni  
B) Forslar hujumi  
C) Xan sulolasining kuchayishi  
D) Budda dinining tarqalishi  
**To‘g‘ri: A.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Dehqonlar qo‘zg‘oloni Sin sulolasining qulashiga asosiy sabab bo‘lgan.

**288.** Karfagenning vayron etilishiga eng muhim sabab nima?  
A) Uchinchi Puni urushi va Rimning g‘alabasi  
B) Birinchi Puni urushi  
C) Ikkinchi Puni urushi  
D) Gannibalning taslim bo‘lishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Uchinchi Puni urushi Karfagenning vayron etilishiga asosiy sabab bo‘lgan.

**289.** Rim respublikasining qulashiga eng muhim sabab nima?  
A) Ichki ziddiyatlar va harbiy diktaturaning kuchayishi  
B) Karfagenning kuchayishi  
C) Yunonistonning hujumi  
D) Savdoning kamayishi  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ichki ziddiyatlar va harbiy diktaturaning kuchayishi asosiy sabab bo‘lgan.

**290.** Kushon davlatining inqiroziga eng muhim sabab nima?  
A) To‘xtovsiz urushlar  
B) Savdoning rivojlanishi  
C) Buddaviylikning tarqalishi  
D) Xitoyning kuchayishi  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** To‘xtovsiz urushlar Kushon davlatining inqiroziga olib kelgan.

**291.** Misr alifbosidagi iyerogliflar soni nechta?  
A) 500 ta  
B) 600 ta  
C) 750 ta  
D) 1000 ta  
**To‘g‘ri: C.** Manba: Qadimgi Misr madaniyati. **Izoh:** Misr alifbosi 750 iyeroglifdan iborat bo‘lgan.

**292.** Rim senati necha kishidan iborat bo‘lgan?  
A) 100 kishi  
B) 200 kishi  
C) 300 kishi  
D) 500 kishi  
**To‘g‘ri: C.** Manba: Rim respublikasi. **Izoh:** Rim senati 300 kishidan iborat bo‘lgan.

**293.** Buyuk Xitoy devorining uzunligi qancha?  
A) 2000 km  
B) 3000 km  
C) 4000 km  
D) 5000 km  
**To‘g‘ri: C.** Manba: Mil. avv. III–milodiy II asrlarda Xitoy. **Izoh:** Buyuk Xitoy devorining uzunligi qariyb 4000 kilometr.

**294.** Buyuk Ipak yo‘lining uzunligi qancha?  
A) 6 ming km  
B) 8 ming km  
C) 10 ming km  
D) 12 ming km  
**To‘g‘ri: D.** Manba: Buyuk Ipak yo‘li. **Izoh:** Buyuk Ipak yo‘li 12 ming kilometrgacha uzunlikda bo‘lgan.

**295.** Nil daryosining uzunligi qancha?  
A) 4 ming km  
B) 5 ming km  
C) 6 ming km  
D) 7 ming km  
**To‘g‘ri: C.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil 6 ming kilometr uzunlikda.

**296.** Vezuviy tog‘i qaysi voqea bilan bog‘liq?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak va uning tarafdorlari Vezuviy tog‘iga chiqib olgan.

**297.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**298.** Zama shahri qaysi voqea bilan bog‘liq?  
A) Gannibalning tor-mor etilishi  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 202-yilda Zama yaqinida Gannibal armiyasi tor-mor etilgan.

**299.** Kann shahri qaysi voqea bilan bog‘liq?  
A) Rim armiyasining tor-mor etilishi  
B) Karfagenning vayron etilishi  
C) Zama jangi  
D) Puni urushlari boshlanishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinida Rim armiyasi tor-mor etilgan.

**300.** Maroqanda shahri qaysi voqea bilan bog‘liq?  
A) Aleksandr tomonidan bosib olinishi  
B) Spitamanning halok bo‘lishi  
C) Yunon-Baqtriya davlatining tashkil topishi  
D) Kushon davlatining tashkil topishi  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Mil. avv. 329-yilda Aleksandr Maroqandani bosib olgan.
"""

class Command(BaseCommand):
    help = "201-300 testlarni izoh va manbalari bilan bazaga qo'shish"

    def handle(self, *args, **options):
        self.stdout.write("201-300 testlarni tahlil qilish va bazaga joylashtirish boshlandi...")

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\*\*\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\*\s*To‘g‘ri:\s*([A-D])\.\*\*\s*Manba:\s*(.*?)\.\s*\*\*\s*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|$)',
            USER_TEXT_V3,
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

            c_hash = hashlib.md5(f"user_q6_v3_{num}_{q_clean}".encode('utf-8')).hexdigest()

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

        self.stdout.write(self.style.SUCCESS(f"\nMuvaffaqiyatli saqlandi! {created_count} ta yangi testlar bazaga qo'shildi."))
