insert into universities (university_id, university_code, university_name) values
    (1, 'KKIROK', '끼록대학교');

insert into university_email_domains (domain_id, university_id, email_domain, verification_method) values
    (1, 1, 'kkirok.ac.kr', 'SCHOOL_EMAIL'),
    (2, 1, 'univ.ac.kr', 'SCHOOL_EMAIL');

insert into meal_type (meal_type_id, meal_type_code, meal_type_name) values
    (1, 'BREAKFAST', '아침'),
    (2, 'LUNCH', '점심'),
    (3, 'DINNER', '저녁'),
    (4, 'SNACK', '간식');

insert into dining_place (dining_place_id, university_id, dining_place_name, dining_place_type, menu_source_url) values
    (1, 1, '학생식당', 'STUDENT', 'https://example.edu/student-cafeteria'),
    (2, 1, '기숙사식당', 'DORMITORY', 'https://example.edu/dormitory-cafeteria');

insert into menu_category (category_id, category_code, category_name, sort_order) values
    (1, 'DEFAULT', '단일 메뉴', 0),
    (2, 'KOREAN', '한식', 1),
    (3, 'WESTERN', '양식', 2);

insert into nutrient (nutrient_id, nutrient_code, nutrient_name, unit, sort_order) values
    (1, 'CALORIES_KCAL', '열량', 'kcal', 1),
    (2, 'CARB_G', '탄수화물', 'g', 2),
    (3, 'PROTEIN_G', '단백질', 'g', 3),
    (4, 'FAT_G', '지방', 'g', 4),
    (5, 'SUGAR_G', '당류', 'g', 5),
    (6, 'SODIUM_MG', '나트륨', 'mg', 6);

insert into food (food_id, source_name, source_food_code, food_name, default_serving_g, source_category) values
    (1, 'PUBLIC_SAMPLE', 'F001', '쌀밥', 210.00, '곡류'),
    (2, 'PUBLIC_SAMPLE', 'F002', '닭가슴살 샐러드', 320.00, '샐러드'),
    (3, 'PUBLIC_SAMPLE', 'F003', '김치찌개', 300.00, '국/찌개'),
    (4, 'PUBLIC_SAMPLE', 'F004', '토마토 파스타', 280.00, '면류'),
    (5, 'PUBLIC_SAMPLE', 'F005', '고등어구이', 120.00, '생선류'),
    (6, 'PUBLIC_SAMPLE', 'F006', '바나나', 100.00, '과일류'),
    (7, 'PUBLIC_SAMPLE', 'F007', '제육볶음', 150.00, '육류'),
    (8, 'PUBLIC_SAMPLE', 'F008', '배추김치', 50.00, '반찬');

insert into food_alias (food_id, alias_name, normalized_alias, alias_type, priority) values
    (2, '닭가슴살샐러드', '닭가슴살샐러드', 'SEARCH', 10),
    (3, '김치 찌개', '김치 찌개', 'SEARCH', 10),
    (7, '돼지불고기', '돼지불고기', 'SEARCH', 5);

insert into food_nutrient_value (food_id, nutrient_id, amount_per_100g) values
    (1, 1, 143.0000), (1, 2, 31.0000), (1, 3, 2.7000), (1, 4, 0.3000), (1, 5, 0.1000), (1, 6, 2.0000),
    (2, 1, 134.0000), (2, 2, 6.0000), (2, 3, 12.0000), (2, 4, 6.0000), (2, 5, 2.0000), (2, 6, 230.0000),
    (3, 1, 61.0000), (3, 2, 4.5000), (3, 3, 3.9000), (3, 4, 3.2000), (3, 5, 1.5000), (3, 6, 420.0000),
    (4, 1, 162.0000), (4, 2, 25.0000), (4, 3, 5.5000), (4, 4, 4.2000), (4, 5, 3.8000), (4, 6, 260.0000),
    (5, 1, 271.0000), (5, 2, 0.0000), (5, 3, 20.2000), (5, 4, 20.4000), (5, 5, 0.0000), (5, 6, 120.0000),
    (6, 1, 89.0000), (6, 2, 22.8000), (6, 3, 1.1000), (6, 4, 0.3000), (6, 5, 12.2000), (6, 6, 1.0000),
    (7, 1, 248.0000), (7, 2, 8.0000), (7, 3, 15.0000), (7, 4, 15.0000), (7, 5, 5.0000), (7, 6, 520.0000),
    (8, 1, 21.0000), (8, 2, 3.2000), (8, 3, 1.4000), (8, 4, 0.2000), (8, 5, 1.1000), (8, 6, 498.0000);

insert into cafeteria_menu (menu_id, dining_place_id, meal_type_id, served_date) values
    (1, 1, 2, date '2026-05-11'),
    (2, 1, 3, date '2026-05-11'),
    (3, 2, 2, date '2026-05-11'),
    (4, 2, 3, date '2026-05-11');

insert into cafeteria_menu_option (option_id, menu_id, category_id, option_name, source_label, price) values
    (1, 1, 2, '제육덮밥 세트', '학생식당 점심 한식', 5500),
    (2, 1, 3, '닭가슴살 샐러드 세트', '학생식당 점심 양식', 6200),
    (3, 2, 1, '고등어구이 정식', '학생식당 석식', 6000),
    (4, 3, 1, '김치찌개 백반', '기숙사식당 중식', 4800),
    (5, 4, 1, '토마토 파스타', '기숙사식당 석식', 5200);

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g) values
    (1, 1, '쌀밥', 210.00),
    (1, 7, '제육볶음', 150.00),
    (1, 8, '배추김치', 50.00),
    (2, 2, '닭가슴살 샐러드', 320.00),
    (2, 6, '바나나', 100.00),
    (3, 1, '쌀밥', 210.00),
    (3, 5, '고등어구이', 120.00),
    (3, 8, '배추김치', 50.00),
    (4, 1, '쌀밥', 210.00),
    (4, 3, '김치찌개', 300.00),
    (5, 4, '토마토 파스타', 280.00);

insert into nutrition_standard_group (standard_group_id, gender, bmi_min, bmi_max, source_name, description) values
    (1, 'MALE', 0.00, 99.99, '초기 샘플 권장 기준', '남성 기본 권장 섭취량 샘플'),
    (2, 'FEMALE', 0.00, 99.99, '초기 샘플 권장 기준', '여성 기본 권장 섭취량 샘플'),
    (3, 'OTHER', 0.00, 99.99, '초기 샘플 권장 기준', '성별 기타 기본 권장 섭취량 샘플');

insert into nutrition_standard_value (standard_group_id, nutrient_id, recommended_amount, upper_limit_amount, basis) values
    (1, 1, 2400.0000, 2900.0000, 'DAY'),
    (1, 2, 330.0000, 390.0000, 'DAY'),
    (1, 3, 65.0000, 160.0000, 'DAY'),
    (1, 4, 67.0000, 90.0000, 'DAY'),
    (1, 5, 50.0000, 65.0000, 'DAY'),
    (1, 6, 1500.0000, 2300.0000, 'DAY'),
    (2, 1, 1900.0000, 2400.0000, 'DAY'),
    (2, 2, 260.0000, 320.0000, 'DAY'),
    (2, 3, 55.0000, 140.0000, 'DAY'),
    (2, 4, 53.0000, 75.0000, 'DAY'),
    (2, 5, 50.0000, 65.0000, 'DAY'),
    (2, 6, 1500.0000, 2300.0000, 'DAY'),
    (3, 1, 2100.0000, 2600.0000, 'DAY'),
    (3, 2, 290.0000, 350.0000, 'DAY'),
    (3, 3, 60.0000, 150.0000, 'DAY'),
    (3, 4, 60.0000, 82.0000, 'DAY'),
    (3, 5, 50.0000, 65.0000, 'DAY'),
    (3, 6, 1500.0000, 2300.0000, 'DAY');
