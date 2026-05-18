drop view if exists v_menu_option_comparison;

create view v_menu_option_comparison as
select dp.university_id,
       dp.dining_place_id,
       dp.dining_place_name,
       dp.dining_place_type,
       dp.is_active as dining_place_is_active,
       m.menu_id,
       m.served_date,
       m.meal_type,
       o.option_id,
       o.category_id,
       c.category_code,
       c.category_name,
       c.sort_order as category_sort_order,
       o.option_name,
       o.source_label,
       o.is_available,
       coalesce(o.calories_kcal,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.calories_kcal * mi.amount_g / 100), 0)
                else 0
           end) as calories_kcal,
       coalesce(o.carb_g,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.carb_g * mi.amount_g / 100), 0)
                else 0
           end) as carb_g,
       coalesce(o.protein_g,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.protein_g * mi.amount_g / 100), 0)
                else 0
           end) as protein_g,
       coalesce(o.fat_g,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.fat_g * mi.amount_g / 100), 0)
                else 0
           end) as fat_g,
       coalesce(o.sugar_g,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.sugar_g * mi.amount_g / 100), 0)
                else 0
           end) as sugar_g,
       coalesce(o.sodium_mg,
           case when sum(case when mi.food_id is not null then 1 else 0 end) > 0
                then coalesce(sum(f.sodium_mg * mi.amount_g / 100), 0)
                else 0
           end) as sodium_mg
from cafeteria_menu m
join dining_place dp on dp.dining_place_id = m.dining_place_id
join cafeteria_menu_option o on o.menu_id = m.menu_id
left join menu_category c on c.category_id = o.category_id
left join cafeteria_menu_item mi on mi.option_id = o.option_id
left join food f on f.food_id = mi.food_id
group by dp.university_id,
         dp.dining_place_id,
         dp.dining_place_name,
         dp.dining_place_type,
         dp.is_active,
         m.menu_id,
         m.served_date,
         m.meal_type,
         o.option_id,
         o.category_id,
         c.category_code,
         c.category_name,
         c.sort_order,
         o.option_name,
         o.source_label,
         o.is_available,
         o.calories_kcal,
         o.carb_g,
         o.protein_g,
         o.fat_g,
         o.sugar_g,
         o.sodium_mg;
