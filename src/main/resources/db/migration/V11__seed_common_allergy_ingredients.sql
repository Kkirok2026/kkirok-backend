insert into ingredient (source_name, source_code, ingredient_name, normalized_name)
select 'ALLERGEN_KEYWORD',
       concat('ALLERGEN_', a.allergen_code, '_', ak.keyword_id),
       ak.keyword,
       ak.normalized_keyword
from allergen_keyword ak
join allergen a on a.allergen_id = ak.allergen_id
where not exists (
    select 1
    from ingredient i
    where i.normalized_name = ak.normalized_keyword
);

insert into ingredient (source_name, source_code, ingredient_name, normalized_name)
select 'COMMON_INGREDIENT', 'COMMON_PUMPKIN', '호박', '호박'
where not exists (select 1 from ingredient where normalized_name = '호박');

insert into ingredient (source_name, source_code, ingredient_name, normalized_name)
select 'COMMON_INGREDIENT', 'COMMON_SWEET_PUMPKIN', '단호박', '단호박'
where not exists (select 1 from ingredient where normalized_name = '단호박');

insert into ingredient (source_name, source_code, ingredient_name, normalized_name)
select 'COMMON_INGREDIENT', 'COMMON_GREEN_PUMPKIN', '애호박', '애호박'
where not exists (select 1 from ingredient where normalized_name = '애호박');

insert into ingredient_alias (ingredient_id, alias_name, normalized_alias, alias_type)
select i.ingredient_id, i.ingredient_name, i.normalized_name, 'SEED'
from ingredient i
where i.source_name in ('ALLERGEN_KEYWORD', 'COMMON_INGREDIENT')
  and not exists (
      select 1
      from ingredient_alias ia
      where ia.ingredient_id = i.ingredient_id
        and ia.normalized_alias = i.normalized_name
  );

insert into ingredient_allergen (ingredient_id, allergen_id, match_basis)
select i.ingredient_id, ak.allergen_id, 'ALLERGEN_KEYWORD_SEED'
from ingredient i
join allergen_keyword ak on ak.normalized_keyword = i.normalized_name
where not exists (
    select 1
    from ingredient_allergen ia
    where ia.ingredient_id = i.ingredient_id
      and ia.allergen_id = ak.allergen_id
);
