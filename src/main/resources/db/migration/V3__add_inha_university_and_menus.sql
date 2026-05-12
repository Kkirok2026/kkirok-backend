insert into universities (university_id, university_code, university_name) values
    (2, 'INHA', '인하대학교');

insert into university_email_domains (domain_id, university_id, email_domain, verification_method) values
    (3, 2, 'inha.edu', 'SCHOOL_EMAIL'),
    (4, 2, 'inha.ac.kr', 'SCHOOL_EMAIL');

insert into dining_place (dining_place_id, university_id, dining_place_name, dining_place_type, menu_source_url) values
    (3, 2, '인하대학교 학생식당', 'STUDENT', 'https://www.inha.ac.kr/kr/1072/subview.do'),
    (4, 2, '인하대학교 생활관식당', 'DORMITORY', 'codex_data/인하대 생활관 식단/5월 식단.pdf');

insert into menu_category (category_id, category_code, category_name, sort_order) values
    (10, 'STUDENT_CRAWLED', '크롤링 메뉴', 10),
    (11, 'DORM_A', '생활관 A', 11),
    (12, 'DORM_B', '생활관 B', 12),
    (13, 'DORM_SIMPLE', '생활관 간편식', 13),
    (14, 'DORM_RAMEN', '생활관 식간 라면', 14),
    (15, 'DORM_DESSERT', '생활관 후식', 15);

insert into cafeteria_menu (menu_id, dining_place_id, meal_type_id, served_date) values
    (1001, 4, 1, date '2026-05-11'),
    (1002, 4, 1, date '2026-05-12'),
    (1003, 4, 1, date '2026-05-13'),
    (1004, 4, 1, date '2026-05-14'),
    (1005, 4, 1, date '2026-05-15'),
    (1011, 4, 2, date '2026-05-11'),
    (1012, 4, 2, date '2026-05-12'),
    (1013, 4, 2, date '2026-05-13'),
    (1014, 4, 2, date '2026-05-14'),
    (1015, 4, 2, date '2026-05-15'),
    (1016, 4, 2, date '2026-05-16'),
    (1017, 4, 2, date '2026-05-17'),
    (1021, 4, 3, date '2026-05-11'),
    (1022, 4, 3, date '2026-05-12'),
    (1023, 4, 3, date '2026-05-13'),
    (1024, 4, 3, date '2026-05-14'),
    (1025, 4, 3, date '2026-05-15'),
    (1026, 4, 3, date '2026-05-16'),
    (1027, 4, 3, date '2026-05-17'),
    (1031, 4, 4, date '2026-05-11'),
    (1032, 4, 4, date '2026-05-12'),
    (1033, 4, 4, date '2026-05-13'),
    (1034, 4, 4, date '2026-05-14'),
    (1035, 4, 4, date '2026-05-15');

insert into food (food_id, source_name, source_food_code, food_name, default_serving_g, source_category) values
    (2001, 'INHA_DORM_MENU', 'INHA_DORM_20260511_BREAKFAST_A', '버섯된장찌개 / 쌀밥 / 생선까스*타르S / 마늘종햄볶음 / 깍두기', 100.00, '생활관 아침 A'),
    (2002, 'INHA_DORM_MENU', 'INHA_DORM_20260511_BREAKFAST_DESSERT', '망고주스', 100.00, '생활관 아침 후식'),
    (2003, 'INHA_DORM_MENU', 'INHA_DORM_20260511_BREAKFAST_SIMPLE', '샌드위치&음료', 100.00, '생활관 아침 간편식'),
    (2004, 'INHA_DORM_MENU', 'INHA_DORM_20260512_BREAKFAST_A', '모닝브래드2종*잼 / 계란후라이 / 계절과일 / 그린샐러드*드레싱 / 시리얼*우유', 100.00, '생활관 아침 A'),
    (2005, 'INHA_DORM_MENU', 'INHA_DORM_20260512_BREAKFAST_DESSERT', '블랙커피', 100.00, '생활관 아침 후식'),
    (2006, 'INHA_DORM_MENU', 'INHA_DORM_20260512_BREAKFAST_SIMPLE', '샌드위치&음료', 100.00, '생활관 아침 간편식'),
    (2007, 'INHA_DORM_MENU', 'INHA_DORM_20260513_BREAKFAST_A', '참치야채볶음밥 / 김치두부국 / 미니돈까스*케찹 / 찐빵 / 깍두기', 100.00, '생활관 아침 A'),
    (2008, 'INHA_DORM_MENU', 'INHA_DORM_20260513_BREAKFAST_DESSERT', '메밀차', 100.00, '생활관 아침 후식'),
    (2009, 'INHA_DORM_MENU', 'INHA_DORM_20260513_BREAKFAST_SIMPLE', '샌드위치&음료', 100.00, '생활관 아침 간편식'),
    (2010, 'INHA_DORM_MENU', 'INHA_DORM_20260514_BREAKFAST_A', '모닝브래드2종*잼 / 계란후라이 / 계절과일 / 그린샐러드*드레싱 / 시리얼*우유', 100.00, '생활관 아침 A'),
    (2011, 'INHA_DORM_MENU', 'INHA_DORM_20260514_BREAKFAST_DESSERT', '블랙커피', 100.00, '생활관 아침 후식'),
    (2012, 'INHA_DORM_MENU', 'INHA_DORM_20260514_BREAKFAST_SIMPLE', '샌드위치&음료', 100.00, '생활관 아침 간편식'),
    (2013, 'INHA_DORM_MENU', 'INHA_DORM_20260515_BREAKFAST_A', '사골우거지탕 / 쌀밥 / 옛날소세지전*케찹 / 연두부*양념장 / 고추지무침 / 깍두기', 100.00, '생활관 아침 A'),
    (2014, 'INHA_DORM_MENU', 'INHA_DORM_20260515_BREAKFAST_DESSERT', '복분자차', 100.00, '생활관 아침 후식'),
    (2015, 'INHA_DORM_MENU', 'INHA_DORM_20260515_BREAKFAST_SIMPLE', '샌드위치&음료', 100.00, '생활관 아침 간편식'),
    (2016, 'INHA_DORM_MENU', 'INHA_DORM_20260511_SNACK_RAMEN', '라면 / 쌀밥 / 김치', 100.00, '생활관 식간 라면'),
    (2017, 'INHA_DORM_MENU', 'INHA_DORM_20260512_SNACK_RAMEN', '라면 / 쌀밥 / 김치', 100.00, '생활관 식간 라면'),
    (2018, 'INHA_DORM_MENU', 'INHA_DORM_20260513_SNACK_RAMEN', '라면 / 쌀밥 / 김치', 100.00, '생활관 식간 라면'),
    (2019, 'INHA_DORM_MENU', 'INHA_DORM_20260514_SNACK_RAMEN', '라면 / 쌀밥 / 김치', 100.00, '생활관 식간 라면'),
    (2020, 'INHA_DORM_MENU', 'INHA_DORM_20260515_SNACK_RAMEN', '라면 / 쌀밥 / 김치', 100.00, '생활관 식간 라면'),
    (2021, 'INHA_DORM_MENU', 'INHA_DORM_20260511_LUNCH_A', '뚝)버섯만두전골 / 쌀밥 / 너비아니&파채 / 도토리묵야채무침 / 무말랭이무침', 100.00, '생활관 점심 A'),
    (2022, 'INHA_DORM_MENU', 'INHA_DORM_20260511_LUNCH_B', '김치볶음밥&참치마요 / 맑은우동국 / 사과파이 / 무말랭이무침', 100.00, '생활관 점심 B'),
    (2023, 'INHA_DORM_MENU', 'INHA_DORM_20260512_LUNCH_A', '콩나물불고기 / 쌀밥 / 근대된장국 / 볼어묵곤약조림 / 치커리생채 / 배추김치', 100.00, '생활관 점심 A'),
    (2024, 'INHA_DORM_MENU', 'INHA_DORM_20260512_LUNCH_B', '가쓰오우동 / 후리가케밥 / 야채멘치까스 / 오복지무침', 100.00, '생활관 점심 B'),
    (2025, 'INHA_DORM_MENU', 'INHA_DORM_20260513_LUNCH_A', '마파두부덮밥 / 부추달걀국 / 탕수육 / 불향숙주볶음 / 배추김치', 100.00, '생활관 점심 A'),
    (2026, 'INHA_DORM_MENU', 'INHA_DORM_20260514_LUNCH_A', '굴소스오징어볶음 / 쌀밥 / 미역국 / 감자채볶음 / 양념깻잎지 / 깍두기', 100.00, '생활관 점심 A'),
    (2027, 'INHA_DORM_MENU', 'INHA_DORM_20260514_LUNCH_B', '치즈부대덮밥 / 미역국 / 설탕꽈배기 / 단무지', 100.00, '생활관 점심 B'),
    (2028, 'INHA_DORM_MENU', 'INHA_DORM_20260515_LUNCH_A', '하이라이스덮밥&함박스테이크 / 유부장국 / 샐러드파스타 / 깍두기', 100.00, '생활관 점심 A'),
    (2029, 'INHA_DORM_MENU', 'INHA_DORM_20260516_LUNCH_A', '나시고랭볶음밥 / 사골대파국 / 오징어바튀김*머스타드S / 파김치', 100.00, '생활관 점심 A'),
    (2030, 'INHA_DORM_MENU', 'INHA_DORM_20260516_LUNCH_SIMPLE', '참치샐러드 / 음료', 100.00, '생활관 점심 간편식'),
    (2031, 'INHA_DORM_MENU', 'INHA_DORM_20260517_LUNCH_A', '참치캔야채비빔밥 / 두부장국 / 핫도그*케찹 / 깍두기', 100.00, '생활관 점심 A'),
    (2032, 'INHA_DORM_MENU', 'INHA_DORM_20260517_LUNCH_SIMPLE', '미니돈까스샐러드 / 음료', 100.00, '생활관 점심 간편식'),
    (2033, 'INHA_DORM_MENU', 'INHA_DORM_20260511_DINNER_A', '유부가락국수 / 후리가케밥 / 갈비만두찜 / 배추김치', 100.00, '생활관 저녁 A'),
    (2034, 'INHA_DORM_MENU', 'INHA_DORM_20260511_DINNER_DESSERT', '식혜', 100.00, '생활관 저녁 후식'),
    (2035, 'INHA_DORM_MENU', 'INHA_DORM_20260511_DINNER_SIMPLE', '바질닭가슴살샐러드 / 음료', 100.00, '생활관 저녁 간편식'),
    (2036, 'INHA_DORM_MENU', 'INHA_DORM_20260512_DINNER_A', '훈제오리볶음밥 / 맑은우동국 / 해물까스*칠리S / 깍두기', 100.00, '생활관 저녁 A'),
    (2037, 'INHA_DORM_MENU', 'INHA_DORM_20260512_DINNER_DESSERT', '요구르트', 100.00, '생활관 저녁 후식'),
    (2038, 'INHA_DORM_MENU', 'INHA_DORM_20260512_DINNER_SIMPLE', '포테이토샌드위치 / 음료', 100.00, '생활관 저녁 간편식'),
    (2039, 'INHA_DORM_MENU', 'INHA_DORM_20260513_DINNER_A', '춘천닭갈비 / 쌀밥 / 콩나물국 / 들기름메밀면무침 / 마늘종어묵볶음 / 배추김치', 100.00, '생활관 저녁 A'),
    (2040, 'INHA_DORM_MENU', 'INHA_DORM_20260513_DINNER_DESSERT', '아이스초코', 100.00, '생활관 저녁 후식'),
    (2041, 'INHA_DORM_MENU', 'INHA_DORM_20260513_DINNER_SIMPLE', '구운버섯샐러드 / 음료', 100.00, '생활관 저녁 간편식'),
    (2042, 'INHA_DORM_MENU', 'INHA_DORM_20260514_DINNER_A', '쇠고기콩나물밥 / 시금치고추장국 / 동그랑땡전*케찹 / 우엉채멸치볶음 / 부추생채 / 깍두기', 100.00, '생활관 저녁 A'),
    (2043, 'INHA_DORM_MENU', 'INHA_DORM_20260514_DINNER_DESSERT', '복숭아아이스티', 100.00, '생활관 저녁 후식'),
    (2044, 'INHA_DORM_MENU', 'INHA_DORM_20260514_DINNER_SIMPLE', '치킨텐더샐러드 / 음료', 100.00, '생활관 저녁 간편식'),
    (2045, 'INHA_DORM_MENU', 'INHA_DORM_20260515_DINNER_A', '삼치데리야끼구이 / 쌀밥 / 얼큰무채국 / 비엔나떡강정 / 콩자반 / 배추김치', 100.00, '생활관 저녁 A'),
    (2046, 'INHA_DORM_MENU', 'INHA_DORM_20260515_DINNER_DESSERT', '보리차', 100.00, '생활관 저녁 후식'),
    (2047, 'INHA_DORM_MENU', 'INHA_DORM_20260515_DINNER_SIMPLE', '대파크림치즈베이글 / 음료', 100.00, '생활관 저녁 간편식'),
    (2048, 'INHA_DORM_MENU', 'INHA_DORM_20260516_DINNER_A', '닭살간장조림 / 쌀밥 / 얼무된장국 / 브로콜리맛살볶음 / 콩나물무침 / 깍두기', 100.00, '생활관 저녁 A'),
    (2049, 'INHA_DORM_MENU', 'INHA_DORM_20260517_DINNER_A', '돈육김치찌개 / 쌀밥 / 떡산적조림 / 명엽채조림 / 도시락김 / 파김치', 100.00, '생활관 저녁 A');

insert into food_nutrient_value (food_id, nutrient_id, amount_per_100g)
select menu_food.food_id,
       n.nutrient_id,
       case when n.nutrient_code = 'CALORIES_KCAL' then menu_food.calories_kcal else 0.0000 end
from (
    select 2001 as food_id, 881.0000 as calories_kcal union all
    select 2002, 0.0000 union all
    select 2003, 1027.0000 union all
    select 2004, 796.0000 union all
    select 2005, 0.0000 union all
    select 2006, 1016.0000 union all
    select 2007, 980.0000 union all
    select 2008, 0.0000 union all
    select 2009, 780.0000 union all
    select 2010, 796.0000 union all
    select 2011, 0.0000 union all
    select 2012, 375.0000 union all
    select 2013, 675.0000 union all
    select 2014, 0.0000 union all
    select 2015, 359.0000 union all
    select 2016, 1088.0000 union all
    select 2017, 1088.0000 union all
    select 2018, 1088.0000 union all
    select 2019, 1088.0000 union all
    select 2020, 1088.0000 union all
    select 2021, 918.0000 union all
    select 2022, 978.0000 union all
    select 2023, 1013.0000 union all
    select 2024, 919.0000 union all
    select 2025, 1021.0000 union all
    select 2026, 881.0000 union all
    select 2027, 956.0000 union all
    select 2028, 888.0000 union all
    select 2029, 669.0000 union all
    select 2030, 673.0000 union all
    select 2031, 455.0000 union all
    select 2032, 628.0000 union all
    select 2033, 741.0000 union all
    select 2034, 0.0000 union all
    select 2035, 334.0000 union all
    select 2036, 995.0000 union all
    select 2037, 0.0000 union all
    select 2038, 608.0000 union all
    select 2039, 1081.0000 union all
    select 2040, 0.0000 union all
    select 2041, 268.0000 union all
    select 2042, 828.0000 union all
    select 2043, 0.0000 union all
    select 2044, 407.0000 union all
    select 2045, 1169.0000 union all
    select 2046, 0.0000 union all
    select 2047, 513.0000 union all
    select 2048, 870.0000 union all
    select 2049, 487.0000
) menu_food
join nutrient n on n.nutrient_code in ('CALORIES_KCAL', 'CARB_G', 'PROTEIN_G', 'FAT_G', 'SUGAR_G', 'SODIUM_MG');

insert into cafeteria_menu_option (option_id, menu_id, category_id, option_name, source_label, price) values
    (2001, 1001, 11, '버섯된장찌개 / 쌀밥 / 생선까스*타르S / 마늘종햄볶음 / 깍두기', '2026-05-11 생활관 아침 A', null),
    (2002, 1001, 15, '망고주스', '2026-05-11 생활관 아침 후식', null),
    (2003, 1001, 13, '샌드위치&음료', '2026-05-11 생활관 아침 간편식', null),
    (2004, 1002, 11, '모닝브래드2종*잼 / 계란후라이 / 계절과일 / 그린샐러드*드레싱 / 시리얼*우유', '2026-05-12 생활관 아침 A', null),
    (2005, 1002, 15, '블랙커피', '2026-05-12 생활관 아침 후식', null),
    (2006, 1002, 13, '샌드위치&음료', '2026-05-12 생활관 아침 간편식', null),
    (2007, 1003, 11, '참치야채볶음밥 / 김치두부국 / 미니돈까스*케찹 / 찐빵 / 깍두기', '2026-05-13 생활관 아침 A', null),
    (2008, 1003, 15, '메밀차', '2026-05-13 생활관 아침 후식', null),
    (2009, 1003, 13, '샌드위치&음료', '2026-05-13 생활관 아침 간편식', null),
    (2010, 1004, 11, '모닝브래드2종*잼 / 계란후라이 / 계절과일 / 그린샐러드*드레싱 / 시리얼*우유', '2026-05-14 생활관 아침 A', null),
    (2011, 1004, 15, '블랙커피', '2026-05-14 생활관 아침 후식', null),
    (2012, 1004, 13, '샌드위치&음료', '2026-05-14 생활관 아침 간편식', null),
    (2013, 1005, 11, '사골우거지탕 / 쌀밥 / 옛날소세지전*케찹 / 연두부*양념장 / 고추지무침 / 깍두기', '2026-05-15 생활관 아침 A', null),
    (2014, 1005, 15, '복분자차', '2026-05-15 생활관 아침 후식', null),
    (2015, 1005, 13, '샌드위치&음료', '2026-05-15 생활관 아침 간편식', null),
    (2016, 1031, 14, '라면 / 쌀밥 / 김치', '2026-05-11 생활관 식간 라면 1088~1350kcal', null),
    (2017, 1032, 14, '라면 / 쌀밥 / 김치', '2026-05-12 생활관 식간 라면 1088~1350kcal', null),
    (2018, 1033, 14, '라면 / 쌀밥 / 김치', '2026-05-13 생활관 식간 라면 1088~1350kcal', null),
    (2019, 1034, 14, '라면 / 쌀밥 / 김치', '2026-05-14 생활관 식간 라면 1088~1350kcal', null),
    (2020, 1035, 14, '라면 / 쌀밥 / 김치', '2026-05-15 생활관 식간 라면 1088~1350kcal', null),
    (2021, 1011, 11, '뚝)버섯만두전골 / 쌀밥 / 너비아니&파채 / 도토리묵야채무침 / 무말랭이무침', '2026-05-11 생활관 점심 A', null),
    (2022, 1011, 12, '김치볶음밥&참치마요 / 맑은우동국 / 사과파이 / 무말랭이무침', '2026-05-11 생활관 점심 B', null),
    (2023, 1012, 11, '콩나물불고기 / 쌀밥 / 근대된장국 / 볼어묵곤약조림 / 치커리생채 / 배추김치', '2026-05-12 생활관 점심 A', null),
    (2024, 1012, 12, '가쓰오우동 / 후리가케밥 / 야채멘치까스 / 오복지무침', '2026-05-12 생활관 점심 B', null),
    (2025, 1013, 11, '마파두부덮밥 / 부추달걀국 / 탕수육 / 불향숙주볶음 / 배추김치', '2026-05-13 생활관 점심 A', null),
    (2026, 1014, 11, '굴소스오징어볶음 / 쌀밥 / 미역국 / 감자채볶음 / 양념깻잎지 / 깍두기', '2026-05-14 생활관 점심 A', null),
    (2027, 1014, 12, '치즈부대덮밥 / 미역국 / 설탕꽈배기 / 단무지', '2026-05-14 생활관 점심 B', null),
    (2028, 1015, 11, '하이라이스덮밥&함박스테이크 / 유부장국 / 샐러드파스타 / 깍두기', '2026-05-15 생활관 점심 A', null),
    (2029, 1016, 11, '나시고랭볶음밥 / 사골대파국 / 오징어바튀김*머스타드S / 파김치', '2026-05-16 생활관 점심 A', null),
    (2030, 1016, 13, '참치샐러드 / 음료', '2026-05-16 생활관 점심 간편식', null),
    (2031, 1017, 11, '참치캔야채비빔밥 / 두부장국 / 핫도그*케찹 / 깍두기', '2026-05-17 생활관 점심 A', null),
    (2032, 1017, 13, '미니돈까스샐러드 / 음료', '2026-05-17 생활관 점심 간편식', null),
    (2033, 1021, 11, '유부가락국수 / 후리가케밥 / 갈비만두찜 / 배추김치', '2026-05-11 생활관 저녁 A', null),
    (2034, 1021, 15, '식혜', '2026-05-11 생활관 저녁 후식', null),
    (2035, 1021, 13, '바질닭가슴살샐러드 / 음료', '2026-05-11 생활관 저녁 간편식', null),
    (2036, 1022, 11, '훈제오리볶음밥 / 맑은우동국 / 해물까스*칠리S / 깍두기', '2026-05-12 생활관 저녁 A', null),
    (2037, 1022, 15, '요구르트', '2026-05-12 생활관 저녁 후식', null),
    (2038, 1022, 13, '포테이토샌드위치 / 음료', '2026-05-12 생활관 저녁 간편식', null),
    (2039, 1023, 11, '춘천닭갈비 / 쌀밥 / 콩나물국 / 들기름메밀면무침 / 마늘종어묵볶음 / 배추김치', '2026-05-13 생활관 저녁 A', null),
    (2040, 1023, 15, '아이스초코', '2026-05-13 생활관 저녁 후식', null),
    (2041, 1023, 13, '구운버섯샐러드 / 음료', '2026-05-13 생활관 저녁 간편식', null),
    (2042, 1024, 11, '쇠고기콩나물밥 / 시금치고추장국 / 동그랑땡전*케찹 / 우엉채멸치볶음 / 부추생채 / 깍두기', '2026-05-14 생활관 저녁 A', null),
    (2043, 1024, 15, '복숭아아이스티', '2026-05-14 생활관 저녁 후식', null),
    (2044, 1024, 13, '치킨텐더샐러드 / 음료', '2026-05-14 생활관 저녁 간편식', null),
    (2045, 1025, 11, '삼치데리야끼구이 / 쌀밥 / 얼큰무채국 / 비엔나떡강정 / 콩자반 / 배추김치', '2026-05-15 생활관 저녁 A', null),
    (2046, 1025, 15, '보리차', '2026-05-15 생활관 저녁 후식', null),
    (2047, 1025, 13, '대파크림치즈베이글 / 음료', '2026-05-15 생활관 저녁 간편식', null),
    (2048, 1026, 11, '닭살간장조림 / 쌀밥 / 얼무된장국 / 브로콜리맛살볶음 / 콩나물무침 / 깍두기', '2026-05-16 생활관 저녁 A', null),
    (2049, 1027, 11, '돈육김치찌개 / 쌀밥 / 떡산적조림 / 명엽채조림 / 도시락김 / 파김치', '2026-05-17 생활관 저녁 A', null);

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
select option_id, option_id, option_name, 100.00
from cafeteria_menu_option
where option_id between 2001 and 2049;
