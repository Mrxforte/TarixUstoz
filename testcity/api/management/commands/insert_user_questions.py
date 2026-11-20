import re
import hashlib
from django.core.management.base import BaseCommand
from api.models import Question, PdfBook, Topic, ExamCategory

USER_TEXT = """
1. Qadimgi dunyo tarixi qaysi voqea bilan yakunlanadi?  
A) 395-yilda Rim imperiyasining bo‘linishi  
B) 476-yilda G‘arbiy Rim imperiyasining qulashi  
C) 330-yilda Konstantinopolning poytaxt bo‘lishi  
D) 313-yilda xristianlikning teng din deb e’lon qilinishi  

2. Epigrafika nimani o‘rganadi?  
A) tangalarni  
B) suyak qoldiqlarini  
C) urf-odatlarni  
D) qadimgi bitiklarni  

3. Numizmatika qaysi soha bilan shug‘ullandi?  
A) tangalarni o‘rganish  
B) yozuvlarni o‘rganish  
C) suyaklarni o‘rganish  
D) udumlarni o‘rganish  

4. «Piramidalar matnlari» va «Marhumlar kitobi» qaysi xalq tarixiga oid?  
A) hindlar  
B) shumerlar  
C) misrliklar  
D) O‘rta Osiyo xalqlari  

5. «Rigveda» qaysi xalq tarixidan hikoya qiladi?  
A) misrliklar  
B) hindlar  
C) rimliklar  
D) O‘rta Osiyo xalqlari  

6. «Avesto» qaysi xalq tarixi bo‘yicha manba hisoblanadi?  
A) hindlar  
B) misrliklar  
C) rimliklar  
D) O‘rta Osiyo xalqlari  

7. Qadimgi dunyo tarixi qaysi davrni qamraydi?  
A) odam paydo bo‘lishidan 476-yilda G‘arbiy Rim qulaguniga qadar  
B) 476–1500-yillar  
C) mil. avv. 3000–milodiy 476-yil  
D) faqat yozuv paydo bo‘lishidan keyingi davr  

8. Arxeologlar asosan nima bilan shug‘ullanadilar?  
A) suyaklarni tekshirib tashqi qiyofani tiklaydilar  
B) urf-odatlarni o‘rganadilar  
C) manzilgohlarda qazishma ishlari olib boradilar  
D) tangalarni o‘rganadilar  

9. Antropologlar nimani o‘rganadilar?  
A) qazishma ishlarini  
B) suyak qoldiqlari asosida qadimgi odamning tashqi qiyofasini  
C) xalqlar udumlarini  
D) qadimgi yozuvlarni  

10. Etnograflar nimani o‘rganadilar?  
A) qazishmalarni  
B) tangalarni  
C) suyaklarni  
D) qadimgi udumlar va an’analarni  

11. Moddiy manbalarga nima kiradi?  
A) mehnat qurollari, sopol idishlar, zeb-ziynatlar  
B) «Avesto»  
C) Gerodot asarlari  
D) Behistun yozuvlari  

12. O‘rta Osiyo tarixi bo‘yicha eng qadimgi yozma manba nima?  
A) Behistun yozuvlari  
B) Gerodot «Tarixi»  
C) «Avesto»  
D) Strabon «Geografiyasi»  

13. Behistun qoyalari qayerda joylashgan?  
A) Surxondaryoda  
B) Erondagi Kirmonshoh shahri yaqinida  
C) Misrda Memfis yaqinida  
D) Bobilda  

14. Behistun yozuvlari kimning buyrug‘i bilan o‘yib yozilgan?  
A) Kir II  
B) Kserks  
C) Aleksandr  
D) Doro I  

15. Gerodotning mashhur asari qanday nomlanadi?  
A) «Tarix»  
B) «Geografiya»  
C) «Makedoniyalik Aleksandr tarixi»  
D) «Aleksandr anabasisi»  

16. Gerodot «Tarix» asarini nechta kitobdan iborat qilib yozgan?  
A) 7  
B) 12  
C) 9  
D) 21  

17. Strabon qaysi asarni yozgan?  
A) «Tarix»  
B) «Geografiya»  
C) «Aleksandr tarixi»  
D) «Anabasis»  

18. Kvint Kursiy Ruf qaysi asar muallifi?  
A) «Geografiya»  
B) «Tarix»  
C) «Avesto»  
D) «Makedoniyalik Aleksandr tarixi»  

19. Arrian qaysi asarni yozgan?  
A) «Aleksandrning harbiy yurishlari»  
B) «Tarix»  
C) «Geografiya»  
D) «Makedoniyalik Aleksandr tarixi»  

20. Ibtidoiy jamoa tuzumida mehnat qurollari qanday bo‘lgan?  
A) xususiy  
B) quldorlarniki  
C) umumiy  
D) ibodatxonaniki  

21. Ibtidoiy to‘da nima?  
A) qarindoshlar jamoasi  
B) turmush va mehnat umumiyligi negizida birlashgan eng qadimgi odamlar jamoasi  
C) qabila ittifoqi  
D) shahar-davlat  

22. Urug‘ nima?  
A) qabila  
B) ittifoq  
C) to‘da  
D) birgalikda yashab mehnat qilgan, umumiy qurollarga ega qarindoshlar jamoasi  

23. Dastlabki urug‘ jamoalari kim atrofida jipslashgan?  
A) ona  
B) ota  
C) sardor  
D) kohin  

24. Matriarxat nima?  
A) ota urug‘i davri  
B) qabila ittifoqi  
C) ona urug‘i davri  
D) harbiy demokratiya  

25. Patriarxat nima?  
A) ona urug‘i davri  
B) ota urug‘i davri  
C) shahar-davlat  
D) quldorlik  

26. Tosh davri bosqichlari qaysi qatorda to‘g‘ri berilgan?  
A) mezolit–paleolit–neolit–eneolit  
B) neolit–paleolit–mezolit–eneolit  
C) paleolit–neolit–mezolit–eneolit  
D) paleolit–mezolit–neolit–eneolit  

27. «Paleolit» so‘zi qanday ma’noni bildiradi?  
A) qadimgi tosh  
B) o‘rta tosh  
C) yangi tosh  
D) mis-tosh  

28. «Mezolit» so‘zi qanday ma’noni bildiradi?  
A) qadimgi tosh  
B) yangi tosh  
C) o‘rta tosh  
D) mis-tosh  

29. «Neolit» so‘zi qanday ma’noni bildiradi?  
A) qadimgi tosh  
B) yangi tosh  
C) o‘rta tosh  
D) mis-tosh  

30. «Eneolit» so‘zi qanday ma’noni bildiradi?  
A) yangi tosh  
B) o‘rta tosh  
C) qadimgi tosh  
D) mis-tosh  

31. Ilk paleolit davri qaysi davrga to‘g‘ri keladi?  
A) mil. avv. 100–40-ming yilliklar  
B) mil. avv. 1 mln–100-ming yilliklar  
C) mil. avv. 40–12-ming yilliklar  
D) mil. avv. 12–7-ming yilliklar  

32. O‘rta paleolit davri qaysi davrni qamraydi?  
A) mil. avv. 100–40-ming yilliklar  
B) mil. avv. 1 mln–100-ming yilliklar  
C) mil. avv. 40–12-ming yilliklar  
D) mil. avv. 12–7-ming yilliklar  

33. So‘nggi paleolit davri qaysi davrga to‘g‘ri keladi?  
A) mil. avv. 1 mln–100-ming yilliklar  
B) mil. avv. 100–40-ming yilliklar  
C) mil. avv. 40–12-ming yilliklar  
D) mil. avv. 12–7-ming yilliklar  

34. Mezolit davri qaysi davrni o‘z ichiga oladi?  
A) mil. avv. 6–4-ming yilliklar  
B) mil. avv. 4–3-ming yilliklar  
C) mil. avv. 40–12-ming yilliklar  
D) mil. avv. 12–7-ming yilliklar  

35. O‘rta Osiyoda neolit davri qaysi davrga to‘g‘ri keladi?  
A) mil. avv. 6–4-ming yilliklar  
B) mil. avv. 12–7-ming yilliklar  
C) mil. avv. 4–3-ming yilliklar  
D) mil. avv. 3–2-ming yilliklar  

36. Avstralopitek suyaklari qayerdan topilgan?  
A) Sharqiy Afrika, Zinj vodiysi  
B) Yava oroli  
C) Janubiy Afrika  
D) Xitoy  

37. Zinjantrop qayerdan topilgan?  
A) Janubiy Afrika  
B) Sharqiy Afrika, Zinj vodiysi  
C) Yava oroli  
D) Germaniya  

38. Pitekantrop qayerdan topilgan?  
A) Xitoy  
B) Germaniya  
C) Fransiya  
D) Yava oroli  

39. Sinantrop qayerdan topilgan?  
A) Xitoy  
B) Yava oroli  
C) Janubiy Afrika  
D) Fransiya  

40. Neandertal suyaklari qayerdan topilgan?  
A) Fransiyadagi Kromanyon g‘ori  
B) Xitoy  
C) Germaniya  
D) Yava oroli  

41. Kromanyon suyaklari qayerdan topilgan?  
A) Germaniya  
B) Fransiyadagi Kromanyon g‘ori  
C) Xitoy  
D) Yava oroli  

42. O‘rta Osiyoda toshdan yasalgan qadimiy mehnat qurollari qaysi manzilgohlardan topilgan?  
A) Teshiktosh va Kapova  
B) Altamir va Lasko  
C) Afrosiyob va Yerqo‘rg‘on  
D) Selungur va Ko‘lbu loq  

43. Chopperlar nima?  
A) bir tomoni urib o‘tkirlangan qo‘pol tosh qurollar  
B) o‘q-yoy  
C) mikrolitlar  
D) kulolchilik charxi  

44. Eng qadimgi mashg‘ulotlar qaysilar?  
A) dehqonchilik va chorvachilik  
B) hunarmandchilik  
C) termachilik va ovchilik  
D) savdo  

45. O‘zlashtiruvchi xo‘jalik nima?  
A) dehqonchilik  
B) termachilik va ovchilik  
C) chorvachilik  
D) hunarmandchilik  

46. Teshiktosh g‘ori qayerda joylashgan?  
A) Boysun tog‘larida  
B) Toshkent vohasida  
C) Farg‘ona vodiysida  
D) Boshqirdistonda  

47. Teshiktosh g‘ori qaysi davr madaniyatiga oid?  
A) ilk paleolit  
B) so‘nggi paleolit  
C) neolit  
D) o‘rta paleolit  

48. Teshiktoshdan topilgan eng mashhur topilma nima?  
A) kromanyon odam suyagi  
B) avstralopitek suyagi  
C) 8–9 yashar neandertal bolaning suyak qoldiqlari  
D) sinantrop suyagi  

49. Teshiktoshda jasad atrofiga nima qadab chiqilgan?  
A) ot suyaklari  
B) tog‘ echkisi shoxlari  
C) tosh qurollar  
D) munchoqlar  

50. Totemizm nima?  
A) jamoa hayvon yoki o‘simlik bilan qarindoshligiga ishonish  
B) jonlar va ruhlar mavjudligiga e’tiqod  
C) buyumlar omad keltirishiga e’tiqod  
D) olovga sig‘inish  

51. Animizm nima?  
A) totemizm  
B) fetishizm  
C) jonlar va ruhlar mavjudligiga e’tiqod  
D) olovga sig‘inish  

52. Fetishizm nima?  
A) totemizm  
B) animizm  
C) olovga sig‘inish  
D) buyumlar omad keltirishiga e’tiqod  

53. Eng qadimiy g‘or rasmlari qaysi g‘orlardan topilgan?  
A) Teshiktosh, Selungur, Ko‘lbu loq  
B) Altamir, Lasko, Kapova  
C) Moxenjodaro, Xarappa  
D) Afrosiyob, Yerqo‘rg‘on  

54. Altamir g‘ori qaysi mamlakatda?  
A) Ispaniya  
B) Fransiya  
C) Boshqirdiston  
D) Italiya  

55. Lasko g‘ori qaysi mamlakatda?  
A) Ispaniya  
B) Boshqirdiston  
C) Fransiya  
D) Germaniya  

56. Kapova g‘ori qayerda joylashgan?  
A) Ispaniya  
B) Fransiya  
C) Indoneziya  
D) Boshqirdiston  

57. So‘nggi paleolit davri qaysi davrni qamraydi?  
A) mil. avv. 12–7-ming yilliklar  
B) mil. avv. 40–12-ming yilliklar  
C) mil. avv. 6–4-ming yilliklar  
D) mil. avv. 4–3-ming yilliklar  

58. So‘nggi paleolit davrida qanday odam yashagan?  
A) neandertal  
B) pitekantrop  
C) kromanyon  
D) sinantrop  

59. So‘nggi paleolit davri manzilgohlari O‘zbekiston hududida qayerlardan topilgan?  
A) Ko‘lbu loq, Samarqand hududi, Farg‘ona vodiysi  
B) Teshiktosh, Selungur, Kapova  
C) Afrosiyob, Yerqo‘rg‘on  
D) Qiziltepa, Uzunqir  

60. So‘nggi paleolit davrining muhim ixtirolari qaysilar?  
A) o‘q-yoy, itni qo‘lga o‘rgatish  
B) kulolchilik, to‘quvchilik  
C) g‘ildirak, kulolchilik charxi  
D) kesuvchi va arralovchi qurollar, taqinchoqlar, turar joy, sun’iy olov  

61. Sun’iy olov qanday usulda olingan?  
A) quyosh nurini jamlash orqali  
B) yog‘ochni bir-biriga ishqalash, chaqmoqtoshni urish orqali  
C) vulqon olovidan  
D) faqat o‘choqdan  

62. Mezolit davri qaysi davrni o‘z ichiga oladi?  
A) mil. avv. 12–7-ming yilliklar  
B) mil. avv. 40–12-ming yilliklar  
C) mil. avv. 6–4-ming yilliklar  
D) mil. avv. 4–3-ming yilliklar  

63. Mezolit davrining muhim ixtirosi nima?  
A) kulolchilik  
B) g‘ildirak  
C) o‘q-yoy  
D) yozuv  

64. Mezolit davri oxirida qaysi hayvon qo‘lga o‘rgatilgan?  
A) ot  
B) tuya  
C) fil  
D) it  

65. Mezolit davri oxirida Old Osiyoda qanday yangi tarmoqlar vujudga keldi?  
A) hunarmandchilik va savdo  
B) ibtidoiy ziroatchilik va chorvachilik  
C) metallurgiya  
D) yozuv  

66. O‘zbekiston hududidagi mezolit davriga oid qaysi manzilgohlar ma’lum?  
A) Obishir, Qo‘shilish, Machay, Zarautsoy  
B) Teshiktosh, Selungur  
C) Afrosiyob, Yerqo‘rg‘on  
D) Qiziltepa, Uzunqir  

67. O‘rta Osiyoda neolit davri qaysi davrga to‘g‘ri keladi?  
A) mil. avv. 12–7-ming yilliklar  
B) mil. avv. 4–3-ming yilliklar  
C) mil. avv. 6–4-ming yilliklar  
D) mil. avv. 3–2-ming yilliklar  

68. Neolit davrining eng katta ixtirosi nima?  
A) o‘q-yoy  
B) g‘ildirak  
C) yozuv  
D) dehqonchilik va chorvachilik  

69. Arxeologlar neolit davri boshlanishini nima bilan belgilaydilar?  
A) metall eritish  
B) sopol idishlar yasashning kashf etilishi  
C) yozuv  
D) shahar paydo bo‘lishi  

70. Mikrolitlar nima?  
A) mayda tosh qurollar  
B) yirik tosh qurollar  
C) mis qurollar  
D) bronza qurollar  

71. Ishlab chiqaruvchi xo‘jalik nima?  
A) termachilik va ovchilik  
B) hunarmandchilik  
C) dehqonchilik va chorvachilik  
D) savdo  

72. Neolit davrida qanday uylar qurila boshlangan?  
A) faqat g‘orlar  
B) chodirlar  
C) toshdan  
D) paxsadan  

73. Neolit davrining muhim yangi tarmoqlari qaysilar?  
A) metallurgiya va yozuv  
B) kulolchilik va to‘quvchilik  
C) savdo va dengizchilik  
D) shaharsozlik  

74. Eneolit davri qaysi davrga to‘g‘ri keladi?  
A) mil. avv. 4–3-ming yillikning o‘rtalari  
B) mil. avv. 3–2-ming yilliklar  
C) mil. avv. 6–4-ming yilliklar  
D) mil. avv. 12–7-ming yilliklar  

75. Eneolit nima?  
A) bronza asri  
B) temir asri  
C) mis-tosh asri  
D) yangi tosh davri  

76. Bronza olish uchun misga nima qo‘shilgan?  
A) oltin  
B) kumush  
C) temir  
D) qalay, qo‘rg‘oshin yoki rux  

77. Bronza qachondan asosiy ashyoga aylangan?  
A) mil. avv. 4-ming yillik boshidan  
B) mil. avv. 3-ming yillik o‘rtalaridan  
C) mil. avv. 2-ming yillik oxiridan  
D) mil. avv. 1-ming yillikdan  

78. Patriarxatga o‘tishning asosiy sababi nima?  
A) erkaklar urug‘ni mahsulot bilan ta’minlay boshlashi  
B) ayollarning uy ishlari bilan shug‘ullanishi  
C) savdoning rivojlanishi  
D) urushlarning ko‘payishi  

79. Patriarxal oila nima?  
A) ona tomonidan qarindoshlar oilasi  
B) qabila  
C) ota tomonidan yaqin qarindoshlarning bir necha avlodlaridan tashkil topgan oila  
D) shahar-davlat  

80. Bronza davri manzilgohlari qaysilar?  
A) Teshiktosh, Selungur  
B) Afrosiyob, Yerqo‘rg‘on  
C) Qiziltepa, Uzunqir  
D) Zamonbobo, Sopollitepa, Jarqo‘ton  

81. Zamonbobo manzilgohi qayerdan topilgan?  
A) Surxondaryo, Muzrabod tumani  
B) Buxoro viloyati, Qorako‘l tumani  
C) Toshkent viloyati, Ohangaron  
D) Farg‘ona vodiysi  

82. Sopollitepa manzilgohi qayerda joylashgan?  
A) Surxondaryo, Muzrabod tumani  
B) Buxoro, Qorako‘l tumani  
C) Toshkent vohasi  
D) Xorazm vohasi  

83. Jarqo‘ton manzilgohi qayerda joylashgan?  
A) Buxoro vohasida  
B) Toshkent vohasida  
C) Surxon vohasida, Sherobod yaqinida  
D) Farg‘ona vodiysida  

84. Jarqo‘tonda ilk shahar alomatlari nimada ko‘zga tashlanadi?  
A) faqat ibodatxona bo‘lgan  
B) faqat bozor bo‘lgan  
C) faqat qal’a bo‘lgan  
D) qal’a va uning atrofida hunarmand va dehqonlar uylari bo‘lgan  

85. Bronza davrida qanday muhim kashfiyotlar qilingan?  
A) o‘q-yoy  
B) kulolchilik charxi va g‘ildirak  
C) yozuv  
D) kompas  

86. Sopollitepadan nimalar topilgan?  
A) xom g‘ishtdan qurilgan uylar, sopol buyumlar, bronza ustaxonalari, bug‘doy va arpa doni  
B) faqat tangalar  
C) faqat qurollar  
D) faqat haykallar  

87. Temir qanday haroratda eriydi?  
A) 1000°C  
B) 500°C  
C) 1500°C  
D) 2000°C  

88. Temirdan birinchi bo‘lib kimlar foydalangan?  
A) misrliklar  
B) shumerlar  
C) yunonlar  
D) Kichik Osiyodagi xettlar  

89. Xettlar temirdan qachon foydalana boshlagan?  
A) mil. avv. XIV–XIII asrlar  
B) mil. avv. IX–VIII asrlar  
C) mil. avv. VI–V asrlar  
D) mil. avv. III–II asrlar  

90. O‘rta Osiyoda temirdan yasalgan ilk mehnat qurollari qachon topilgan?  
A) mil. avv. XIV–XIII asrlar  
B) mil. avv. IX–VIII asrlar  
C) mil. avv. VI–V asrlar  
D) mil. avv. III–II asrlar  

91. Harbiy demokratiya nima?  
A) quldorlik tuzumi  
B) monarxiya  
C) respublika  
D) ilk davlatchilikka o‘tish davrida saylab qo‘yilgan harbiy sardorlar boshchiligidagi boshqaruv  

92. Temir asri boshida O‘rta Osiyo aholisi nechta guruhga bo‘lingan?  
A) 4 ta: kohinlar, jangchilar, dehqonlar, hunarmandlar  
B) 3 ta  
C) 2 ta  
D) 5 ta  

93. «Avesto»da katta patriarxal oila qanday nomlangan?  
A) vis  
B) varzana  
C) nmana  
D) zantu  

94. «Vis» nima?  
A) nmana  
B) bir necha nmanalardan tashkil topgan urug‘ jamoasi  
C) varzana  
D) dax’yu  

95. «Varzana» nima?  
A) vis  
B) zantu  
C) nmana  
D) hududiy qo‘shnichilik jamoasi  

96. «Zantu» nima?  
A) qabila  
B) vis  
C) varzana  
D) dax’yu  

97. «Dax’yu» nima?  
A) zantu  
B) varzana  
C) qabilalar ittifoqi  
D) nmana  

98. Ijtimoiy tuzum taraqqiyoti qaysi qatorda to‘g‘ri berilgan?  
A) urug‘ → to‘da → qabila  
B) ibtidoiy to‘da → urug‘ → qabila → qabilalar ittifoqi  
C) qabila → urug‘ → to‘da  
D) shahar → qabila  

Javoblar kaliti
1-B, 2-D, 3-A, 4-C, 5-B, 6-D, 7-A, 8-C, 9-B, 10-D, 11-A, 12-C, 13-B, 14-D, 15-A, 16-C, 17-B, 18-D, 19-A, 20-C, 21-B, 22-D, 23-A, 24-C, 25-B, 26-D, 27-A, 28-C, 29-B, 30-D, 31-B, 32-A, 33-C, 34-D, 35-A, 36-C, 37-B, 38-D, 39-A, 40-C, 41-B, 42-D, 43-A, 44-C, 45-B, 46-A, 47-D, 48-C, 49-B, 50-A, 51-C, 52-D, 53-B, 54-A, 55-C, 56-D, 57-B, 58-C, 59-A, 60-D, 61-B, 62-A, 63-C, 64-D, 65-B, 66-A, 67-C, 68-D, 69-B, 70-A, 71-C, 72-D, 73-B, 74-A, 75-C, 76-D, 77-B, 78-A, 79-C, 80-D, 81-B, 82-A, 83-C, 84-D, 85-B, 86-A, 87-C, 88-D, 89-A, 90-B, 91-D, 92-A, 93-C, 94-B, 95-D, 96-A, 97-C, 98-B
"""

class Command(BaseCommand):
    help = "Foydalanuvchi tomonidan taqdim etilgan 98 ta rasmiy 6-sinf testlarini bazaga kiritish"

    def handle(self, *args, **options):
        self.stdout.write("6-sinf testlarini tahlil qilish va bazaga joylashtirish boshlandi...")

        # Parse Answer Key
        key_match = re.search(r'Javoblar kaliti\s*\n(.*)', USER_TEXT, re.DOTALL)
        if not key_match:
            self.stderr.write("Javoblar kaliti topilmadi!")
            return

        key_text = key_match.group(1)
        answers = {}
        for pair in re.findall(r'(\d+)\s*-\s*([A-D])', key_text):
            answers[int(pair[0])] = pair[1].upper()

        self.stdout.write(f"Topilgan kalitlar soni: {len(answers)}")

        # Parse Questions
        q_blocks = re.findall(r'(\d+)\.\s*(.*?)\n\s*A\)\s*(.*?)\n\s*B\)\s*(.*?)\n\s*C\)\s*(.*?)\n\s*D\)\s*(.*?)(?=\n\s*\d+\.|\n\s*Javoblar kaliti|$)', USER_TEXT, re.DOTALL)

        self.stdout.write(f"Topilgan savollar soni: {len(q_blocks)}")

        # Fetch or Create Grade 6 Book & Topic
        book = PdfBook.objects.filter(grade=6).first()
        topic = Topic.objects.filter(document=book).first() if book else None
        exam_cat = ExamCategory.objects.filter(code="PRACTICE").first()

        created_count = 0
        updated_count = 0

        for num_str, q_text, opt_a, opt_b, opt_c, opt_d in q_blocks:
            num = int(num_str)
            correct = answers.get(num, "A")

            q_clean = q_text.strip()
            a_clean = opt_a.strip()
            b_clean = opt_b.strip()
            c_clean = opt_c.strip()
            d_clean = opt_d.strip()

            c_hash = hashlib.md5(f"user_q6_{num}_{q_clean}".encode('utf-8')).hexdigest()

            q, created = Question.objects.get_or_create(
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
                    'correct_option': correct,
                    'explanation': f"6-sinf Qadimgi dunyo tarixi (1-5-§ rasmiy test). To'g'ri javob: {correct} varianti.",
                    'short_explanation': "6-sinf Tarix 1-5-§",
                    'source_citation': "6-sinf Qadimgi dunyo tarixi 1-5-§",
                    'verification_status': 'VERIFIED',
                    'is_official_sample': True,
                    'is_generated_practice': False
                }
            )

            if created:
                created_count += 1
            else:
                updated_count += 1

        self.stdout.write(self.style.SUCCESS(f"\nMuvaffaqiyatli saqlandi! {created_count} ta yangi test bazaga qo'shildi."))
