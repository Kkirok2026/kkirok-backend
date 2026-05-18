drop table if exists tmp_inha_student_seed;

create table tmp_inha_student_seed (
    served_date date not null,
    meal_type varchar(30) not null,
    category_code varchar(50) not null,
    option_name varchar(255) not null,
    source_label varchar(255) not null
);

insert into tmp_inha_student_seed (served_date, meal_type, category_code, option_name, source_label) values
    ('2026-05-11', 'LUNCH', 'STUDENT_HANSANG', '제육덮밥 / 미역국 / 어묵볶음 / 단무지 / 배추김치', '2026-05-11 학생식당 한상한담'),
    ('2026-05-11', 'LUNCH', 'STUDENT_ONE_PLATE', '치킨마요덮밥 / 장국 / 샐러드 / 피클', '2026-05-11 학생식당 ONE PLATE'),
    ('2026-05-11', 'LUNCH', 'STUDENT_NOODLE', '잔치국수 / 후리가케밥 / 김말이튀김 / 배추김치', '2026-05-11 학생식당 Noodle'),
    ('2026-05-11', 'LUNCH', 'STUDENT_SIMPLE', '참치샌드위치 / 음료', '2026-05-11 학생식당 간편식'),
    ('2026-05-11', 'DINNER', 'STUDENT_DINNER', '돈까스카레덮밥 / 유부장국 / 양배추샐러드 / 깍두기', '2026-05-11 학생식당 석식'),

    ('2026-05-12', 'LUNCH', 'STUDENT_HANSANG', '돼지고기김치찌개 / 쌀밥 / 계란말이 / 콩나물무침 / 깍두기', '2026-05-12 학생식당 한상한담'),
    ('2026-05-12', 'LUNCH', 'STUDENT_ONE_PLATE', '불고기필라프 / 크림스프 / 감자튀김 / 피클', '2026-05-12 학생식당 ONE PLATE'),
    ('2026-05-12', 'LUNCH', 'STUDENT_NOODLE', '우육면 / 추가밥 / 춘권튀김 / 단무지', '2026-05-12 학생식당 Noodle'),
    ('2026-05-12', 'LUNCH', 'STUDENT_SIMPLE', '닭가슴살샐러드 / 음료', '2026-05-12 학생식당 간편식'),
    ('2026-05-12', 'DINNER', 'STUDENT_DINNER', '오므라이스 / 미니우동 / 소시지구이 / 맛김치', '2026-05-12 학생식당 석식'),

    ('2026-05-13', 'LUNCH', 'STUDENT_HANSANG', '닭갈비덮밥 / 콩나물국 / 멸치볶음 / 무생채 / 배추김치', '2026-05-13 학생식당 한상한담'),
    ('2026-05-13', 'LUNCH', 'STUDENT_ONE_PLATE', '함박스테이크 / 볶음밥 / 샐러드 / 피클', '2026-05-13 학생식당 ONE PLATE'),
    ('2026-05-13', 'LUNCH', 'STUDENT_NOODLE', '비빔국수 / 계란국 / 만두튀김 / 단무지', '2026-05-13 학생식당 Noodle'),
    ('2026-05-13', 'LUNCH', 'STUDENT_SIMPLE', '에그샌드위치 / 음료', '2026-05-13 학생식당 간편식'),
    ('2026-05-13', 'DINNER', 'STUDENT_DINNER', '마파두부덮밥 / 부추계란국 / 탕수육 / 배추김치', '2026-05-13 학생식당 석식'),

    ('2026-05-14', 'LUNCH', 'STUDENT_HANSANG', '돈육간장불고기 / 쌀밥 / 된장국 / 감자조림 / 깍두기', '2026-05-14 학생식당 한상한담'),
    ('2026-05-14', 'LUNCH', 'STUDENT_ONE_PLATE', '새우튀김 오므라이스 / 미니떡볶이 / 단무지 / 맛김치', '2026-05-14 학생식당 ONE PLATE'),
    ('2026-05-14', 'LUNCH', 'STUDENT_NOODLE', '라멘 / 추가밥 / 고로케 / 단무지', '2026-05-14 학생식당 Noodle'),
    ('2026-05-14', 'LUNCH', 'STUDENT_SIMPLE', '불고기버거 / 음료', '2026-05-14 학생식당 간편식'),
    ('2026-05-14', 'DINNER', 'STUDENT_DINNER', '치킨덮밥 / 장국 / 콘샐러드 / 배추김치', '2026-05-14 학생식당 석식'),

    ('2026-05-15', 'LUNCH', 'STUDENT_HANSANG', '순두부찌개 / 쌀밥 / 떡갈비조림 / 시금치나물 / 깍두기', '2026-05-15 학생식당 한상한담'),
    ('2026-05-15', 'LUNCH', 'STUDENT_ONE_PLATE', '토마토스파게티 / 마늘빵 / 샐러드 / 피클', '2026-05-15 학생식당 ONE PLATE'),
    ('2026-05-15', 'LUNCH', 'STUDENT_NOODLE', '쫄면 / 우동국물 / 김가루밥 / 단무지', '2026-05-15 학생식당 Noodle'),
    ('2026-05-15', 'LUNCH', 'STUDENT_SIMPLE', '햄치즈샌드위치 / 음료', '2026-05-15 학생식당 간편식'),
    ('2026-05-15', 'DINNER', 'STUDENT_DINNER', '하이라이스 / 함박스테이크 / 유부장국 / 깍두기', '2026-05-15 학생식당 석식'),

    ('2026-05-16', 'LUNCH', 'STUDENT_HANSANG', '김치제육덮밥 / 계란파국 / 어묵튀김 / 단무지', '2026-05-16 학생식당 한상한담'),
    ('2026-05-16', 'LUNCH', 'STUDENT_ONE_PLATE', '치킨가스덮밥 / 장국 / 양배추샐러드 / 피클', '2026-05-16 학생식당 ONE PLATE'),
    ('2026-05-16', 'LUNCH', 'STUDENT_NOODLE', '어묵우동 / 후리가케밥 / 야채튀김 / 배추김치', '2026-05-16 학생식당 Noodle'),
    ('2026-05-16', 'LUNCH', 'STUDENT_SIMPLE', '시저샐러드 / 음료', '2026-05-16 학생식당 간편식'),
    ('2026-05-16', 'DINNER', 'STUDENT_DINNER', '참치마요덮밥 / 미소장국 / 소떡소떡 / 깍두기', '2026-05-16 학생식당 석식'),

    ('2026-05-17', 'LUNCH', 'STUDENT_HANSANG', '돼지불백 / 쌀밥 / 콩나물국 / 무말랭이무침 / 배추김치', '2026-05-17 학생식당 한상한담'),
    ('2026-05-17', 'LUNCH', 'STUDENT_ONE_PLATE', '새우튀김 오므라이스 / 미니떡볶이 / 단무지 / 맛김치', '2026-05-17 학생식당 ONE PLATE'),
    ('2026-05-17', 'LUNCH', 'STUDENT_NOODLE', '냉모밀 / 유부초밥 / 고구마튀김 / 단무지', '2026-05-17 학생식당 Noodle'),
    ('2026-05-17', 'LUNCH', 'STUDENT_SIMPLE', '치킨텐더샐러드 / 음료', '2026-05-17 학생식당 간편식'),
    ('2026-05-17', 'DINNER', 'STUDENT_DINNER', '불닭덮밥 / 맑은장국 / 감자고로케 / 깍두기', '2026-05-17 학생식당 석식');

insert into cafeteria_menu (dining_place_id, served_date, meal_type)
select dp.dining_place_id, s.served_date, s.meal_type
from (
    select distinct served_date, meal_type
    from tmp_inha_student_seed
) s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 학생식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'STUDENT' collate utf8mb4_unicode_ci
on duplicate key update menu_id = menu_id;

insert into cafeteria_menu_option (menu_id, category_id, option_name, source_label, is_available)
select m.menu_id, c.category_id, s.option_name, s.source_label, true
from tmp_inha_student_seed s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 학생식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'STUDENT' collate utf8mb4_unicode_ci
join cafeteria_menu m
  on m.dining_place_id = dp.dining_place_id
 and m.served_date = s.served_date
 and m.meal_type collate utf8mb4_unicode_ci = s.meal_type collate utf8mb4_unicode_ci
join menu_category c
  on c.category_code collate utf8mb4_unicode_ci = s.category_code collate utf8mb4_unicode_ci
on duplicate key update
    category_id = values(category_id),
    source_label = values(source_label),
    is_available = true;

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
select o.option_id, null, s.option_name, 100.00
from tmp_inha_student_seed s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 학생식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'STUDENT' collate utf8mb4_unicode_ci
join cafeteria_menu m
  on m.dining_place_id = dp.dining_place_id
 and m.served_date = s.served_date
 and m.meal_type collate utf8mb4_unicode_ci = s.meal_type collate utf8mb4_unicode_ci
join cafeteria_menu_option o
  on o.menu_id = m.menu_id
 and o.option_name collate utf8mb4_unicode_ci = s.option_name collate utf8mb4_unicode_ci
left join cafeteria_menu_item existing_item
  on existing_item.option_id = o.option_id
 and existing_item.raw_item_name collate utf8mb4_unicode_ci = s.option_name collate utf8mb4_unicode_ci
where existing_item.menu_item_id is null;

drop table if exists tmp_inha_student_seed;
