create table universities (
    university_id bigint auto_increment primary key,
    university_code varchar(50) not null unique,
    university_name varchar(200) not null unique,
    is_active boolean not null default true,
    created_at timestamp not null default current_timestamp
);

create table university_email_domains (
    domain_id bigint auto_increment primary key,
    university_id bigint not null,
    email_domain varchar(255) not null unique,
    verification_method varchar(50) not null default 'SCHOOL_EMAIL',
    is_active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    constraint fk_university_email_domains_university
        foreign key (university_id) references universities(university_id)
);

create table user_account (
    user_id bigint auto_increment primary key,
    primary_university_id bigint,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    name varchar(100) not null,
    status varchar(30) not null default 'ACTIVE',
    created_at timestamp not null default current_timestamp,
    last_login_at timestamp null,
    constraint fk_user_account_primary_university
        foreign key (primary_university_id) references universities(university_id)
);

create table student_verifications (
    verification_id bigint auto_increment primary key,
    user_id bigint not null,
    university_id bigint not null,
    student_email varchar(255) not null,
    status varchar(30) not null,
    verified_at timestamp null,
    created_at timestamp not null default current_timestamp,
    unique (university_id, student_email),
    check (status in ('PENDING', 'DOMAIN_VERIFIED', 'VERIFIED', 'REJECTED')),
    constraint fk_student_verifications_user
        foreign key (user_id) references user_account(user_id) on delete cascade,
    constraint fk_student_verifications_university
        foreign key (university_id) references universities(university_id)
);

create table user_health_profile (
    user_id bigint primary key,
    height_cm decimal(5,2) not null,
    weight_kg decimal(5,2) not null,
    gender varchar(20) not null,
    bmi decimal(5,2) not null,
    updated_at timestamp not null default current_timestamp,
    check (height_cm > 0),
    check (weight_kg > 0),
    check (gender in ('MALE', 'FEMALE', 'OTHER')),
    constraint fk_user_health_profile_user
        foreign key (user_id) references user_account(user_id) on delete cascade
);

create table auth_sessions (
    session_id bigint auto_increment primary key,
    user_id bigint not null,
    access_token varchar(120) not null unique,
    issued_at timestamp not null default current_timestamp,
    expires_at timestamp not null,
    revoked_at timestamp null,
    constraint fk_auth_sessions_user
        foreign key (user_id) references user_account(user_id) on delete cascade
);

create table meal_type (
    meal_type_id bigint auto_increment primary key,
    meal_type_code varchar(30) not null unique,
    meal_type_name varchar(100) not null
);

create table dining_place (
    dining_place_id bigint auto_increment primary key,
    university_id bigint not null,
    dining_place_name varchar(255) not null,
    dining_place_type varchar(30) not null,
    menu_source_url varchar(500),
    is_active boolean not null default true,
    created_at timestamp not null default current_timestamp,
    unique (university_id, dining_place_name),
    check (dining_place_type in ('STUDENT', 'DORMITORY')),
    constraint fk_dining_place_university
        foreign key (university_id) references universities(university_id)
);

create table menu_category (
    category_id bigint auto_increment primary key,
    category_code varchar(50) not null unique,
    category_name varchar(100) not null,
    sort_order int not null default 0
);

create table cafeteria_menu (
    menu_id bigint auto_increment primary key,
    dining_place_id bigint not null,
    meal_type_id bigint not null,
    served_date date not null,
    crawled_at timestamp not null default current_timestamp,
    unique (dining_place_id, meal_type_id, served_date),
    constraint fk_cafeteria_menu_dining_place
        foreign key (dining_place_id) references dining_place(dining_place_id) on delete cascade,
    constraint fk_cafeteria_menu_meal_type
        foreign key (meal_type_id) references meal_type(meal_type_id)
);

create table cafeteria_menu_option (
    option_id bigint auto_increment primary key,
    menu_id bigint not null,
    category_id bigint,
    option_name varchar(255) not null,
    source_label varchar(255),
    price int,
    is_available boolean not null default true,
    unique (menu_id, option_name),
    constraint fk_cafeteria_menu_option_menu
        foreign key (menu_id) references cafeteria_menu(menu_id) on delete cascade,
    constraint fk_cafeteria_menu_option_category
        foreign key (category_id) references menu_category(category_id)
);

create table food (
    food_id bigint auto_increment primary key,
    source_name varchar(100) not null,
    source_food_code varchar(100),
    food_name varchar(255) not null,
    default_serving_g decimal(8,2) not null,
    source_category varchar(100),
    created_at timestamp not null default current_timestamp,
    unique (source_name, source_food_code),
    check (default_serving_g > 0)
);

create table food_alias (
    alias_id bigint auto_increment primary key,
    food_id bigint not null,
    alias_name varchar(255) not null,
    normalized_alias varchar(255) not null,
    alias_type varchar(50) not null default 'SEARCH',
    priority int not null default 0,
    unique (food_id, normalized_alias),
    constraint fk_food_alias_food
        foreign key (food_id) references food(food_id) on delete cascade
);

create table nutrient (
    nutrient_id bigint auto_increment primary key,
    nutrient_code varchar(50) not null unique,
    nutrient_name varchar(100) not null,
    unit varchar(20) not null,
    sort_order int not null default 0
);

create table food_nutrient_value (
    food_id bigint not null,
    nutrient_id bigint not null,
    amount_per_100g decimal(12,4) not null,
    primary key (food_id, nutrient_id),
    check (amount_per_100g >= 0),
    constraint fk_food_nutrient_value_food
        foreign key (food_id) references food(food_id) on delete cascade,
    constraint fk_food_nutrient_value_nutrient
        foreign key (nutrient_id) references nutrient(nutrient_id)
);

create table cafeteria_menu_item (
    menu_item_id bigint auto_increment primary key,
    option_id bigint not null,
    food_id bigint,
    raw_item_name varchar(255) not null,
    amount_g decimal(8,2) not null,
    check (amount_g > 0),
    constraint fk_cafeteria_menu_item_option
        foreign key (option_id) references cafeteria_menu_option(option_id) on delete cascade,
    constraint fk_cafeteria_menu_item_food
        foreign key (food_id) references food(food_id)
);

create table diet_entry (
    diet_entry_id bigint auto_increment primary key,
    user_id bigint not null,
    meal_type_id bigint not null,
    consumed_date date not null,
    memo varchar(500),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    unique (user_id, meal_type_id, consumed_date),
    constraint fk_diet_entry_user
        foreign key (user_id) references user_account(user_id) on delete cascade,
    constraint fk_diet_entry_meal_type
        foreign key (meal_type_id) references meal_type(meal_type_id)
);

create table diet_entry_item (
    diet_item_id bigint auto_increment primary key,
    diet_entry_id bigint not null,
    food_id bigint not null,
    source_option_id bigint,
    item_name_snapshot varchar(255) not null,
    amount_g decimal(8,2) not null,
    is_excluded boolean not null default false,
    created_at timestamp not null default current_timestamp,
    check (amount_g > 0),
    constraint fk_diet_entry_item_entry
        foreign key (diet_entry_id) references diet_entry(diet_entry_id) on delete cascade,
    constraint fk_diet_entry_item_food
        foreign key (food_id) references food(food_id),
    constraint fk_diet_entry_item_source_option
        foreign key (source_option_id) references cafeteria_menu_option(option_id)
);

create table nutrition_standard_group (
    standard_group_id bigint auto_increment primary key,
    gender varchar(20) not null,
    height_min_cm decimal(5,2),
    height_max_cm decimal(5,2),
    weight_min_kg decimal(6,2),
    weight_max_kg decimal(6,2),
    bmi_min decimal(5,2),
    bmi_max decimal(5,2),
    source_name varchar(200) not null,
    description varchar(500),
    created_at timestamp not null default current_timestamp,
    check (gender in ('MALE', 'FEMALE', 'OTHER', 'ALL'))
);

create table nutrition_standard_value (
    standard_group_id bigint not null,
    nutrient_id bigint not null,
    recommended_amount decimal(12,4) not null,
    upper_limit_amount decimal(12,4),
    basis varchar(100) not null,
    primary key (standard_group_id, nutrient_id),
    check (recommended_amount >= 0),
    check (upper_limit_amount is null or upper_limit_amount >= recommended_amount),
    constraint fk_nutrition_standard_value_group
        foreign key (standard_group_id) references nutrition_standard_group(standard_group_id) on delete cascade,
    constraint fk_nutrition_standard_value_nutrient
        foreign key (nutrient_id) references nutrient(nutrient_id)
);

create index idx_auth_sessions_token on auth_sessions(access_token);
create index idx_food_name on food(food_name);
create index idx_food_alias_normalized on food_alias(normalized_alias);
create index idx_menu_daily on cafeteria_menu(served_date, meal_type_id);
create index idx_diet_entry_user_date on diet_entry(user_id, consumed_date);
create index idx_diet_entry_item_entry on diet_entry_item(diet_entry_id);
