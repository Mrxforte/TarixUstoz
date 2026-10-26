import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_201_300 = """
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

USER_TEXT_301_400 = """
**301.** Mil. avv. 509-yilda qanday voqea yuz bergan?  
A) Rimda respublika e’lon qilingan  
B) Rim shahriga asos solingan  
C) Karfagen vayron etilgan  
D) Puni urushlari boshlangan  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Mil. avv. 509-yilda Mag‘rur Tarkviniy quvg‘in qilinib, Rimda respublika boshqaruvi e’lon qilingan.

**302.** Mil. avv. 264-yilda qanday voqea yuz bergan?  
A) Birinchi Puni urushi boshlangan  
B) Ikkinchi Puni urushi boshlangan  
C) Karfagen vayron etilgan  
D) Zama jangi bo‘lib o‘tgan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 264-yilda Rim va Karfagen o‘rtasida birinchi Puni urushi boshlangan.

**303.** Mil. avv. 216-yilda qanday voqea yuz bergan?  
A) Kann jangida Rim armiyasi tor-mor etilgan  
B) Zama jangida Gannibal tor-mor etilgan  
C) Karfagen vayron etilgan  
D) Birinchi Puni urushi boshlangan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinidagi jangda Gannibal Rim armiyasini tor-mor etgan.

**304.** Mil. avv. 49-yilda qanday voqea yuz bergan?  
A) Yuliy Sezar Rubikon daryosidan o‘tib, hokimiyat uchun kurash boshlagan  
B) Oktavian Avgust imperator bo‘lgan  
C) Spartak qo‘zg‘oloni boshlangan  
D) Sezar o‘ldirilgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Mil. avv. 49-yilda Sezar Rubikon daryosidan o‘tib, yakka hokimlik uchun ochiq kurash boshlagan.

**305.** Mil. avv. 44-yilda qanday voqea yuz bergan?  
A) Yuliy Sezar senatorlar fitnasi natijasida o‘ldirilgan  
B) Oktavian Avgust imperator bo‘lgan  
C) Spartak qo‘zg‘oloni boshlangan  
D) Sezar Rubikon daryosidan o‘tgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Mil. avv. 44-yilda Brut boshchiligidagi senatorlar fitnasi natijasida Yuliy Sezar o‘ldirilgan.

**306.** Rimda respublika qachon e’lon qilingan?  
A) mil. avv. 509-yilda  
B) mil. avv. 510-yilda  
C) mil. avv. 508-yilda  
D) mil. avv. 507-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Mil. avv. 509-yilda Rimda respublika e’lon qilingan.

**307.** Birinchi Puni urushi qachon boshlangan?  
A) mil. avv. 264-yilda  
B) mil. avv. 260-yilda  
C) mil. avv. 256-yilda  
D) mil. avv. 250-yilda  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Birinchi Puni urushi mil. avv. 264-yilda boshlangan.

**308.** Kann jangi qachon bo‘lib o‘tgan?  
A) mil. avv. 216-yilda  
B) mil. avv. 212-yilda  
C) mil. avv. 208-yilda  
D) mil. avv. 204-yilda  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Kann jangi mil. avv. 216-yilda bo‘lib o‘tgan.

**309.** Yuliy Sezar qachon o‘ldirilgan?  
A) mil. avv. 44-yilda  
B) mil. avv. 45-yilda  
C) mil. avv. 43-yilda  
D) mil. avv. 42-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar mil. avv. 44-yilda senatorlar fitnasi natijasida o‘ldirilgan.

**310.** Oktavian Avgust qachon imperator unvonini olgan?  
A) mil. avv. 29-yilda  
B) mil. avv. 31-yilda  
C) mil. avv. 27-yilda  
D) mil. avv. 25-yilda  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Mil. avv. 29-yilda Oktavian Senatdan imperator unvonini va Avgust laqabini olgan.

**311.** Yuliy Sezar kim?  
A) Rim imperatori va diktatori bo‘lgan sarkarda  
B) Karfagen sarkardasi  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar mil. avv. 49-yilda hokimiyatni egallab, Rim imperatori va diktatori bo‘lgan.

**312.** Oktavian Avgust kim?  
A) Rim imperiyasining asoschisi  
B) Karfagen sarkardasi  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Oktavian Avgust Rim imperiyasining asoschisi bo‘lib, uning davrida Rimda ko‘plab saroylar qurilgan.

**313.** Spartak kim?  
A) Gladiatorlar maktabidan qochgan frakiyalik qul  
B) Rim imperatori  
C) Karfagen sarkardasi  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak Kapuya gladiatorlik maktabidan qochib, qullar qo‘zg‘oloniga boshchilik qilgan.

**314.** Gannibal kim?  
A) Karfagen sarkardasi  
B) Rim imperatori  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Gannibal Karfagen armiyasiga boshchilik qilgan va Rimga qarshi kurashgan.

**315.** Trayan kim?  
A) Rim imperatori, Trayan ustuni qurilgan hukmdor  
B) Karfagen sarkardasi  
C) Spartak qo‘zg‘oloni rahbari  
D) Rim senatori  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Trayan milodiy 98-yilda Rim imperatori bo‘lgan va uning nomi bilan Trayan ustuni qurilgan.

**316.** Rim imperiyasining asoschisi kim?  
A) Yuliy Sezar  
B) Oktavian Avgust  
C) Spartak  
D) Gannibal  
**To‘g‘ri: B.** Manba: Rim respublikasining qulashi. **Izoh:** Oktavian Avgust Rim imperiyasining asoschisi hisoblanadi.

**317.** Kapuya gladiatorlik maktabidan qochib, qullar qo‘zg‘oloniga boshchilik qilgan kim?  
A) Yuliy Sezar  
B) Oktavian Avgust  
C) Spartak  
D) Gannibal  
**To‘g‘ri: C.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak Kapuya maktabidan qochib, qullar qo‘zg‘oloniga boshchilik qilgan.

**318.** Karfagen armiyasiga boshchilik qilib, Rimga qarshi kurashgan sarkarda kim?  
A) Yuliy Sezar  
B) Oktavian Avgust  
C) Spartak  
D) Gannibal  
**To‘g‘ri: D.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Gannibal Karfagen armiyasiga boshchilik qilib, Rimga qarshi kurashgan.

**319.** Rimda Trayan ustuni qaysi imperator sharafiga qurilgan?  
A) Yuliy Sezar  
B) Oktavian Avgust  
C) Trayan  
D) Neron  
**To‘g‘ri: C.** Manba: Rim imperiyasining qulashi. **Izoh:** Trayan ustuni imperator Trayan sharafiga qurilgan.

**320.** Rimda xristianlikni teng din deb e’lon qilgan imperator kim?  
A) Konstantin  
B) Oktavian Avgust  
C) Trayan  
D) Neron  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati. **Izoh:** Milodiy 313-yilda imperator Konstantin xristianlikni boshqa dinlarga teng deb e’lon qilgan.

**321.** Gladiator nima?  
A) Rimda tomosha uchun jang qilgan qul yoki jinoyatchi  
B) Rimda harbiy qo‘mondon  
C) Rimda senator  
D) Rimda soliq yig‘uvchi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Gladiatorlar maxsus maktablarda ta’lim olib, tomoshabinlar uchun jang qilgan qullar yoki jinoyatchilar.

**322.** Kolon nima?  
A) Rimda mayda yer ijarachisi  
B) Rimda harbiy qo‘mondon  
C) Rimda senator  
D) Rimda soliq yig‘uvchi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Kolonlar — yerdan foydalanganlik uchun yer egasiga mahsulot bilan to‘lov bergan mayda yer ijarachilari.

**323.** «Kulbali qullar» kimlar?  
A) Quldorlar tomonidan kichik yer va mehnat qurollari bilan ta’minlangan qullar  
B) Rimda harbiy qo‘mondonlar  
C) Rimda senatorlar  
D) Rimda soliq yig‘uvchilar  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** «Kulbali qullar» — quldorlar tomonidan kichik yer, mehnat qurollari va urug‘ bilan ta’minlangan, oila qurishga ruxsat berilgan qullar.

**324.** Triumf nima?  
A) Rimda g‘alaba sharafiga o‘tkazilgan tantana  
B) Rimda sud jarayoni  
C) Rimda saylov marosimi  
D) Rimda diniy bayram  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Triumf — Rimda g‘alaba sharafiga o‘tkazilgan tantana bo‘lib, unda g‘olib sarkarda lavr gulchambari taqib, aravada shaharga kirgan.

**325.** Provinsiya nima?  
A) Rim tomonidan bosib olingan hudud  
B) Rimda harbiy qo‘mondon  
C) Rimda senator  
D) Rimda soliq yig‘uvchi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Provinsiya — Rim tomonidan bosib olingan va prokonsullar tomonidan boshqarilgan hudud.

**326.** Rimda tomosha uchun jang qilgan qul yoki jinoyatchi nima deb atalgan?  
A) gladiator  
B) kolon  
C) senator  
D) konsul  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Bunday shaxslar gladiatorlar deb atalgan.

**327.** Rimda mayda yer ijarachisi nima deb atalgan?  
A) gladiator  
B) kolon  
C) senator  
D) konsul  
**To‘g‘ri: B.** Manba: Qullar va gladiatorlar. **Izoh:** Mayda yer ijarachilari kolonlar deb atalgan.

**328.** Rimda g‘alaba sharafiga o‘tkazilgan tantana nima deb atalgan?  
A) triumf  
B) forum  
C) senat  
D) konsul  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** G‘alaba tantanasi triumf deb atalgan.

**329.** Rim tomonidan bosib olingan hudud nima deb atalgan?  
A) provinsiya  
B) koloniya  
C) polis  
D) nom  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Bosib olingan hududlar provinsiyalar deb atalgan.

**330.** Rimda xalq tribunining taqiqlash huquqi nima deb atalgan?  
A) veto  
B) triumf  
C) forum  
D) senat  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Xalq tribunining taqiqlash huquqi veto deb atalgan.

**331.** Rim lavozimlarini vazifalari bilan moslashtiring:  
1) Konsul — a) oliy mansabdor shaxs  
2) Senat — b) oqsoqollar kengashi  
3) Xalq tribuni — c) qashshoqlar himoyachisi  
4) Diktator — d) urush davrida cheklanmagan hokimiyat  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Konsul — oliy mansabdor, Senat — oqsoqollar, Xalq tribuni — qashshoqlar himoyachisi, Diktator — urush davri hukmdori.

**332.** Rim imperatorlarini ularning faoliyatlari bilan moslashtiring:  
1) Oktavian Avgust — a) imperiya asoschisi  
2) Trayan — b) Trayan ustuni qurilgan  
3) Konstantin — c) xristianlikni teng din qilgan  
4) Neron — d) inqiroz davri imperatori  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi, Qadimgi Rim madaniyati. **Izoh:** Oktavian — asoschi, Trayan — ustun, Konstantin — xristianlik, Neron — inqiroz.

**333.** Rim voqealari va sanalarini moslashtiring:  
1) Respublika e’lon qilingani — a) mil. avv. 509-yil  
2) Kann jangi — b) mil. avv. 216-yil  
3) Zama jangi — c) mil. avv. 202-yil  
4) Karfagenning vayron etilishi — d) mil. avv. 146-yil  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Respublika (509), Kann (216), Zama (202), Karfagen (146).

**334.** Rim shaxslarini voqealari bilan moslashtiring:  
1) Spartak — a) qullar qo‘zg‘oloni  
2) Gannibal — b) Kann jangi  
3) Sezar — c) Rubikon daryosidan o‘tish  
4) Oktavian — d) Aktium jangi  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Spartak — qo‘zg‘olon, Gannibal — Kann, Sezar — Rubikon, Oktavian — Aktium.

**335.** Rim atamalarini ta’riflari bilan moslashtiring:  
1) Gladiator — a) tomosha uchun jang qiluvchi  
2) Kolon — b) mayda yer ijarachisi  
3) Triumf — c) g‘alaba tantanasi  
4) Provinsiya — d) bosib olingan hudud  
A) 1-a, 2-b, 3-c, 4-d  
B) 1-b, 2-a, 3-c, 4-d  
C) 1-a, 2-c, 3-b, 4-d  
D) 1-d, 2-b, 3-c, 4-a  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar, Rim tarixi. **Izoh:** Gladiator — jangchi, Kolon — ijarachi, Triumf — tantana, Provinsiya — hudud.

**336.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Rimda respublika e’lon qilingan  
2) Birinchi Puni urushi boshlangan  
3) Kann jangi bo‘lib o‘tgan  
4) Karfagen vayron etilgan  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Respublika (509) → Birinchi Puni (264) → Kann (216) → Karfagen (146).

**337.** Quyidagi Rim voqealarini xronologik tartibga soling:  
1) Spartak qo‘zg‘oloni  
2) Sezar diktaturasi  
3) Oktavian Avgust hukmronligi  
4) G‘arbiy Rim imperiyasining qulashi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Spartak (74–71) → Sezar (49) → Oktavian (29) → G‘arbiy Rim qulashi (476).

**338.** Quyidagi Rim imperatorlarini xronologik tartibga soling:  
1) Oktavian Avgust  
2) Trayan  
3) Konstantin  
4) Odoakr  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 1-2-4-3  
**To‘g‘ri: A.** Manba: Rim tarixi. **Izoh:** Oktavian (mil. avv. 29) → Trayan (milodiy 98) → Konstantin (milodiy 313) → Odoakr (milodiy 476).

**339.** Quyidagi O‘rta Osiyo voqealarini xronologik tartibga soling:  
1) Kir II massagetlar ustiga yurishi  
2) Aleksandr Maroqandani bosib olishi  
3) Spitaman qo‘zg‘oloni  
4) Yunon-Baqtriya davlatining tashkil topishi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Kir II (530) → Maroqanda (329) → Spitaman (328) → Yunon-Baqtriya (250).

**340.** Quyidagi Kushon davlati voqealarini xronologik tartibga soling:  
1) Yuechjilar Yunon-Baqtriyani bosib olgan  
2) Kushon davlati tashkil topgan  
3) Kudzula Kadfiz hukmronligi  
4) Kanishka hukmronligi  
A) 1-2-3-4  
B) 2-1-3-4  
C) 1-3-2-4  
D) 2-3-1-4  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** Yuechjilar (140–130) → Kushon davlati (mil. I asr) → Kudzula Kadfiz → Kanishka.

**341.** Quyidagi gap to‘g‘rimi? «Mil. avv. 509-yilda Rimda respublika e’lon qilingan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 510-yilda e’lon qilingan  
C) Noto‘g‘ri, chunki 508-yilda e’lon qilingan  
D) Noto‘g‘ri, chunki Rimda respublika bo‘lmagan  
**To‘g‘ri: A.** Manba: Rim respublikasi. **Izoh:** Mil. avv. 509-yilda Rimda respublika e’lon qilingan.

**342.** Quyidagi gap to‘g‘rimi? «Spartak qo‘zg‘oloni mil. avv. 74–71-yillarda bo‘lib o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 74–70-yillarda bo‘lgan  
C) Noto‘g‘ri, chunki 73–71-yillarda bo‘lgan  
D) Noto‘g‘ri, chunki Spartak qo‘zg‘oloni bo‘lmagan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘oloni mil. avv. 74–71-yillarda bo‘lib o‘tgan.

**343.** Quyidagi gap to‘g‘rimi? «Kann jangi mil. avv. 216-yilda bo‘lib o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 212-yilda bo‘lgan  
C) Noto‘g‘ri, chunki 208-yilda bo‘lgan  
D) Noto‘g‘ri, chunki Kann jangi bo‘lmagan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Kann jangi mil. avv. 216-yilda bo‘lib o‘tgan.

**344.** Quyidagi gap to‘g‘rimi? «Yuliy Sezar mil. avv. 49-yilda Rubikon daryosidan o‘tgan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 48-yilda o‘tgan  
C) Noto‘g‘ri, chunki 47-yilda o‘tgan  
D) Noto‘g‘ri, chunki Sezar Rubikondan o‘tmagan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar mil. avv. 49-yilda Rubikon daryosidan o‘tib, hokimiyat uchun kurash boshlagan.

**345.** Quyidagi gap to‘g‘rimi? «G‘arbiy Rim imperiyasi milodiy 476-yilda qulagan.»  
A) To‘g‘ri  
B) Noto‘g‘ri, chunki 475-yilda qulagan  
C) Noto‘g‘ri, chunki 477-yilda qulagan  
D) Noto‘g‘ri, chunki u qulmagan  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** G‘arbiy Rim imperiyasi milodiy 476-yilda qulagan.

**346.** Nega Rimda qullar mehnati samarasiz bo‘lgan?  
A) Qullarga haq to‘lanmagan va ular manfaatdor bo‘lmagan  
B) Qullar juda ko‘p bo‘lgan  
C) Qullar kasal bo‘lgan  
D) Qullar qochib ketgan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Qullarga haq to‘lanmaganligi sababli ular mehnatdan manfaatdor bo‘lmagan.

**347.** Nega Rimda kolonlar paydo bo‘lgan?  
A) Qullar mehnati samarasiz bo‘lib, yer ijarachilari kerak bo‘lgan  
B) Qullar qochib ketgan  
C) Yerlar bo‘sh qolgan  
D) Savdo rivojlangan  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Qullar mehnati samarasiz bo‘lganligi sababli yerlar kolonlarga ijaraga berilgan.

**348.** Nega Rim imperiyasi G‘arbiy va Sharqiyga bo‘lingan?  
A) Imperiyani bir markazdan boshqarish qiyin bo‘lgan  
B) Rimda fuqarolar urushi boshlangan  
C) G‘arbiy Rim mustaqillik e’lon qilgan  
D) Sharqiy Rim imperiyasi kuchliroq bo‘lgan  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ulkan imperiyani Konstantinopoldan turib boshqarish qiyin bo‘lganligi sababli 395-yilda imperiya ikkiga bo‘lingan.

**349.** Nega G‘arbiy Rim imperiyasi qulagan?  
A) Ichki inqiroz va varvarlar hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi G‘arbiy Rimning qulashiga olib kelgan.

**350.** Nega Kushon davlati inqirozga yuz tutgan?  
A) To‘xtovsiz urushlar  
B) Savdoning rivojlanishi  
C) Buddaviylikning tarqalishi  
D) Xitoyning kuchayishi  
**To‘g‘ri: A.** Manba: Kushon davlati. **Izoh:** To‘xtovsiz urushlar Kushon davlatining inqiroziga olib kelgan.

**351.** Rim respublikasining qulashiga sabab nima?  
A) Ichki ziddiyatlar va harbiy diktaturaning kuchayishi  
B) Karfagenning kuchayishi  
C) Yunonistonning hujumi  
D) Savdoning kamayishi  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ichki ziddiyatlar va harbiy diktaturaning kuchayishi respublikaning qulashiga olib kelgan.

**352.** Karfagenning vayron etilishiga sabab nima?  
A) Uchinchi Puni urushi va Rimning g‘alabasi  
B) Birinchi Puni urushi  
C) Ikkinchi Puni urushi  
D) Gannibalning taslim bo‘lishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Uchinchi Puni urushi natijasida mil. avv. 146-yilda Karfagen vayron etilgan.

**353.** G‘arbiy Rim imperiyasining qulashiga sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Rimda tinchlik o‘rnatilishi  
C) Sharqiy Rimning kuchayishi  
D) Savdoning rivojlanishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**354.** Misrning forslar tomonidan bosib olinishiga sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Mil. avv. 525-yilda fors shohi Kambiz II kuchli lashkar bilan Misrni bosib olgan.

**355.** Ossuriyaning qulashiga sabab nima?  
A) Bobil va Midiya tomonidan tor-mor etilishi  
B) Misr tomonidan bosib olinishi  
C) Forslar tomonidan bosib olinishi  
D) Ichki urushlar natijasida  
**To‘g‘ri: A.** Manba: Old Osiyo davlatlari. **Izoh:** Mil. avv. 605-yilda Ossuriya Bobil va Midiya tomonidan tor-mor etilgan.

**356.** Rim va Yunoniston xudolarining o‘xshash jihati nima?  
A) Ikkalasi ham ko‘p xudolikka ishonishgan  
B) Ikkalasi ham bir xil nomdagi xudolarga sig‘ingan  
C) Ikkalasi ham bir vaqtda vujudga kelgan  
D) Ikkalasi ham bir xil ibodatxonalarga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston afsonalari. **Izoh:** Rim ham, Yunoniston ham ko‘p xudolikka ishonishgan va xudolarga sig‘inishgan.

**357.** Rim va Karfagenning o‘xshash jihati nima?  
A) Ikkalasi ham O‘rtayer dengizida hukmronlik uchun kurashgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim va Karfagen O‘rtayer dengizida hukmronlik uchun kurashgan.

**358.** Rim va Yunoniston madaniyatlarining o‘xshash jihati nima?  
A) Ikkalasi ham teatr va me’morchilikni rivojlantirgan  
B) Ikkalasi ham bir xil tilda gapirgan  
C) Ikkalasi ham bir xil qonunlarga ega bo‘lgan  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Ikkala madaniyatda ham teatr, me’morchilik va haykaltaroshlik rivojlangan.

**359.** Oktavian Avgust va Yuliy Sezarning o‘xshash jihati nima?  
A) Ikkalasi ham Rimda yakka hokimlik o‘rnatgan  
B) Ikkalasi ham bir vaqtda yashagan  
C) Ikkalasi ham Karfagenni bosib olgan  
D) Ikkalasi ham bir xil lavozimda bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Ikkalasi ham Rimda yakka hokimlik o‘rnatgan.

**360.** Kushon va Yunon-Baqtriya davlatlarining o‘xshash jihati nima?  
A) Ikkalasi ham O‘rta Osiyo hududida tashkil topgan  
B) Ikkalasi ham bir xil dinlarga ega bo‘lgan  
C) Ikkalasi ham bir vaqtda tashkil topgan  
D) Ikkalasi ham bir xil yozuvga ega bo‘lgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Ikkalasi ham O‘rta Osiyo hududida tashkil topgan.

**361.** Rim respublikasi va Rim imperiyasining farqi nimada?  
A) Respublikada konsullar saylangan, imperiyada yakka hukmdor bo‘lgan  
B) Respublikada imperator, imperiyada konsullar bo‘lgan  
C) Ikkalasi ham bir xil boshqaruvga ega bo‘lgan  
D) Respublikada podsho, imperiyada senat bo‘lgan  
**To‘g‘ri: A.** Manba: Rim respublikasi, Rim imperiyasining qulashi. **Izoh:** Respublikada konsullar saylangan, imperiyada esa yakka hukmdor (imperator) bo‘lgan.

**362.** Rim va Yunoniston madaniyatlarining farqi nimada?  
A) Rim me’morchiligida beton va gumbaz ishlatilgan, Yunonistonda ustunlar  
B) Yunonistonda beton, Rimda ustunlar ishlatilgan  
C) Ikkalasi ham bir xil me’morchilikka ega  
D) Ikkalasi ham bir vaqtda vujudga kelgan  
**To‘g‘ri: A.** Manba: Qadimgi Rim madaniyati, Qadimgi Yunoniston madaniyati. **Izoh:** Rim me’morchiligida beton va gumbaz, Yunonistonda ustunlar keng ishlatilgan.

**363.** Oktavian Avgust va Yuliy Sezarning farqi nimada?  
A) Sezar diktator bo‘lgan, Oktavian imperator unvonini olgan  
B) Oktavian diktator bo‘lgan, Sezar imperator unvonini olgan  
C) Ikkalasi ham bir xil lavozimda bo‘lgan  
D) Ikkalasi ham bir vaqtda yashagan  
**To‘g‘ri: A.** Manba: Rim respublikasining qulashi. **Izoh:** Yuliy Sezar diktator bo‘lgan, Oktavian esa imperator unvonini olgan.

**364.** Kushon va Yunon-Baqtriya davlatlarining farqi nimada?  
A) Yunon-Baqtriya ellinistik madaniyatga, Kushon buddaviylikka asoslangan  
B) Yunon-Baqtriya buddaviylikka, Kushon ellinistik madaniyatga asoslangan  
C) Ikkalasi ham bir xil madaniyatga ega  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Salavkiylar davlati, Kushon davlati. **Izoh:** Yunon-Baqtriya ellinistik madaniyatga, Kushon davlati buddaviylik diniga asoslangan.

**365.** Rim va Karfagenning farqi nimada?  
A) Rim Italiyada, Karfagen Shimoliy Afrikada joylashgan  
B) Rim Shimoliy Afrikada, Karfagen Italiyada joylashgan  
C) Ikkalasi ham bir xil hududda joylashgan  
D) Ikkalasi ham bir vaqtda tashkil topgan  
**To‘g‘ri: A.** Manba: Qadimgi Italiya, O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Rim Italiyada, Karfagen Shimoliy Afrikada joylashgan.

**366.** «U 30 yoshida yangi diniy ta’limot payg‘ambari bo‘lgan.» Bu kim haqida?  
A) Budda  
B) Zardusht  
C) Iso  
D) Suqrot  
**To‘g‘ri: B.** Manba: Zardushtiylik. **Izoh:** Zardusht 30 yoshida payg‘ambar bo‘lgan.

**367.** «U o‘zining dono hikmatlari tufayli shaharning eng aqlli kishisi deb e’lon qilingan edi.» Bu kim haqida?  
A) Suqrot  
B) Aflotun  
C) Arastu  
D) Diogen  
**To‘g‘ri: A.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Suqrot shaharning eng aqlli kishisi deb e’lon qilingan.

**368.** «Uning qo‘shini 5400 nafar jangchidan iborat bo‘lgan va u jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan.» Bu kim haqida?  
A) Sargon I  
B) Xammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**369.** «Ular uzum novdalaridan narvon to‘qib, ko‘rinmaydigan tarafdan cho‘qqidan pastga tushib oldilar.» Bu qaysi voqea haqida?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Fermopil jangi  
D) Salamin jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak qo‘zg‘olonida qullar Vezuviy tog‘idan uzum novdalaridan narvon to‘qib tushgan.

**370.** «Ular otlar qo‘shilgan jangovar aravalardan foydalanganlar.» Bu qaysi xalq haqida?  
A) misrliklar  
B) giksoslar  
C) shumerlar  
D) rimliklar  
**To‘g‘ri: B.** Manba: Qadimgi Misr. **Izoh:** Giksoslar otlar qo‘shilgan jangovar aravalardan foydalanganlar.

**371.** Nil daryosi qaysi dengizga quyiladi?  
A) O‘rtayer dengiziga  
B) Qizil dengizga  
C) Arabiston dengiziga  
D) Qora dengizga  
**To‘g‘ri: A.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil daryosi O‘rtayer dengiziga quyiladi.

**372.** Dajla va Frot daryolari orasidagi hudud qanday ataladi?  
A) Misr  
B) Mesopotamiya  
C) Hindiston  
D) Xitoy  
**To‘g‘ri: B.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Dajla va Frot oralig‘i Mesopotamiya deb ataladi.

**373.** Behistun qoyalari qayerda joylashgan?  
A) Surxondaryoda  
B) Erondagi Kirmonshoh yaqinida  
C) Misrda Memfis yaqinida  
D) Bobilda  
**To‘g‘ri: B.** Manba: Qadimgi tarix bo‘yicha manbalar. **Izoh:** Behistun qoyalari Eronning Kirmonshoh shahri yaqinida.

**374.** Teshiktosh g‘ori qayerda joylashgan?  
A) Boysun tog‘larida  
B) Toshkent vohasida  
C) Farg‘ona vodiysida  
D) Boshqirdistonda  
**To‘g‘ri: A.** Manba: Eng qadimgi odamlarning mashg‘ulotlari. **Izoh:** Teshiktosh g‘ori Boysun tog‘larida joylashgan.

**375.** Qadimgi Yunonistonning eng baland tog‘i qaysi?  
A) Olimp  
B) Peloponnes  
C) Atos  
D) Parnas  
**To‘g‘ri: A.** Manba: Antik tarixning boshlanishi. **Izoh:** Yunonistonning eng baland tog‘i Olimp.

**376.** Quyidagilardan qaysi biri Misr fir’avni emas?  
A) Menes  
B) Yaxmos  
C) Tutmos III  
D) Xammurapi  
**To‘g‘ri: D.** Manba: Qadimgi Misr, Bobil podsholigi. **Izoh:** Xammurapi Bobil podshosi, qolganlar Misr fir’avnlari.

**377.** Quyidagilardan qaysi biri Mesopotamiya shahri emas?  
A) Uruk  
B) Ur  
C) Lagash  
D) Memfis  
**To‘g‘ri: D.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Memfis Misr shahri, qolganlar Mesopotamiya shaharlari.

**378.** Quyidagilardan qaysi biri yunon faylasufi emas?  
A) Geraklit  
B) Demokrit  
C) Suqrot  
D) Gannibal  
**To‘g‘ri: D.** Manba: Qadimgi Yunoniston olimlari. **Izoh:** Gannibal Karfagen sarkardasi, qolganlar yunon faylasuflari.

**379.** Quyidagilardan qaysi biri Rim shahri emas?  
A) Rim  
B) Pompeya  
C) Karfagen  
D) Neapol  
**To‘g‘ri: C.** Manba: Qadimgi Rim. **Izoh:** Karfagen Shimoliy Afrikadagi shahar, qolganlar Italiyadagi shaharlar.

**380.** Quyidagilardan qaysi biri O‘rta Osiyo manzilgohi emas?  
A) Teshiktosh  
B) Selungur  
C) Ko‘lbu loq  
D) Altamir  
**To‘g‘ri: D.** Manba: Eng qadimgi odamlar. **Izoh:** Altamir Ispaniyadagi g‘or, qolganlar O‘rta Osiyo manzilgohlari.

**381.** Misrni yagona davlatga birlashtirgan birinchi fir’avn kim?  
A) Yaxmos  
B) Menes  
C) Tutmos III  
D) Tutanxamon  
**To‘g‘ri: B.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Menes Misrni birlashtirgan birinchi fir’avn.

**382.** Temirdan birinchi bo‘lib foydalangan xalq qaysi?  
A) misrliklar  
B) shumerlar  
C) xettlar  
D) yunonlar  
**To‘g‘ri: C.** Manba: Temir davriga o‘tish. **Izoh:** Temirdan birinchi bo‘lib Kichik Osiyodagi xettlar foydalangan.

**383.** Misr iyerogliflarini birinchi bo‘lib o‘qigan olim kim?  
A) Genrix Shlimann  
B) Jak-Fransua Shampolyon  
C) Gerodot  
D) Strabon  
**To‘g‘ri: B.** Manba: Qadimgi Misr madaniyati. **Izoh:** Shampolyon 1822-yilda iyerogliflarni birinchi bo‘lib o‘qigan.

**384.** Jahon tarixida birinchi bo‘lib muntazam qo‘shin tuzgan shoh kim?  
A) Sargon I  
B) Xammurapi  
C) Doro I  
D) Aleksandr  
**To‘g‘ri: A.** Manba: Mesopotamiya sivilizatsiyalari. **Izoh:** Sargon I birinchi bo‘lib muntazam qo‘shin tuzgan.

**385.** Birinchi Olimpiya o‘yinlari qachon o‘tkazilgan?  
A) mil. avv. 776-yilda  
B) mil. avv. 753-yilda  
C) mil. avv. 490-yilda  
D) mil. avv. 480-yilda  
**To‘g‘ri: A.** Manba: Fuqaro tarbiyasi. **Izoh:** Birinchi Olimpiya o‘yinlari mil. avv. 776-yilda o‘tkazilgan.

**386.** Rim imperiyasining qulashiga eng muhim sabab nima?  
A) Ichki inqiroz va varvarlar hujumi  
B) Savdoning rivojlanishi  
C) Sharqiy Rimning kuchayishi  
D) Rimda tinchlik o‘rnatilishi  
**To‘g‘ri: A.** Manba: Rim imperiyasining qulashi. **Izoh:** Ichki inqiroz va varvar qabilalarining hujumi asosiy sabab bo‘lgan.

**387.** Misrning forslar tomonidan bosib olinishiga eng muhim sabab nima?  
A) Misrning zaiflashishi va fors lashkarining kuchliligi  
B) Misrning forslar bilan ittifoqi  
C) Misrda fuqarolar urushi  
D) Misr iqtisodiyotining yemirilishi  
**To‘g‘ri: A.** Manba: Qadimgi Misr. **Izoh:** Misrning zaiflashishi va fors lashkarining kuchliligi asosiy sabab.

**388.** Ossuriyaning qulashiga eng muhim sabab nima?  
A) Bobil va Midiya tomonidan tor-mor etilishi  
B) Misr tomonidan bosib olinishi  
C) Forslar tomonidan bosib olinishi  
D) Ichki urushlar natijasida  
**To‘g‘ri: A.** Manba: Old Osiyo davlatlari. **Izoh:** Bobil va Midiya tomonidan tor-mor etilishi asosiy sabab.

**389.** Yunon koloniyalarining tashkil topishiga eng muhim sabab nima?  
A) Yersiz va qarzdor yunonlarning vatanini tark etishi  
B) Yunonlarning savdo qilish istagi  
C) Urushlardan qochish  
D) Diniy sabablar  
**To‘g‘ri: A.** Manba: Yunon koloniyalari. **Izoh:** Yersiz va qarzdor yunonlarning vatanini tark etishi asosiy sabab.

**390.** Buyuk Ipak yo‘lining vujudga kelishiga eng muhim sabab nima?  
A) Sharq va G‘arb o‘rtasida savdo va madaniy aloqalar zarurati  
B) Xitoy imperatorining sayohat istagi  
C) Rim imperiyasining savdo talabi  
D) O‘rta Osiyoning mustaqilligi  
**To‘g‘ri: A.** Manba: Buyuk Ipak yo‘li. **Izoh:** Savdo va madaniy aloqalar zarurati asosiy sabab.

**391.** Nil daryosining uzunligi qancha?  
A) 4 ming km  
B) 5 ming km  
C) 6 ming km  
D) 7 ming km  
**To‘g‘ri: C.** Manba: Nil vodiysi va uning aholisi. **Izoh:** Nil 6 ming kilometr uzunlikda.

**392.** Xufu piramidasining balandligi qancha?  
A) 100 metr  
B) 147 metr  
C) 200 metr  
D) 250 metr  
**To‘g‘ri: B.** Manba: Piramidalar va daxmalar. **Izoh:** Xufu piramidasi balandligi 147 metr.

**393.** Mumiyolash qancha kun davom etgan?  
A) 40 kun  
B) 50 kun  
C) 70 kun  
D) 90 kun  
**To‘g‘ri: C.** Manba: Piramidalar va daxmalar. **Izoh:** Mumiyolash qariyb 70 kun davom etgan.

**394.** Misr iyerogliflarining soni nechta?  
A) 500 ta  
B) 600 ta  
C) 750 ta  
D) 1000 ta  
**To‘g‘ri: C.** Manba: Qadimgi Misr madaniyati. **Izoh:** Misr alifbosi 750 iyeroglifdan iborat bo‘lgan.

**395.** Rim senati necha kishidan iborat bo‘lgan?  
A) 100 kishi  
B) 200 kishi  
C) 300 kishi  
D) 500 kishi  
**To‘g‘ri: C.** Manba: Rim respublikasi. **Izoh:** Rim senati 300 kishidan iborat bo‘lgan.

**396.** Vezuviy tog‘i qaysi voqea bilan bog‘liq?  
A) Spartak qo‘zg‘oloni  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: Qullar va gladiatorlar. **Izoh:** Spartak va uning tarafdorlari Vezuviy tog‘iga chiqib olgan.

**397.** Fermopil darasi qaysi voqea bilan bog‘liq?  
A) Marafon jangi  
B) Fermopil jangi  
C) Salamin jangi  
D) Plateya jangi  
**To‘g‘ri: B.** Manba: Yunon-fors urushlari. **Izoh:** Fermopil darasida 300 spartalik jasorati bo‘lib o‘tgan.

**398.** Zama shahri qaysi voqea bilan bog‘liq?  
A) Gannibalning tor-mor etilishi  
B) Marafon jangi  
C) Salamin jangi  
D) Fermopil jangi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 202-yilda Zama yaqinida Gannibal armiyasi tor-mor etilgan.

**399.** Kann shahri qaysi voqea bilan bog‘liq?  
A) Rim armiyasining tor-mor etilishi  
B) Karfagenning vayron etilishi  
C) Zama jangi  
D) Puni urushlari boshlanishi  
**To‘g‘ri: A.** Manba: O‘rtayer dengizida hukmronlik uchun kurash. **Izoh:** Mil. avv. 216-yilda Kann yaqinida Rim armiyasi tor-mor etilgan.

**400.** Maroqanda shahri qaysi voqea bilan bog‘liq?  
A) Aleksandr tomonidan bosib olinishi  
B) Spitamanning halok bo‘lishi  
C) Yunon-Baqtriya davlatining tashkil topishi  
D) Kushon davlatining tashkil topishi  
**To‘g‘ri: A.** Manba: O‘rta Osiyo tarixi. **Izoh:** Mil. avv. 329-yilda Aleksandr Maroqandani bosib olgan.
"""

class Command(BaseCommand):
    help = "201-400 gacha bo'lgan to'liq testlarni bazaga va JSON fayliga kiritish"

    def handle(self, *args, **options):
        self.stdout.write("201-400 testlarni tahlil qilish va bazaga joylashtirish boshlandi...")

        all_text = USER_TEXT_201_300 + "\n\n" + USER_TEXT_301_400

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\*\*\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\*\s*To‘g‘ri:\s*([A-D])\.\*\*\s*Manba:\s*(.*?)\.\s*\*\*\s*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|\n\n###|$)',
            all_text,
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

            c_hash = hashlib.md5(f"user_q6_v4_{num}_{q_clean}".encode('utf-8')).hexdigest()

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
