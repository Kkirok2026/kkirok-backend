delete from meal_log_item
where food_id in (
    select food_id
    from food
    where source_name = 'USER_CUSTOM'
);

delete from food
where source_name = 'USER_CUSTOM';

update food
set calories_kcal = 335
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P123-208020300-1061';

update food
set calories_kcal = 300
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'D501-022000000-0001';

update food
set calories_kcal = 180
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P123-209020200-0002';

update food
set calories_kcal = 10
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P120-401040000-1124';

update food
set calories_kcal = 16
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'D117-694000000-0001';

update food
set calories_kcal = 14
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P114-101010200-1446';

update food
set calories_kcal = 278
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P123-214020300-0001';

update food
set calories_kcal = 69
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P123-226020200-0978';

update food
set calories_kcal = 500
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P108-003000400-0138';

update food
set calories_kcal = 70
where source_name = 'NATIONAL_INTEGRATED'
  and source_food_code = 'P114-400020100-2658';

update food
set carb_g = 70,
    protein_g = 28,
    fat_g = 36,
    sugar_g = 7,
    sodium_mg = 950
where source_name = 'PRESENTATION_MENU'
  and source_food_code = 'INHA_STUDENT_20260526_GOGUMA_CHEESE_DONKATSU';

update food
set carb_g = 58,
    protein_g = 8,
    fat_g = 6,
    sugar_g = 8,
    sodium_mg = 780
where source_name = 'PRESENTATION_MENU'
  and source_food_code = 'INHA_STUDENT_20260526_SNOW_CHEESE_RABOKKI';

update food
set carb_g = 54,
    protein_g = 13,
    fat_g = 15,
    sugar_g = 4,
    sodium_mg = 720
where source_name = 'PRESENTATION_MENU'
  and source_food_code = 'INHA_STUDENT_20260526_TUNA_MAYO_GIM_RICE';

update food
set carb_g = 26,
    protein_g = 24,
    fat_g = 18,
    sugar_g = 4,
    sodium_mg = 1050
where source_name = 'PRESENTATION_MENU'
  and source_food_code = 'INHA_STUDENT_20260526_BEEF_YUKGAEJANG';

update food
set carb_g = 12,
    protein_g = 7,
    fat_g = 7,
    sugar_g = 4,
    sodium_mg = 620
where source_name = 'PRESENTATION_MENU'
  and source_food_code = 'INHA_STUDENT_20260526_FRANK_FISHCAKE';

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P123-208020300-1061'
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
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'D501-022000000-0001'
    ),
    amount_g = 100
where raw_item_name in ('쌀밥', '추가밥')
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P123-209020200-0002'
    ),
    amount_g = 100
where raw_item_name = '동그랑땡전*케찹'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P120-401040000-1124'
    ),
    amount_g = 100
where raw_item_name = '도시락김'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'D117-694000000-0001'
    ),
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
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P114-101010200-1446'
    ),
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

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P123-214020300-0001'
    ),
    amount_g = 100
where raw_item_name = '감자튀김'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
  );

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P123-226020200-0978'
    ),
    amount_g = 100
where raw_item_name = '그린샐러드*D'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
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
select o.option_id,
       f.food_id,
       '신라면',
       100
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join food f on f.source_name = 'NATIONAL_INTEGRATED'
    and f.source_food_code = 'P108-003000400-0138'
where dp.dining_place_name = '인하대학교 학생식당'
  and m.served_date = '2026-05-26'
  and m.meal_type = 'LUNCH'
  and o.option_name = '신라면/너구리/불닭볶음면/짜파게티';

update cafeteria_menu_item
set food_id = (
        select food_id from food
        where source_name = 'NATIONAL_INTEGRATED'
          and source_food_code = 'P114-400020100-2658'
    ),
    amount_g = 100
where raw_item_name = '오복지무침'
  and option_id in (
      select o.option_id
      from cafeteria_menu_option o
      join cafeteria_menu m on m.menu_id = o.menu_id
      join dining_place dp on dp.dining_place_id = m.dining_place_id
      where dp.dining_place_name = '인하대학교 학생식당'
        and m.served_date = '2026-05-26'
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

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-208020300-1061')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_HAM_CHEESE_SOONDUBU');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-208020300-1061')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_HAM_CHEESE_SOONDUBU');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'D501-022000000-0001')
where food_id in (
    select food_id
    from food
    where source_name = 'PRESENTATION_MENU'
      and source_food_code in ('INHA_STUDENT_20260526_RICE', 'INHA_STUDENT_20260526_EXTRA_RICE')
);

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'D501-022000000-0001')
where food_id in (
    select food_id
    from food
    where source_name = 'PRESENTATION_MENU'
      and source_food_code in ('INHA_STUDENT_20260526_RICE', 'INHA_STUDENT_20260526_EXTRA_RICE')
);

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-209020200-0002')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DONGGEURANGTTAENG');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-209020200-0002')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DONGGEURANGTTAENG');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P120-401040000-1124')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DOSIRAK_GIM');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P120-401040000-1124')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DOSIRAK_GIM');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'D117-694000000-0001')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DANMUJI');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'D117-694000000-0001')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_DANMUJI');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P114-101010200-1446')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_MAT_KIMCHI');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P114-101010200-1446')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_MAT_KIMCHI');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-214020300-0001')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_FRENCH_FRIES');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-214020300-0001')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_FRENCH_FRIES');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-226020200-0978')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_GREEN_SALAD');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P123-226020200-0978')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_GREEN_SALAD');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P108-003000400-0138')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_SHIN_RAMEN');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P108-003000400-0138')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_SHIN_RAMEN');

update cafeteria_menu_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P114-400020100-2658')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_OBOGEE_MUCHIM');

update meal_log_item
set food_id = (select food_id from food where source_name = 'NATIONAL_INTEGRATED' and source_food_code = 'P114-400020100-2658')
where food_id = (select food_id from food where source_name = 'PRESENTATION_MENU' and source_food_code = 'INHA_STUDENT_20260526_OBOGEE_MUCHIM');

delete from food
where source_name = 'PRESENTATION_MENU'
  and source_food_code in (
      'INHA_STUDENT_20260526_HAM_CHEESE_SOONDUBU',
      'INHA_STUDENT_20260526_RICE',
      'INHA_STUDENT_20260526_DONGGEURANGTTAENG',
      'INHA_STUDENT_20260526_DOSIRAK_GIM',
      'INHA_STUDENT_20260526_DANMUJI',
      'INHA_STUDENT_20260526_MAT_KIMCHI',
      'INHA_STUDENT_20260526_TONGDEUNGSIM_DONKATSU',
      'INHA_STUDENT_20260526_EXTRA_RICE',
      'INHA_STUDENT_20260526_FRENCH_FRIES',
      'INHA_STUDENT_20260526_GREEN_SALAD',
      'INHA_STUDENT_20260526_SHIN_RAMEN',
      'INHA_STUDENT_20260526_OBOGEE_MUCHIM'
  );
