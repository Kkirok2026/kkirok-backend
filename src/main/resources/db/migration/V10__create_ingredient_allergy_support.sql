create table ingredient (
    ingredient_id bigint auto_increment primary key,
    source_name varchar(100) not null,
    source_code varchar(100),
    ingredient_name varchar(255) not null,
    normalized_name varchar(255) not null unique,
    large_category varchar(255),
    middle_category varchar(255),
    english_name varchar(500),
    scientific_name varchar(1000),
    region_name varchar(255),
    status_name varchar(255),
    use_condition varchar(1000),
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create table ingredient_alias (
    alias_id bigint auto_increment primary key,
    ingredient_id bigint not null,
    alias_name varchar(255) not null,
    normalized_alias varchar(255) not null,
    alias_type varchar(50) not null default 'SEARCH',
    created_at timestamp not null default current_timestamp,
    unique (ingredient_id, normalized_alias),
    constraint fk_ingredient_alias_ingredient
        foreign key (ingredient_id) references ingredient(ingredient_id) on delete cascade
);

create table allergen (
    allergen_id bigint auto_increment primary key,
    allergen_code varchar(50) not null unique,
    allergen_name varchar(100) not null unique,
    is_legal_required boolean not null default true,
    created_at timestamp not null default current_timestamp
);

create table allergen_keyword (
    keyword_id bigint auto_increment primary key,
    allergen_id bigint not null,
    keyword varchar(100) not null,
    normalized_keyword varchar(100) not null,
    unique (allergen_id, normalized_keyword),
    constraint fk_allergen_keyword_allergen
        foreign key (allergen_id) references allergen(allergen_id) on delete cascade
);

create table ingredient_allergen (
    ingredient_id bigint not null,
    allergen_id bigint not null,
    match_basis varchar(100) not null default 'KEYWORD',
    primary key (ingredient_id, allergen_id),
    constraint fk_ingredient_allergen_ingredient
        foreign key (ingredient_id) references ingredient(ingredient_id) on delete cascade,
    constraint fk_ingredient_allergen_allergen
        foreign key (allergen_id) references allergen(allergen_id) on delete cascade
);

create table food_ingredient (
    food_id bigint not null,
    ingredient_id bigint not null,
    source_name varchar(100) not null,
    source_reference varchar(255),
    raw_ingredient_name varchar(500) not null,
    display_order int,
    confidence varchar(30) not null default 'API_REPORTED',
    created_at timestamp not null default current_timestamp,
    primary key (food_id, ingredient_id, raw_ingredient_name),
    constraint fk_food_ingredient_food
        foreign key (food_id) references food(food_id) on delete cascade,
    constraint fk_food_ingredient_ingredient
        foreign key (ingredient_id) references ingredient(ingredient_id) on delete cascade
);

create table user_ingredient_allergy (
    allergy_id bigint auto_increment primary key,
    user_id bigint not null,
    ingredient_id bigint,
    allergy_name varchar(255) not null,
    normalized_allergy_name varchar(255) not null,
    reaction_note varchar(255),
    created_at timestamp not null default current_timestamp,
    unique (user_id, normalized_allergy_name),
    constraint fk_user_ingredient_allergy_user
        foreign key (user_id) references user_account(user_id) on delete cascade,
    constraint fk_user_ingredient_allergy_ingredient
        foreign key (ingredient_id) references ingredient(ingredient_id)
);

insert into allergen (allergen_id, allergen_code, allergen_name, is_legal_required) values
    (1, 'EGG', '알류', true),
    (2, 'MILK', '우유', true),
    (3, 'BUCKWHEAT', '메밀', true),
    (4, 'PEANUT', '땅콩', true),
    (5, 'SOYBEAN', '대두', true),
    (6, 'WHEAT', '밀', true),
    (7, 'MACKEREL', '고등어', true),
    (8, 'CRAB', '게', true),
    (9, 'SHRIMP', '새우', true),
    (10, 'PORK', '돼지고기', true),
    (11, 'PEACH', '복숭아', true),
    (12, 'TOMATO', '토마토', true),
    (13, 'SULFITE', '아황산류', true),
    (14, 'WALNUT', '호두', true),
    (15, 'CHICKEN', '닭고기', true),
    (16, 'BEEF', '쇠고기', true),
    (17, 'SQUID', '오징어', true),
    (18, 'SHELLFISH', '조개류', true),
    (19, 'PINE_NUT', '잣', true);

insert into allergen_keyword (allergen_id, keyword, normalized_keyword) values
    (1, '계란', '계란'), (1, '달걀', '달걀'), (1, '난류', '난류'), (1, '알류', '알류'),
    (2, '우유', '우유'), (2, '유청', '유청'), (2, '분유', '분유'), (2, '치즈', '치즈'), (2, '버터', '버터'),
    (3, '메밀', '메밀'),
    (4, '땅콩', '땅콩'),
    (5, '대두', '대두'), (5, '콩', '콩'), (5, '두부', '두부'), (5, '두유', '두유'), (5, '간장', '간장'), (5, '된장', '된장'),
    (6, '밀', '밀'), (6, '소맥', '소맥'), (6, '밀가루', '밀가루'), (6, '소맥분', '소맥분'),
    (7, '고등어', '고등어'),
    (8, '게', '게'), (8, '꽃게', '꽃게'),
    (9, '새우', '새우'), (9, '쉬림프', '쉬림프'),
    (10, '돼지고기', '돼지고기'), (10, '돈육', '돈육'), (10, '돼지', '돼지'),
    (11, '복숭아', '복숭아'),
    (12, '토마토', '토마토'),
    (13, '아황산', '아황산'), (13, '이산화황', '이산화황'), (13, '메타중아황산', '메타중아황산'),
    (14, '호두', '호두'),
    (15, '닭고기', '닭고기'), (15, '닭', '닭'), (15, '계육', '계육'),
    (16, '쇠고기', '쇠고기'), (16, '소고기', '소고기'), (16, '우육', '우육'),
    (17, '오징어', '오징어'),
    (18, '조개', '조개'), (18, '굴', '굴'), (18, '전복', '전복'), (18, '홍합', '홍합'), (18, '바지락', '바지락'),
    (19, '잣', '잣');

create index idx_ingredient_name on ingredient(ingredient_name);
create index idx_ingredient_alias_normalized on ingredient_alias(normalized_alias);
create index idx_food_ingredient_food on food_ingredient(food_id);
create index idx_user_ingredient_allergy_user on user_ingredient_allergy(user_id);
