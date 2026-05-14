insert into menu_category (category_id, category_code, category_name, sort_order) values
    (16, 'STUDENT_HANSANG', '한상한담', 20),
    (17, 'STUDENT_ONE_PLATE', 'ONE PLATE', 21),
    (18, 'STUDENT_NOODLE', 'Noodle', 22),
    (19, 'STUDENT_SELF_RAMEN', '셀프라면', 23),
    (20, 'STUDENT_SIMPLE', '간편식', 24),
    (21, 'STUDENT_DINNER', '석식', 25)
on duplicate key update
    category_name = values(category_name),
    sort_order = values(sort_order);

update diet_entry_item
set source_option_id = null
where source_option_id in (
    select o.option_id
    from cafeteria_menu_option o
    join cafeteria_menu m on m.menu_id = o.menu_id
    join dining_place dp on dp.dining_place_id = m.dining_place_id
    join universities u on u.university_id = dp.university_id
    join meal_type mt on mt.meal_type_id = m.meal_type_id
    where u.university_code = 'INHA'
      and dp.dining_place_type = 'DORMITORY'
      and mt.meal_type_code not in ('LUNCH', 'DINNER')
);

delete from cafeteria_menu
where dining_place_id in (
    select dp.dining_place_id
    from dining_place dp
    join universities u on u.university_id = dp.university_id
    where u.university_code = 'INHA'
      and dp.dining_place_type = 'DORMITORY'
)
  and meal_type_id in (
      select meal_type_id
      from meal_type
      where meal_type_code not in ('LUNCH', 'DINNER')
  );

drop table if exists tmp_inha_student_menu_category;

create table tmp_inha_student_menu_category (
    option_id bigint primary key,
    category_code varchar(50) not null,
    source_label varchar(255) not null
);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_DINNER', '석식'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'DINNER'
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_HANSANG', '한상한담'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'LUNCH'
  and (
      replace(lower(coalesce(o.source_label, '')), ' ', '') like '%한상한담%'
      or replace(lower(o.option_name), ' ', '') like '%한상한담%'
  )
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_ONE_PLATE', 'ONE PLATE'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'LUNCH'
  and (
      replace(lower(coalesce(o.source_label, '')), ' ', '') like '%oneplate%'
      or replace(lower(o.option_name), ' ', '') like '%oneplate%'
  )
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_NOODLE', 'Noodle'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'LUNCH'
  and (
      replace(lower(coalesce(o.source_label, '')), ' ', '') like '%noodle%'
      or replace(lower(o.option_name), ' ', '') like '%noodle%'
      or coalesce(o.source_label, '') like '%누들%'
      or o.option_name like '%누들%'
  )
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_SELF_RAMEN', '셀프라면'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'LUNCH'
  and (
      coalesce(o.source_label, '') like '%셀프라면%'
      or o.option_name like '%셀프라면%'
      or coalesce(o.source_label, '') like '%라면%'
      or o.option_name like '%라면%'
  )
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

insert into tmp_inha_student_menu_category (option_id, category_code, source_label)
select o.option_id, 'STUDENT_SIMPLE', '간편식'
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
join universities u on u.university_id = dp.university_id
join meal_type mt on mt.meal_type_id = m.meal_type_id
where u.university_code = 'INHA'
  and dp.dining_place_type = 'STUDENT'
  and mt.meal_type_code = 'LUNCH'
  and (
      coalesce(o.source_label, '') like '%간편식%'
      or o.option_name like '%간편식%'
  )
on duplicate key update
    category_code = values(category_code),
    source_label = values(source_label);

update cafeteria_menu_option
set category_id = (
        select c.category_id
        from tmp_inha_student_menu_category t
        join menu_category c on c.category_code = t.category_code
        where t.option_id = cafeteria_menu_option.option_id
    ),
    source_label = (
        select t.source_label
        from tmp_inha_student_menu_category t
        where t.option_id = cafeteria_menu_option.option_id
    )
where option_id in (
    select option_id
    from tmp_inha_student_menu_category
);

drop table if exists tmp_inha_student_menu_category;
