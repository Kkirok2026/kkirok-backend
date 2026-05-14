alter table user_custom_food
    add column normalized_food_name varchar(255) null after food_name;

update user_custom_food
set normalized_food_name = lower(replace(food_name, ' ', ''))
where normalized_food_name is null;

alter table user_custom_food
    modify normalized_food_name varchar(255) not null;

alter table user_custom_food
    add constraint uq_user_custom_food_user_normalized_name unique (user_id, normalized_food_name);
