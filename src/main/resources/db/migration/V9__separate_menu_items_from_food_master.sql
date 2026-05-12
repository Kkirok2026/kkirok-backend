alter table diet_entry_item modify column food_id bigint null;

update diet_entry_item
set food_id = null
where food_id in (
    select food_id
    from food
    where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU')
);

update cafeteria_menu_item
set food_id = null
where food_id in (
    select food_id
    from food
    where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU')
);

delete from user_food_allergy
where food_id in (
    select food_id
    from food
    where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU')
);

delete from food_alias
where food_id in (
    select food_id
    from food
    where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU')
);

delete from food_nutrient_value
where food_id in (
    select food_id
    from food
    where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU')
);

delete from food
where source_name in ('INHA_DORM_MENU', 'INHA_STUDENT_MENU');
