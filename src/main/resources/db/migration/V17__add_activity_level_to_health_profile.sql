alter table user_health_profile
    add column activity_level varchar(30) null;

update user_health_profile
set activity_level = 'LOW_ACTIVE'
where activity_level is null;

alter table user_health_profile
    add constraint chk_user_health_profile_activity_level
        check (activity_level is null or activity_level in ('SEDENTARY', 'LOW_ACTIVE', 'ACTIVE', 'VERY_ACTIVE'));
