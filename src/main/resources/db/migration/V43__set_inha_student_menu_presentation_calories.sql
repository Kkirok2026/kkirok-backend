insert into food (
    source_name,
    source_food_code,
    food_name,
    default_serving_g,
    nutrition_basis_amount_g,
    total_weight_g,
    calories_kcal,
    carb_g,
    protein_g,
    fat_g,
    sugar_g,
    sodium_mg
) values
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_HAM_CHEESE_SOONDUBU', '햄치즈순두부찌개', 100, 100, 100, 335, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_RICE', '쌀밥', 100, 100, 100, 300, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_DONGGEURANGTTAENG', '동그랑땡전', 100, 100, 100, 180, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_DOSIRAK_GIM', '도시락김', 100, 100, 100, 10, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_DANMUJI', '단무지', 100, 100, 100, 16, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_MAT_KIMCHI', '맛김치', 100, 100, 100, 14, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_TONGDEUNGSIM_DONKATSU', '통등심돈까스', 100, 100, 100, 570, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_GOGUMA_CHEESE_DONKATSU', '고구마치즈돈까스', 100, 100, 100, 720, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_EXTRA_RICE', '추가밥', 100, 100, 100, 300, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_FRENCH_FRIES', '감자튀김', 100, 100, 100, 278, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_GREEN_SALAD', '그린샐러드', 100, 100, 100, 69, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_SHIN_RAMEN', '신라면', 100, 100, 100, 500, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_SNOW_CHEESE_RABOKKI', '눈꽃치즈라볶이', 100, 100, 100, 322, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_TUNA_MAYO_GIM_RICE', '참치마요김가루밥', 100, 100, 100, 406, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_BEEF_YUKGAEJANG', '소고기육개장', 100, 100, 100, 350, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_FRANK_FISHCAKE', '후랑크어묵볶음', 100, 100, 100, 140, 0, 0, 0, 0, 0),
    ('PRESENTATION_MENU', 'INHA_STUDENT_20260526_OBOGEE_MUCHIM', '오복지무침', 100, 100, 100, 70, 0, 0, 0, 0, 0)
on duplicate key update
    food_name = values(food_name),
    default_serving_g = values(default_serving_g),
    nutrition_basis_amount_g = values(nutrition_basis_amount_g),
    total_weight_g = values(total_weight_g),
    calories_kcal = values(calories_kcal),
    carb_g = values(carb_g),
    protein_g = values(protein_g),
    fat_g = values(fat_g),
    sugar_g = values(sugar_g),
    sodium_mg = values(sodium_mg);

update cafeteria_menu_option
set calories_kcal = 855
where option_name = '햄치즈순두부찌개 / 쌀밥 / 동그랑땡전*케찹 / 도시락김 / 단무지 / 맛김치'
  and exists (
      select 1
      from cafeteria_menu m
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where m.menu_id = cafeteria_menu_option.menu_id
        and dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'LUNCH'
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'PRESENTATION_MENU'
          and source_food_code = 'INHA_STUDENT_20260526_HAM_CHEESE_SOONDUBU'
    ),
    amount_g = 100
where raw_item_name = '햄치즈순두부찌개'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'LUNCH'
        and o.option_name = '햄치즈순두부찌개 / 쌀밥 / 동그랑땡전*케찹 / 도시락김 / 단무지 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_RICE'),
    amount_g = 100
where raw_item_name = '쌀밥'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and o.option_name in (
            '햄치즈순두부찌개 / 쌀밥 / 동그랑땡전*케찹 / 도시락김 / 단무지 / 맛김치',
            '소고기육개장 / 쌀밥 / 후랑크어묵볶음 / 오복지무침 / 맛김치'
        )
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DONGGEURANGTTAENG'),
    amount_g = 100
where raw_item_name = '동그랑땡전*케찹'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and o.option_name = '햄치즈순두부찌개 / 쌀밥 / 동그랑땡전*케찹 / 도시락김 / 단무지 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DOSIRAK_GIM'),
    amount_g = 100
where raw_item_name = '도시락김'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and o.option_name = '햄치즈순두부찌개 / 쌀밥 / 동그랑땡전*케찹 / 도시락김 / 단무지 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DANMUJI'),
    amount_g = 100
where raw_item_name = '단무지'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_MAT_KIMCHI'),
    amount_g = 100
where raw_item_name = '맛김치'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_option
set option_name = '고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치',
    calories_kcal = 1397
where option_name = '통등심돈까스or고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치'
  and exists (
      select 1
      from cafeteria_menu m
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where m.menu_id = cafeteria_menu_option.menu_id
        and dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'LUNCH'
  );

update cafeteria_menu_item
set raw_item_name = '고구마치즈돈까스',
    food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_GOGUMA_CHEESE_DONKATSU'),
    amount_g = 100
where raw_item_name in ('통등심돈까스or고구마치즈돈까스', '고구마치즈돈까스')
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_EXTRA_RICE'),
    amount_g = 100
where raw_item_name = '추가밥'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name in (
          '고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치',
          '통등심돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치'
      )
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_GREEN_SALAD'),
    amount_g = 100
where raw_item_name = '그린샐러드*D'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name in (
          '고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치',
          '통등심돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치'
      )
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_FRENCH_FRIES'),
    amount_g = 100
where raw_item_name = '감자튀김'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name in (
          '고구마치즈돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치',
          '통등심돈까스 / 추가밥 / 그린샐러드*D / 감자튀김 / 단무지 / 맛김치'
      )
  );

update cafeteria_menu_option
set calories_kcal = 500
where option_name = '신라면/너구리/불닭볶음면/짜파게티'
  and exists (
      select 1
      from cafeteria_menu m
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where m.menu_id = cafeteria_menu_option.menu_id
        and dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'LUNCH'
  );

delete from cafeteria_menu_item
where option_id in (
    select o.option_id
    from cafeteria_menu_option o
    join cafeteria_menu m on m.menu_id = o.menu_id
    join dining_place dp on dp.dining_place_id = m.dining_place_id
    where dp.dining_place_name = '인하대학교 학생식당'
      and m.served_date = '2026-05-26'
      and m.meal_type = 'LUNCH'
      and o.option_name = '신라면/너구리/불닭볶음면/짜파게티'
);

insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
select o.option_id, null, o.option_name, 100
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
where dp.dining_place_name = '인하대학교 학생식당'
  and m.served_date = '2026-05-26'
  and m.meal_type = 'LUNCH'
  and o.option_name = '신라면/너구리/불닭볶음면/짜파게티'
  and not exists (
      select 1
      from cafeteria_menu_item existing
      where existing.option_id = o.option_id
  );

update cafeteria_menu_option
set calories_kcal = 758
where option_name = '눈꽃치즈라볶이 / 참치마요김가루밥 / 단무지 / 맛김치'
  and exists (
      select 1
      from cafeteria_menu m
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where m.menu_id = cafeteria_menu_option.menu_id
        and dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'LUNCH'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_SNOW_CHEESE_RABOKKI'),
    amount_g = 100
where raw_item_name = '눈꽃치즈라볶이'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '눈꽃치즈라볶이 / 참치마요김가루밥 / 단무지 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_TUNA_MAYO_GIM_RICE'),
    amount_g = 100
where raw_item_name = '참치마요김가루밥'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '눈꽃치즈라볶이 / 참치마요김가루밥 / 단무지 / 맛김치'
  );

update cafeteria_menu_option
set calories_kcal = 874
where option_name = '소고기육개장 / 쌀밥 / 후랑크어묵볶음 / 오복지무침 / 맛김치'
  and exists (
      select 1
      from cafeteria_menu m
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where m.menu_id = cafeteria_menu_option.menu_id
        and dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
        and m.meal_type = 'DINNER'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_BEEF_YUKGAEJANG'),
    amount_g = 100
where raw_item_name = '소고기육개장'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '소고기육개장 / 쌀밥 / 후랑크어묵볶음 / 오복지무침 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_FRANK_FISHCAKE'),
    amount_g = 100
where raw_item_name = '후랑크어묵볶음'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '소고기육개장 / 쌀밥 / 후랑크어묵볶음 / 오복지무침 / 맛김치'
  );

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_OBOGEE_MUCHIM'),
    amount_g = 100
where raw_item_name = '오복지무침'
  and option_id in (
      select option_id
      from cafeteria_menu_option
      where option_name = '소고기육개장 / 쌀밥 / 후랑크어묵볶음 / 오복지무침 / 맛김치'
  );

update meal_log_item
set food_id = (
        select mi.food_id
        from cafeteria_menu_item mi
        where mi.option_id = meal_log_item.source_menu_option_id
          and mi.raw_item_name = meal_log_item.item_name_snapshot
        limit 1
    ),
    amount_g = (
        select mi.amount_g
        from cafeteria_menu_item mi
        where mi.option_id = meal_log_item.source_menu_option_id
          and mi.raw_item_name = meal_log_item.item_name_snapshot
        limit 1
    )
where source_menu_option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  )
  and exists (
      select 1
      from cafeteria_menu_item mi
      where mi.option_id = meal_log_item.source_menu_option_id
        and mi.raw_item_name = meal_log_item.item_name_snapshot
        and mi.food_id is not null
  );
