import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT_V2 = """
**1. Qadimgi dunyo tarixi qaysi voqea bilan yakunlanadi?**
A) 395-yilda Rim imperiyasining G‘arbiy va Sharqiyga bo‘linishi
B) 476-yilda G‘arbiy Rim imperiyasining butunlay qulashi
C) 473-yilda G‘arbiy Rim imperiyasining butunlay qulashi
D) 313-yilda xristianlikning Rimda teng din deb e’lon qilinishi
* **To‘g‘ri javob:** B
* **Manba:** Kirish. Qadimgi dunyo tarixi tushunchasi.
* **Izoh:** Darslikda qadimgi dunyo tarixi “milodiy 476-yilda G‘arbiy Rim imperiyasi qulaguniga qadar” davom etishi aniq yozilgan. 473-yil xato sana (chalg‘ituvchi), 395-yil imperiya bo‘lingan yil, 313-yil esa xristianlik teng deb e’lon qilingan yil.

**2. Epigrafika nimani o‘rganadi?**
A) qadimiy tangalarni va ularning yozuvlarini
B) inson suyak qoldiqlarini va qiyofasini
C) qadimgi bitik va yozuvlarni
D) xalqlarning qadimgi udumlarini
* **To‘g‘ri javob:** C
* **Manba:** Kirish. Tarixiy manbalar va yordamchi fanlar.
* **Izoh:** Matnda "Qadimgi bitiklarni o‘rganish bilan epigrafika... shug‘ullanadi" deb yozilgan. A – numizmatika, B – antropologiya, D – etnografiya.

**3. Numizmatika qaysi soha bilan shug‘ullandi?**
A) qadimgi tangalarni o‘rganish
B) qadimgi yozuvlarni o‘rganish
C) inson suyaklarini o‘rganish
D) qadimgi udumlarni o‘rganish
* **To‘g‘ri javob:** A
* **Manba:** Kirish. Tarixiy manbalar va yordamchi fanlar.
* **Izoh:** Kitobda "tangalarni o‘rganish bilan esa numizmatika degan maxsus fanlar shug‘ullanadi" deb yozilgan. Boshqa variantlar boshqa fanlarga tegishli.

**4. «Piramidalar matnlari» va «Marhumlar kitobi» qaysi xalq tarixiga oid?**
A) qadimgi hindlar tarixiga oid
B) qadimgi shumerlar tarixiga oid
C) qadimgi misrliklar tarixiga oid
D) O‘rta Osiyo xalqlari tarixiga oid
* **To‘g‘ri javob:** C
* **Manba:** Kirish. Qadimgi yozma manbalar.
* **Izoh:** Darslikda bu kitoblar misrliklar tarixi haqida ekanligi ta'kidlangan. Hindlar – «Rigveda», O‘rta Osiyo – «Avesto».

**5. «Rigveda» qaysi xalq tarixidan hikoya qiladi?**
A) qadimgi misrliklar tarixidan
B) qadimgi hindlar tarixidan
C) qadimgi rimliklar tarixidan
D) O‘rta Osiyo xalqlari tarixidan
* **To‘g‘ri javob:** B
* **Manba:** Kirish. Qadimgi yozma manbalar.
* **Izoh:** Matnda "«Rigveda» – hindlar" deb aniq ko‘rsatilgan. Misrliklar – Piramidalar matnlari, O‘rta Osiyo – Avesto.

**6. «Avesto» qaysi xalq tarixi bo‘yicha manba hisoblanadi?**
A) qadimgi hindlar tarixi bo‘yicha
B) qadimgi misrliklar tarixi bo‘yicha
C) qadimgi rimliklar tarixi bo‘yicha
D) O‘rta Osiyo xalqlari tarixi bo‘yicha
* **To‘g‘ri javob:** D
* **Manba:** Kirish. Qadimgi yozma manbalar.
* **Izoh:** Kitobda "«Avesto» esa O‘rta Osiyo xalqlari tarixidan hikoya qiladi" deyilgan. Qolganlari boshqa xalqlar manbalari.

**7. Qadimgi dunyo tarixi qaysi davrni qamraydi?**
A) odam paydo bo‘lishidan 476-yilda G‘arbiy Rim qulaguniga qadar
B) odam paydo bo‘lishidan 473-yilda G‘arbiy Rim qulaguniga qadar
C) mil. avv. 3000-yildan milodiy 476-yilgacha bo‘lgan davrni
D) faqat yozuv paydo bo‘lishidan keyingi davrni
* **To‘g‘ri javob:** A
* **Manba:** Kirish. Qadimgi dunyo tarixi tushunchasi.
* **Izoh:** Darslikda "odam paydo bo‘lganidan to milodiy 476-yilda G‘arbiy Rim imperiyasi qulaguniga qadar" deb yozilgan. B - xato sana, C - noto'g'ri boshlanish, D - yozuvdan oldingi davr ham kiradi.

**8. Arxeologlar asosan nima bilan shug‘ullanadilar?**
A) suyaklarni tekshirib tashqi qiyofani tiklaydilar
B) qadimgi urf-odatlarni o‘rganadilar
C) manzilgohlarda qazishma ishlari olib boradilar
D) qadimgi tangalarni o‘rganadilar
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Qadimgi tarixni o‘rganish.
* **Izoh:** Matnda "Arxeologlar qadim zamonlarda odamlar yashagan manzilgohlarda qazishma ishlarini amalga oshiradilar" deyilgan. A - antropologlar, B - etnograflar, D - numizmatlar.

**9. Antropologlar nimani o‘rganadilar?**
A) manzilgohlarda qazishma ishlarini
B) suyak qoldiqlari asosida tashqi qiyofani
C) turli xalqlarning qadimgi udumlarini
D) toshga bitilgan qadimgi yozuvlarni
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Qadimgi tarixni o‘rganish.
* **Izoh:** "Antropologlar qadimgi odamlarning suyak qoldiqlarini tekshirib, ularning tashqi qiyofasini tiklaydilar" deb yozilgan. A - arxeologlar, C - etnograflar, D - epigrafistlar.

**10. Etnograflar nimani o‘rganadilar?**
A) qadimgi manzilgohlardagi qazishmalarni
B) qadimgi davlatlarning tangalarini
C) qadimgi odamlarning suyaklarini
D) qadimgi udumlar va an’analarni
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Qadimgi tarixni o‘rganish.
* **Izoh:** "Etnograflar qadimgi odamlarning ko‘pgina udumlari... xo‘jalik va madaniy an’analarini o‘rganadilar". A - arxeologlar, B - numizmatlar, C - antropologlar.

**11. Moddiy manbalarga nima kiradi?**
A) mehnat qurollari, sopol idishlar, zeb-ziynatlar
B) zardushtiylarning muqaddas «Avesto» kitobi
C) Gerodot yozib qoldirgan «Tarix» asari
D) Doro I buyrug‘i bilan yozilgan Behistun yozuvlari
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** Darslikda "Mehnat qurollari, sopol idishlar, qurol-aslahalar, zeb-ziynat buyumlari... moddiy manbalardir" deyilgan. B, C, D - bular yozma manbalar.

**12. O‘rta Osiyo tarixi bo‘yicha eng qadimgi yozma manba nima?**
A) Doro I ning Behistun yozuvlari
B) Gerodotning «Tarix» nomli asari
C) zardushtiylarning muqaddas «Avesto»si
D) Strabonning «Geografiya» nomli asari
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "O‘rta Osiyo tarixi bo‘yicha eng qadimgi yozma manba zardushtiylarning muqaddas kitobi «Avesto»dir". Qolganlari keyinroq yaratilgan.

**13. Behistun qoyalari qayerda joylashgan?**
A) Surxondaryo vohasidagi tog‘larda
B) Erondagi Kirmonshoh shahri yaqinida
C) Misrdagi Memfis shahri yaqinida
D) Bobil shahri atrofidagi qoyalarda
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Behistun qoyalari Erondagi Kirmonshoh shahri yaqinida joylashgan". Qolgan joylar geografik jihatdan noto'g'ri.

**14. Behistun yozuvlari kimning buyrug‘i bilan o‘yib yozilgan?**
A) Fors shohi Kir II buyrug‘i bilan
B) Fors shohi Kserks buyrug‘i bilan
C) Makedoniyalik Aleksandr buyrug‘i bilan
D) Fors shohi Doro I buyrug‘i bilan
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Fors shohi Doro I buyrug‘i bilan qoyaga o‘yib yozilgan yozuvlarda..." deyilgan. Kir II va Kserks boshqa hukmdorlar, Aleksandr esa Forsni zabt etgan.

**15. Gerodotning mashhur asari qanday nomlanadi?**
A) «Tarix» nomli asari
B) «Geografiya» nomli asari
C) «Makedoniyalik Aleksandr tarixi»
D) «Aleksandrning harbiy yurishlari»
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Gerodot... «Tarix» asarini yozdi". B - Strabon, C - Kvint Kursiy Ruf, D - Arrian.

**16. Gerodot «Tarix» asarini nechta kitobdan iborat qilib yozgan?**
A) 7 ta kitobdan iborat qilib
B) 12 ta kitobdan iborat qilib
C) 9 ta kitobdan iborat qilib
D) 21 ta kitobdan iborat qilib
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** Darslikda "to‘qqiz kitobdan iborat «Tarix» asarini yozdi" deb aniq ko‘rsatilgan.

**17. Strabon qaysi asarni yozgan?**
A) «Tarix» nomli mashhur asarni
B) «Geografiya» nomli mashhur asarni
C) «Makedoniyalik Aleksandr tarixi»ni
D) «Aleksandrning harbiy yurishlari»ni
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Strabon «Geografiya» nomli asarida O‘rta Osiyo xalqlari madaniyati haqida ma’lumotlar keltiradi". A - Gerodot, C - Kvint Kursiy Ruf, D - Arrian.

**18. Kvint Kursiy Ruf qaysi asar muallifi?**
A) «Geografiya» nomli asar muallifi
B) «Tarix» nomli mashhur asar muallifi
C) «Avesto» nomli muqaddas kitob muallifi
D) «Makedoniyalik Aleksandr tarixi» muallifi
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Kvint Kursiy Ruf «Makedoniyalik Aleksandr tarixi» nomli asarni yozib...". A - Strabon, B - Gerodot, C - zardushtiylar kitobi.

**19. Arrian qaysi asarni yozgan?**
A) «Aleksandrning harbiy yurishlari»ni
B) «Tarix» nomli mashhur asarni
C) «Geografiya» nomli mashhur asarni
D) «Makedoniyalik Aleksandr tarixi»ni
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Qadimgi tarix bo‘yicha manbalar.
* **Izoh:** "Arrian ham «Aleksandrning harbiy yurishlari» («Aleksandr anabasisi») deb nomlangan asar yozgan". D - Kvint Kursiy Rufga tegishli.

**20. Ibtidoiy jamoa tuzumida mehnat qurollari qanday bo‘lgan?**
A) faqat xususiy mulk hisoblangan
B) faqat quldorlarga tegishli bo‘lgan
C) barcha uchun umumiy bo‘lgan
D) faqat ibodatxonalarga tegishli bo‘lgan
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** "Ibtidoiy jamoa tuzumi... barcha mehnat qurollari umumiy bo‘lgan va hamma birgalikda mehnat qilgan". Xususiy mulk keyingi davrlarda paydo bo‘lgan.

**21. Ibtidoiy to‘da nima?**
A) qarindoshlardan tashkil topgan ixcham guruh
B) turmush va mehnat umumiyligidagi eng qadimgi jamoa
C) bir necha qabilalardan tashkil topgan harbiy ittifoq
D) o‘zini o‘zi boshqaruvchi mustaqil shahar-davlat
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** Darslikda aynan shu ta’rif berilgan: "Ibtidoiy to‘da bu – turmush va mehnat umumiyligi negizida birlashgan eng qadimgi odamlar jamoasi". A - urug‘, C - qabilalar ittifoqi, D - keyingi davr tuzilmasi.

**22. Urug‘ nima?**
A) bir necha qabilalarning harbiy va siyosiy ittifoqi
B) eng qadimgi odamlarning katta ibtidoiy to‘dasi
C) umumiy qurollarga ega bo‘lgan qarindoshlar jamoasi
D) o‘zini o‘zi boshqaruvchi mustaqil shahar-davlat
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** "Urug‘ bu – birgalikda yashagan va mehnat qilgan, umumiy mehnat qurollari va qurol-yarog‘larga ega bo‘lgan qarindoshlar jamoasi". A, B, D boshqa tuzilmalar.

**23. Dastlabki urug‘ jamoalari kim atrofida jipslashgan?**
A) yoshi katta ayol (ona) atrofida jipslashgan
B) eng kuchli erkak (ota) atrofida jipslashgan
C) tajribali harbiy sardor atrofida jipslashgan
D) diniy marosimlarni biluvchi kohin atrofida
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** "Dastlabki urug‘ jamoalari ayol kishi, ya’ni ona atrofida jipslasha boshlagan". Ota - patriarxat davrida, sardor - harbiy demokratiyada.

**24. Matriarxat nima?**
A) ota urug‘i hukmronlik qilgan tarixiy davr
B) qabilalar ittifoqi tuzilgan tarixiy davr
C) ona urug‘i hukmronlik qilgan tarixiy davr
D) harbiy demokratiya qaror topgan davr
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** "Insoniyat tarixidagi bu bosqich ona urug‘i davri – matriarxat deb ataladi". A - patriarxat, B, D - boshqa tuzilmalar.

**25. Patriarxat nima?**
A) ona urug‘i hukmronlik qilgan tarixiy davr
B) ota urug‘i hukmronlik qilgan tarixiy davr
C) shahar-davlatlar paydo bo‘lgan tarixiy davr
D) ilk quldorlik tuzumi shakllangan tarixiy davr
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Ibtidoiy jamoa tuzumi.
* **Izoh:** "Olimlar insoniyat tarixidagi bu davrni patriarxat – ota urug‘i davri – deb atashadi". A - matriarxat.

**26. Tosh davri bosqichlari qaysi qatorda to‘g‘ri berilgan?**
A) mezolit, paleolit, neolit, eneolit
B) neolit, paleolit, mezolit, eneolit
C) paleolit, neolit, mezolit, eneolit
D) paleolit, mezolit, neolit, eneolit
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** Tosh davri bosqichlari xronologik tartibda: paleolit → mezolit → neolit → eneolit. Boshqa variantlarda tartib buzilgan.

**27. «Paleolit» so‘zi qanday ma’noni bildiradi?**
A) qadimgi tosh davri ma’nosini
B) o‘rta tosh davri ma’nosini
C) yangi tosh davri ma’nosini
D) mis va tosh davri ma’nosini
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** "paleolit (yunoncha «paleos» – «qadimgi» va «litos» – «tosh» so‘zlaridan)". B - mezolit, C - neolit, D - eneolit.

**28. «Mezolit» so‘zi qanday ma’noni bildiradi?**
A) qadimgi tosh davri ma’nosini
B) yangi tosh davri ma’nosini
C) o‘rta tosh davri ma’nosini
D) mis va tosh davri ma’nosini
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** "mezolit («mezos» – «o‘rta»)". A - paleolit, B - neolit.

**29. «Neolit» so‘zi qanday ma’noni bildiradi?**
A) qadimgi tosh davri ma’nosini
B) yangi tosh davri ma’nosini
C) o‘rta tosh davri ma’nosini
D) mis va tosh davri ma’nosini
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** "neolit («neos» – «yangi»)". C - mezolit, D - eneolit.

**30. «Eneolit» so‘zi qanday ma’noni bildiradi?**
A) yangi tosh davri ma’nosini
B) o‘rta tosh davri ma’nosini
C) qadimgi tosh davri ma’nosini
D) mis va tosh davri ma’nosini
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** "eneolit (lotincha «eneus» – «mis» va yunoncha «litos» – «tosh»)". A - neolit, B - mezolit.

**31. Ilk paleolit davri qaysi davrga to‘g‘ri keladi?**
A) mil. avv. 100–40-ming yilliklar
B) mil. avv. 1 mln–100-ming yilliklar
C) mil. avv. 40–12-ming yilliklar
D) mil. avv. 12–7-ming yilliklar
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** Ilk paleolit – mil. avv. 1 million – 100-ming yilliklar. A - o‘rta paleolit, C - so‘nggi paleolit, D - mezolit.

**32. O‘rta paleolit davri qaysi davrni qamraydi?**
A) mil. avv. 100–40-ming yilliklar
B) mil. avv. 1 mln–100-ming yilliklar
C) mil. avv. 40–12-ming yilliklar
D) mil. avv. 12–7-ming yilliklar
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** O‘rta paleolit – mil. avv. 100–40-ming yilliklar. B - ilk paleolit, C - so‘nggi paleolit.

**33. So‘nggi paleolit davri qaysi davrga to‘g‘ri keladi?**
A) mil. avv. 1 mln–100-ming yilliklar
B) mil. avv. 100–40-ming yilliklar
C) mil. avv. 40–12-ming yilliklar
D) mil. avv. 12–7-ming yilliklar
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Tosh davri.
* **Izoh:** So‘nggi paleolit – mil. avv. 40–12-ming yilliklar. D - mezolit davri.

**34. Mezolit davri qaysi davrni o‘z ichiga oladi?**
A) mil. avv. 6–4-ming yilliklar
B) mil. avv. 4–3-ming yilliklar
C) mil. avv. 40–12-ming yilliklar
D) mil. avv. 12–7-ming yilliklar
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Urug‘chilik jamiyati (Mezolit davri).
* **Izoh:** "Mezolit davri (o‘rta tosh davri) miloddan avvalgi 12–7-ming yilliklarni o‘z ichiga oladi". A - neolit, B - eneolit, C - so‘nggi paleolit.

**35. O‘rta Osiyoda neolit davri qaysi davrga to‘g‘ri keladi?**
A) mil. avv. 6–4-ming yilliklar
B) mil. avv. 12–7-ming yilliklar
C) mil. avv. 4–3-ming yilliklar
D) mil. avv. 3–2-ming yilliklar
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Dehqonchilik va chorvachilikning rivojlanishi (Neolit).
* **Izoh:** "O‘rta Osiyoda neolit davri miloddan avvalgi 6–4-ming yilliklar hisoblanadi". B - mezolit, C - eneolit.

**36. Avstralopitek suyaklari qayerdan topilgan?**
A) Sharqiy Afrika, Zinj vodiysidan
B) Indoneziyadagi Yava orolidan
C) Janubiy Afrika hududidan
D) Xitoy hududidan topilgan
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Janubiy Afrikadan – avstralopitek". A - zinjantrop, B - pitekantrop, D - sinantrop.

**37. Zinjantrop qayerdan topilgan?**
A) Janubiy Afrika hududidan
B) Sharqiy Afrika, Zinj vodiysidan
C) Indoneziyadagi Yava orolidan
D) Germaniya hududidan topilgan
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Sharqiy Afrikadagi Zinj vodiysidan – zinjantrop". A - avstralopitek, C - pitekantrop.

**38. Pitekantrop qayerdan topilgan?**
A) Xitoy hududidan topilgan
B) Germaniya hududidan topilgan
C) Fransiyadagi g‘orlardan topilgan
D) Indoneziyadagi Yava orolidan
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Yava orolidan (Indoneziya) – pitekantrop". A - sinantrop, B - neandertal, C - kromanyon.

**39. Sinantrop qayerdan topilgan?**
A) Xitoy hududidan topilgan
B) Indoneziyadagi Yava orolidan
C) Janubiy Afrika hududidan
D) Fransiyadagi Kromanyon g‘oridan
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Xitoy hududidan – sinantrop". B - pitekantrop, C - avstralopitek.

**40. Neandertal suyaklari qayerdan topilgan?**
A) Fransiyadagi Kromanyon g‘oridan
B) Xitoy hududidan topilgan
C) Germaniya hududidan topilgan
D) Indoneziyadagi Yava orolidan
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Germaniyadan – neandertal". A - kromanyon, B - sinantrop, D - pitekantrop.

**41. Kromanyon suyaklari qayerdan topilgan?**
A) Germaniya hududidan topilgan
B) Fransiyadagi Kromanyon g‘oridan
C) Xitoy hududidan topilgan
D) Indoneziyadagi Yava orolidan
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlar.
* **Izoh:** "Fransiyadagi Kromanyon g‘oridan – hozirgi qiyofadagi odam – kromanyon suyaklari topilgan". A - neandertal.

**42. O‘rta Osiyoda toshdan yasalgan qadimiy mehnat qurollari qaysi manzilgohlardan topilgan?**
A) Teshiktosh va Kapova g‘orlaridan topilgan
B) Altamir va Lasko g‘orlaridan topilgan
C) Afrosiyob va Yerqo‘rg‘on shaharlaridan
D) Selungur va Ko‘lbu loq manzilgohlaridan
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "Farg‘ona vodiysidagi Selungur makonidan va Toshkent vohasidagi Ko‘lbu loq manzilgohidan ham topilgan". A - Teshiktosh o‘rta paleolitga oid, B - rasmlar, C - bronza davri.

**43. Chopperlar nima?**
A) bir tomoni urib o‘tkirlangan qo‘pol tosh qurollar
B) mezolit davrida ishlatilgan eng qadimgi o‘q-yoylar
C) neolit davrida yasalgan mayda tosh qurollar
D) bronza davrida ishlatilgan kulolchilik charxi
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "ularning bir tomoni urib o‘tkirlangan. Arxeologiya fanida bu tosh qurollar chopperlar deb nomlangan". C - mezolit davridagi mayda tosh qurollar.

**44. Eng qadimgi mashg‘ulotlar qaysilar?**
A) dehqonchilik va chorvachilik
B) hunarmandchilik va savdo
C) termachilik va ovchilik
D) kulolchilik va to‘quvchilik
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "kishilarning eng qadimiy mashg‘uloti bo‘lgan termachilik va ovchilik". A, B, D keyingi davrlarda (neolit) paydo bo‘lgan.

**45. O‘zlashtiruvchi xo‘jalik nima?**
A) dehqonchilik va chorvachilik
B) termachilik va ovchilik
C) hunarmandchilik va savdo
D) kulolchilik va to‘quvchilik
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "termachilik va ovchilik o‘zlashtiruvchi xo‘jalik deb ataladi". Ishlab chiqaruvchi xo‘jalik – dehqonchilik va chorvachilik (A).

**46. Teshiktosh g‘ori qayerda joylashgan?**
A) Boysun tog‘larida joylashgan
B) Toshkent vohasida joylashgan
C) Farg‘ona vodiysida joylashgan
D) Boshqirdistondagi Kapova yaqinida
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "Boysun tog‘laridan topilgan Teshiktosh g‘ori...". B - Ko‘lbu loq, C - Selungur, D - Kapova g‘ori.

**47. Teshiktosh g‘ori qaysi davr madaniyatiga oid?**
A) ilk paleolit davriga oid
B) so‘nggi paleolit davriga oid
C) neolit davriga oid
D) o‘rta paleolit davriga oid
* **To‘g‘ri javob:** D
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "Teshiktosh g‘ori o‘rta paleolit davri madaniyatining jahonga mashhur yodgorligi hisoblanadi". B - Kromanyon davri.

**48. Teshiktoshdan topilgan eng mashhur topilma nima?**
A) kromanyon odamning to‘liq suyak qoldiqlari
B) avstralopitekning bosh suyagi qoldiqlari
C) 8–9 yashar neandertal bolaning suyaklari
D) sinantropning jag‘ suyagi qoldiqlari
* **To‘g‘ri javob:** C
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "8-9 yashar neandertal bolaning suyak qoldiqlari eng mashhur topilmadir". A - so‘nggi paleolit, B - Janubiy Afrika, D - Xitoy.

**49. Teshiktoshda jasad atrofiga nima qadab chiqilgan?**
A) yovvoyi otlarning suyaklari
B) tog‘ echkisi shoxlari
C) o‘tkir uchi tosh qurollar
D) rangli toshlardan munchoqlar
* **To‘g‘ri javob:** B
* **Manba:** Eng qadimgi tuzum. Eng qadimgi odamlarning mashg‘ulotlari.
* **Izoh:** "Jasad atrofiga tog‘ echkisi shoxlari qadab chiqilgan". Bu dastlabki diniy e’tiqodlar bilan bog‘liq.

**50. Totemizm nima?**
A) jamoa hayvon yoki o‘simlik bilan qarindoshligiga ishonish
B) insonni o‘rab turgan muhitda jonlar borligiga ishonish
C) turli xil buyumlar omad keltirishiga ishonish
D) olov va quyosh kabi tabiat kuchlariga sig‘inish
* **To‘g‘ri javob:** A
* **Manba:** Eng qadimgi tuzum. Diniy e’tiqodlarning vujudga kelishi.
* **Izoh:** "...biror hayvon yoki o‘simlik turi bilan qarindoshlik aloqasiga ishonish totemizm deb ataladi". B – animizm, C – fetishizm.
"""

class Command(BaseCommand):
    help = "Foydalanuvchining yangilangan va qayta ishlangan 1-50 testlarini (izoh va manbalar bilan) kiritish"

    def handle(self, *args, **options):
        self.stdout.write("Yangilangan testlarni tahlil qilish va bazaga yozish boshlandi...")

        # Parse Blocks
        blocks = re.findall(
            r'\*\*(\d+)\.\s*(.*?)\*\*\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)\n\s*\*\s*\*\*To‘g‘ri javob:\*\*\s*([A-D])\n\s*\*\s*\*\*Manba:\*\*\s*(.*?)\n\s*\*\s*\*\*Izoh:\*\*\s*(.*?)(?=\n\n\*\*|\n\*\*|$)',
            USER_TEXT_V2,
            re.DOTALL
        )

        self.stdout.write(f"Topilgan to'liq va mukammal bloklar soni: {len(blocks)}")

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

            c_hash = hashlib.md5(f"user_q6_v2_{num}_{q_clean}".encode('utf-8')).hexdigest()

            q, created = Question.objects.update_or_create(
                content_hash=c_hash,
                defaults={
                    'topic': topic,
                    'grade': 6,
                    'subject': "Qadimgi dunyo tarixi",
                    'exam_category': exam_cat,
                    'question_type': 'SINGLE_CHOICE',
                    'difficulty': 'MEDIUM',
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

        self.stdout.write(self.style.SUCCESS(f"\nYangilangan {created_count + updated_count} ta testlar izohlari hamda manbalari bilan bazaga saqlandi!"))
