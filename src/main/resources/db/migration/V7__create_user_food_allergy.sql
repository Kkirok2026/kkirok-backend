create table user_food_allergy (
    allergy_id bigint auto_increment primary key,
    user_id bigint not null,
    food_id bigint not null,
    reaction_note varchar(255),
    created_at timestamp not null default current_timestamp,
    unique (user_id, food_id),
    constraint fk_user_food_allergy_user
        foreign key (user_id) references user_account(user_id) on delete cascade,
    constraint fk_user_food_allergy_food
        foreign key (food_id) references food(food_id)
);

create index idx_user_food_allergy_user on user_food_allergy(user_id);
