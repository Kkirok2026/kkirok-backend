drop table if exists tmp_inha_dorm_week4_seed;

create table tmp_inha_dorm_week4_seed (
    served_date date not null,
    meal_type varchar(30) not null,
    category_code varchar(50) not null,
    option_name varchar(255) not null,
    source_label varchar(255) not null,
    calories_kcal decimal(10,2) null
);

insert into tmp_inha_dorm_week4_seed (served_date, meal_type, category_code, option_name, source_label, calories_kcal) values
    ('2026-05-25', 'LUNCH', 'DORM_A', '소고기야채볶음밥 / 맑은우동국 / 깐풍교자만두 / 깍두기', '2026-05-25 생활관 점심 A', 1017.00),
    ('2026-05-25', 'LUNCH', 'DORM_B', '새우튀김샐러드 / 음료', '2026-05-25 생활관 점심 간편식', 375.00),
    ('2026-05-25', 'DINNER', 'DORM_A', '황태해장국 / 쌀밥 / 동그랑땡전*케찹 / 마늘종햄볶음 / 궁채장아찌 / 깍두기', '2026-05-25 생활관 저녁 A', 816.00),
    ('2026-05-26', 'LUNCH', 'DORM_A', '쫄면순두부찌개 / 쌀밥 / 갈릭마요미트볼 / 새송이버섯볶음 / 깍두기', '2026-05-26 생활관 점심 A', 1042.00),
    ('2026-05-26', 'LUNCH', 'DORM_B', '나폴리탄파스타 / 크림스프 / 추가밥 / 수제마늘빵 / 오이피클', '2026-05-26 생활관 점심 B', 1379.00),
    ('2026-05-26', 'DINNER', 'DORM_A', '사골떡만두국 / 추가밥 / 너비아니&파채 / 쥐어채볶음 / 무말랭이무침 / 배추김치', '2026-05-26 생활관 저녁 A', 904.00),
    ('2026-05-26', 'DINNER', 'DORM_DESSERT', '식혜', '2026-05-26 생활관 저녁 후식', 0.00),
    ('2026-05-26', 'DINNER', 'DORM_SIMPLE', '베이컨시저샐러드 / 음료', '2026-05-26 생활관 저녁 간편식', 512.00),
    ('2026-05-27', 'LUNCH', 'DORM_A', '살얼음냉모밀국수 / 멘치까스*데미s / 후리가케밥 / 오복지무침', '2026-05-27 생활관 점심 A', 998.00),
    ('2026-05-27', 'DINNER', 'DORM_A', '카레볶음밥 / &청양소세지구이 / 두부미소국 / 쫄면야채무침 / 깍두기', '2026-05-27 생활관 저녁 A', 1012.00),
    ('2026-05-27', 'DINNER', 'DORM_DESSERT', '보리차', '2026-05-27 생활관 저녁 후식', 0.00),
    ('2026-05-27', 'DINNER', 'DORM_SIMPLE', '해쉬브라운머핀 / 음료', '2026-05-27 생활관 저녁 간편식', 449.00),
    ('2026-05-28', 'LUNCH', 'DORM_A', '로제소스닭갈비 / 쌀밥 / 미역국 / 사각어묵볶음 / 부추생채 / 배추김치', '2026-05-28 생활관 점심 A', 939.00),
    ('2026-05-28', 'LUNCH', 'DORM_B', '나가사끼짬뽕 / 추가밥 / 치킨너겟*머스타드s / 단무지무침', '2026-05-28 생활관 점심 B', 978.00),
    ('2026-05-28', 'DINNER', 'DORM_A', '참치마요덮밥 / 꼬치어묵국 / 국물떡볶이 / 배추김치', '2026-05-28 생활관 저녁 A', 1077.00),
    ('2026-05-28', 'DINNER', 'DORM_DESSERT', '매실차', '2026-05-28 생활관 저녁 후식', 0.00),
    ('2026-05-28', 'DINNER', 'DORM_SIMPLE', '두부포케샐러드 / 음료', '2026-05-28 생활관 저녁 간편식', 349.00),
    ('2026-05-29', 'LUNCH', 'DORM_A', '돌솥비빔밥 / 유부장국 / 청포묵무침 / 설탕꽈배기 / 배추김치', '2026-05-29 생활관 점심 A', 653.00),
    ('2026-05-29', 'DINNER', 'DORM_A', '묵은지닭조림 / 근대고추장국 / 쌀밥 / 푸실리모듬햄볶음 / 콩나물무침 / 깍두기', '2026-05-29 생활관 저녁 A', 908.00),
    ('2026-05-29', 'DINNER', 'DORM_DESSERT', '자스민차', '2026-05-29 생활관 저녁 후식', 0.00),
    ('2026-05-29', 'DINNER', 'DORM_SIMPLE', '누텔라바나샌드위치 / 음료', '2026-05-29 생활관 저녁 간편식', 560.00),
    ('2026-05-30', 'LUNCH', 'DORM_A', '당면불고기덮밥 / 시래기된장국 / 시나몬츄러스 / 깍두기', '2026-05-30 생활관 점심 A', 900.00),
    ('2026-05-30', 'LUNCH', 'DORM_SIMPLE', '가라아게샐러드 / 음료', '2026-05-30 생활관 점심 간편식', 336.00),
    ('2026-05-30', 'DINNER', 'DORM_A', '참깨달걀수제비 / 추가밥 / 새우까스*타르s / 깍두기', '2026-05-30 생활관 저녁 A', 1016.00),
    ('2026-05-31', 'LUNCH', 'DORM_A', '오징어볶음 / 김치콩나물국 / 쌀밥 / 부추전 / 도시락김 / 깍두기', '2026-05-31 생활관 점심 A', 707.00),
    ('2026-05-31', 'LUNCH', 'DORM_SIMPLE', '훈제오리샐러드 / 음료', '2026-05-31 생활관 점심 간편식', 347.00),
    ('2026-05-31', 'DINNER', 'DORM_A', '자장덮밥 / 대파달걀국 / 칠리탕수육 / 짜사이무침', '2026-05-31 생활관 저녁 A', 837.00);

insert into cafeteria_menu (dining_place_id, served_date, meal_type)
select dp.dining_place_id, s.served_date, s.meal_type
from (
    select distinct served_date, meal_type
    from tmp_inha_dorm_week4_seed
) s
join dining_place dp
  on dp.dining_place_name /*!80000 collate utf8mb4_unicode_ci */ = '인하대학교 생활관식당' /*!80000 collate utf8mb4_unicode_ci */
 and dp.dining_place_type /*!80000 collate utf8mb4_unicode_ci */ = 'DORMITORY' /*!80000 collate utf8mb4_unicode_ci */
on duplicate key update menu_id = menu_id;

insert into cafeteria_menu_option (menu_id, category_id, option_name, source_label, is_available, calories_kcal)
select m.menu_id, c.category_id, s.option_name, s.source_label, true, s.calories_kcal
from tmp_inha_dorm_week4_seed s
join dining_place dp
  on dp.dining_place_name /*!80000 collate utf8mb4_unicode_ci */ = '인하대학교 생활관식당' /*!80000 collate utf8mb4_unicode_ci */
 and dp.dining_place_type /*!80000 collate utf8mb4_unicode_ci */ = 'DORMITORY' /*!80000 collate utf8mb4_unicode_ci */
join cafeteria_menu m
  on m.dining_place_id = dp.dining_place_id
 and m.served_date = s.served_date
 and m.meal_type /*!80000 collate utf8mb4_unicode_ci */ = s.meal_type /*!80000 collate utf8mb4_unicode_ci */
join menu_category c
  on c.category_code /*!80000 collate utf8mb4_unicode_ci */ = s.category_code /*!80000 collate utf8mb4_unicode_ci */
on duplicate key update
    category_id = values(category_id),
    source_label = values(source_label),
    is_available = true,
    calories_kcal = values(calories_kcal);

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
select o.option_id, null, s.option_name, 100.00
from tmp_inha_dorm_week4_seed s
join dining_place dp
  on dp.dining_place_name /*!80000 collate utf8mb4_unicode_ci */ = '인하대학교 생활관식당' /*!80000 collate utf8mb4_unicode_ci */
 and dp.dining_place_type /*!80000 collate utf8mb4_unicode_ci */ = 'DORMITORY' /*!80000 collate utf8mb4_unicode_ci */
join cafeteria_menu m
  on m.dining_place_id = dp.dining_place_id
 and m.served_date = s.served_date
 and m.meal_type /*!80000 collate utf8mb4_unicode_ci */ = s.meal_type /*!80000 collate utf8mb4_unicode_ci */
join cafeteria_menu_option o
  on o.menu_id = m.menu_id
 and o.option_name /*!80000 collate utf8mb4_unicode_ci */ = s.option_name /*!80000 collate utf8mb4_unicode_ci */
left join cafeteria_menu_item existing_item
  on existing_item.option_id = o.option_id
 and existing_item.raw_item_name /*!80000 collate utf8mb4_unicode_ci */ = s.option_name /*!80000 collate utf8mb4_unicode_ci */
where existing_item.menu_item_id is null;

drop table if exists tmp_inha_dorm_week4_seed;
