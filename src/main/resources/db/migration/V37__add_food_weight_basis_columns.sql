alter table food
    add column nutrition_basis_amount_g decimal(8,2) not null default 100.00;

alter table food
    add column total_weight_g decimal(8,2) null;

update food
set nutrition_basis_amount_g = default_serving_g
where nutrition_basis_amount_g = 100.00
  and default_serving_g is not null;
