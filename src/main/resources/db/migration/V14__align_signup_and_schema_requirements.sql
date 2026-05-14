alter table user_account
    add column age int null;

alter table user_account
    add constraint chk_user_account_age check (age is null or (age >= 1 and age <= 120));

update user_account
set age = 22
where email = 'test@inha.edu'
  and age is null;

alter table universities
    drop column university_code;

alter table universities
    drop column is_active;

alter table cafeteria_menu_option
    drop column price;
