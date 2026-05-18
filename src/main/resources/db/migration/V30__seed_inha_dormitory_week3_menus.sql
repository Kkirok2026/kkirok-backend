drop table if exists tmp_inha_dorm_week3_seed;

create table tmp_inha_dorm_week3_seed (
    served_date date not null,
    meal_type varchar(30) not null,
    category_code varchar(50) not null,
    option_name varchar(255) not null,
    source_label varchar(255) not null,
    calories_kcal decimal(10,2) null
);

insert into tmp_inha_dorm_week3_seed (served_date, meal_type, category_code, option_name, source_label, calories_kcal) values
    ('2026-05-18', 'LUNCH', 'DORM_A', '똑)닭개장 / 쌀밥 / 미트볼폭찹 / 마늘쫑건새우볶음 / 배추김치', '2026-05-18 생활관 점심 A', 844.00),
    ('2026-05-18', 'LUNCH', 'DORM_B', '마제소바 / 추가밥 / 피자춘권 / 단무지무침', '2026-05-18 생활관 점심 B', 1105.00),
    ('2026-05-18', 'DINNER', 'DORM_A', '치킨마요덮밥 / 얼큰콩나물국 / 순대강정 / 배추김치', '2026-05-18 생활관 저녁 A', 1165.00),
    ('2026-05-18', 'DINNER', 'DORM_DESSERT', '보리차', '2026-05-18 생활관 저녁 후식', 0.00),
    ('2026-05-18', 'DINNER', 'DORM_SIMPLE', '훈제오리샐러드 / 청포도주스', '2026-05-18 생활관 저녁 간편식', 261.00),

    ('2026-05-19', 'LUNCH', 'DORM_A', '두반장제육볶음 / 잡곡밥 / 얼갈이된장국 / 깻잎무침 / 미역줄기볶음 / 갓김치', '2026-05-19 생활관 점심 A', 853.00),
    ('2026-05-19', 'LUNCH', 'DORM_B', 'Big불고기치즈버거 / 양념감자 / 캔콜라', '2026-05-19 생활관 점심 B', 519.00),
    ('2026-05-19', 'DINNER', 'DORM_A', '바지락칼국수 / 보리밥&고추장 / 찹쌀깨찰빵 / 배추겉절이', '2026-05-19 생활관 저녁 A', 956.00),
    ('2026-05-19', 'DINNER', 'DORM_DESSERT', '비빔코너', '2026-05-19 생활관 저녁 후식', 0.00),
    ('2026-05-19', 'DINNER', 'DORM_SIMPLE', '블루베리리코타샐러드 / 주스쿨', '2026-05-19 생활관 저녁 간편식', 501.00),

    ('2026-05-20', 'LUNCH', 'DORM_A', '반반냉면(물냉면+비빔냉면) / 너비아니육전&땡초양념장 / 백김치', '2026-05-20 생활관 점심 A', 520.00),
    ('2026-05-20', 'DINNER', 'DORM_A', '강된장부추비빔밥 / 얼큰어묵국 / 크로와상&딸기잼 / 배추김치', '2026-05-20 생활관 저녁 A', 720.00),
    ('2026-05-20', 'DINNER', 'DORM_DESSERT', '복숭아홍차', '2026-05-20 생활관 저녁 후식', 0.00),
    ('2026-05-20', 'DINNER', 'DORM_SIMPLE', '반미샌드위치 / 요구르트', '2026-05-20 생활관 저녁 간편식', 483.00),

    ('2026-05-21', 'LUNCH', 'DORM_A', '똑)돼지고기김치찌개 / 잡곡밥 / 비엔나어묵조림 / 쥐어채조림 / 알타리김치', '2026-05-21 생활관 점심 A', 985.00),
    ('2026-05-21', 'LUNCH', 'DORM_B', '왕소세지 얹은 오므라이스 / 얼갈이된장국 / 수제맛살튀김&케찹 / 배추김치', '2026-05-21 생활관 점심 B', 1255.00),
    ('2026-05-21', 'DINNER', 'DORM_A', '김치비빔국수 / 야채계란국 / 추가밥 / 치킨불고기고로케&케찹 / 단무지', '2026-05-21 생활관 저녁 A', 1192.00),
    ('2026-05-21', 'DINNER', 'DORM_DESSERT', '요구르트', '2026-05-21 생활관 저녁 후식', 0.00),
    ('2026-05-21', 'DINNER', 'DORM_SIMPLE', '떡갈비샐러드 / 두유', '2026-05-21 생활관 저녁 간편식', 686.00),

    ('2026-05-22', 'LUNCH', 'DORM_A', '도토리묵사발 / 쌀밥 / 불맛오징어땡초볶음 / 2종나물(무생채/콩나물) / 깍두기', '2026-05-22 생활관 점심 A', 730.00),
    ('2026-05-22', 'DINNER', 'DORM_A', '똑)소고기미역국 / 쌀밥 / 매콤산적스테이크 / 순두부찜&양념장 / 시금치생채 / 배추김치', '2026-05-22 생활관 저녁 A', 884.00),
    ('2026-05-22', 'DINNER', 'DORM_DESSERT', '복분자차', '2026-05-22 생활관 저녁 후식', 0.00),
    ('2026-05-22', 'DINNER', 'DORM_SIMPLE', '치킨또띠아랩 / 사이다', '2026-05-22 생활관 저녁 간편식', 341.00),

    ('2026-05-23', 'LUNCH', 'DORM_A', '매운우삼겹떡국 / 추가밥 / 피쉬앤칩스 / 단무지', '2026-05-23 생활관 점심 A', 597.00),
    ('2026-05-23', 'LUNCH', 'DORM_SIMPLE', '푸실리소세지샐러드 / 매실음료', '2026-05-23 생활관 점심 간편식', 592.00),
    ('2026-05-23', 'DINNER', 'DORM_A', '소보로비빔밥 / 유채된장국 / 타코야끼 / 깍두기', '2026-05-23 생활관 저녁 A', 1035.00),

    ('2026-05-24', 'LUNCH', 'DORM_A', '함박스테이크 / 김치미니우동 / 추가밥 / 그린샐러드&S / 깍두기', '2026-05-24 생활관 점심 A', 949.00),
    ('2026-05-24', 'LUNCH', 'DORM_SIMPLE', '단호박콘샐러드 / 옥수수수염차', '2026-05-24 생활관 점심 간편식', 247.00),
    ('2026-05-24', 'DINNER', 'DORM_A', '청양풍간장닭볶음 / 쌀밥 / 콩가루배추국 / 도시락김 / 실곤약콩나물무침 / 깍두기', '2026-05-24 생활관 저녁 A', 713.00);

insert into cafeteria_menu (dining_place_id, served_date, meal_type)
select dp.dining_place_id, s.served_date, s.meal_type
from (
    select distinct served_date, meal_type
    from tmp_inha_dorm_week3_seed
) s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 생활관식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'DORMITORY' collate utf8mb4_unicode_ci
on duplicate key update menu_id = menu_id;

insert into cafeteria_menu_option (menu_id, category_id, option_name, source_label, is_available, calories_kcal)
select m.menu_id, c.category_id, s.option_name, s.source_label, true, s.calories_kcal
from tmp_inha_dorm_week3_seed s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 생활관식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'DORMITORY' collate utf8mb4_unicode_ci
join cafeteria_menu m
  on m.dining_place_id = dp.dining_place_id
 and m.served_date = s.served_date
 and m.meal_type collate utf8mb4_unicode_ci = s.meal_type collate utf8mb4_unicode_ci
join menu_category c
  on c.category_code collate utf8mb4_unicode_ci = s.category_code collate utf8mb4_unicode_ci
on duplicate key update
    category_id = values(category_id),
    source_label = values(source_label),
    is_available = true,
    calories_kcal = values(calories_kcal);

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
select o.option_id, null, s.option_name, 100.00
from tmp_inha_dorm_week3_seed s
join dining_place dp
  on dp.dining_place_name collate utf8mb4_unicode_ci = '인하대학교 생활관식당' collate utf8mb4_unicode_ci
 and dp.dining_place_type collate utf8mb4_unicode_ci = 'DORMITORY' collate utf8mb4_unicode_ci
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

drop table if exists tmp_inha_dorm_week3_seed;
