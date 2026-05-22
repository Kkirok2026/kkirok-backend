alter table user_health_profile
    add column target_period_started_on date null;

update user_health_profile
set target_period_started_on = current_date
where target_period_value is not null
  and target_period_started_on is null;

alter table user_health_profile
    add constraint chk_user_health_profile_target_period_started_on
        check (
            (target_period_value is null and target_period_started_on is null)
            or (target_period_value is not null and target_period_started_on is not null)
        );
