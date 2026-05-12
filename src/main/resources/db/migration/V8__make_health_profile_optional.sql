alter table user_health_profile
    modify column height_cm decimal(5,2) null;

alter table user_health_profile
    modify column weight_kg decimal(5,2) null;

alter table user_health_profile
    modify column gender varchar(20) null;

alter table user_health_profile
    modify column bmi decimal(5,2) null;
