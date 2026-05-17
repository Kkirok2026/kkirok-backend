drop table if exists tmp_inha_student_seed_options;

create table tmp_inha_student_seed_options (
    option_id bigint not null primary key
);

insert into tmp_inha_student_seed_options (option_id)
select o.option_id
from cafeteria_menu_option o
join cafeteria_menu m on m.menu_id = o.menu_id
join dining_place dp on dp.dining_place_id = m.dining_place_id
where dp.university_id = 2
  and dp.dining_place_type = 'STUDENT'
  and m.served_date between '2026-05-11' and '2026-05-17'
  and o.source_label like '2026-05-% 학생식당 %';

update meal_log_item
set source_menu_option_id = null
where source_menu_option_id in (
    select option_id
    from tmp_inha_student_seed_options
);

delete from cafeteria_menu_item
where option_id in (
    select option_id
    from tmp_inha_student_seed_options
);

delete from cafeteria_menu_option
where option_id in (
    select option_id
    from tmp_inha_student_seed_options
);

delete from cafeteria_menu
where menu_id in (
    select menu_id
    from (
        select m.menu_id
        from cafeteria_menu m
        join dining_place dp on dp.dining_place_id = m.dining_place_id
        where dp.university_id = 2
          and dp.dining_place_type = 'STUDENT'
          and m.served_date between '2026-05-11' and '2026-05-17'
          and not exists (
              select 1
              from cafeteria_menu_option o
              where o.menu_id = m.menu_id
          )
    ) empty_seed_menus
);

drop table if exists tmp_inha_student_seed_options;
