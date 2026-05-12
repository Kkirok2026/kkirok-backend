create table if not exists school_email_verification_code (
    verification_id bigint auto_increment primary key,
    university_id bigint not null,
    student_email varchar(255) not null,
    purpose varchar(30) not null default 'SIGNUP',
    code_hash varchar(255) not null,
    expires_at timestamp not null,
    consumed_at timestamp null,
    created_at timestamp not null default current_timestamp,
    check (purpose in ('SIGNUP')),
    constraint fk_school_email_verification_code_university
        foreign key (university_id) references universities(university_id)
);

delete from student_verifications where university_id = 1;

update user_account
set primary_university_id = null
where primary_university_id = 1;

delete from cafeteria_menu_item
where option_id in (
    select o.option_id
    from cafeteria_menu_option o
    join cafeteria_menu m on m.menu_id = o.menu_id
    join dining_place dp on dp.dining_place_id = m.dining_place_id
    where dp.university_id = 1
);

delete from cafeteria_menu_option
where menu_id in (
    select m.menu_id
    from cafeteria_menu m
    join dining_place dp on dp.dining_place_id = m.dining_place_id
    where dp.university_id = 1
);

delete from cafeteria_menu
where dining_place_id in (
    select dining_place_id
    from dining_place
    where university_id = 1
);

delete from dining_place where university_id = 1;
delete from university_email_domains where university_id = 1;
delete from universities where university_id = 1;

delete from food_alias
where food_id in (
    select food_id
    from food
    where source_name = 'PUBLIC_SAMPLE'
);

delete from food_nutrient_value
where food_id in (
    select food_id
    from food
    where source_name = 'PUBLIC_SAMPLE'
);

delete from food where source_name = 'PUBLIC_SAMPLE';
