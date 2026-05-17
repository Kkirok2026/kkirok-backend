alter table user_health_profile
    add column target_period_value int null;

alter table user_health_profile
    add column target_period_unit varchar(20) null;

alter table user_health_profile
    add constraint chk_user_health_profile_target_period_value
        check (target_period_value is null or target_period_value > 0);

alter table user_health_profile
    add constraint chk_user_health_profile_target_period_unit
        check (target_period_unit is null or target_period_unit in ('WEEK', 'MONTH'));
