create table user_custom_food (
    custom_food_id bigint auto_increment primary key,
    user_id bigint not null,
    food_id bigint not null unique,
    food_name varchar(255) not null,
    serving_amount_g decimal(8,2) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp,
    check (serving_amount_g > 0),
    constraint fk_user_custom_food_user
        foreign key (user_id) references user_account(user_id) on delete cascade,
    constraint fk_user_custom_food_food
        foreign key (food_id) references food(food_id) on delete cascade
);

create index idx_user_custom_food_user on user_custom_food(user_id);
