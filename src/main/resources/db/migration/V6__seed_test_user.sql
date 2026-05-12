insert into user_account (user_id, primary_university_id, email, password_hash, name, status) values
    (1001, 2, 'test@inha.edu', '$2a$10$Waudz3CfBeYlILEU4OqZFennc0xTB36KU/y/S0KxL8HhLI7vE2cCW', 'test', 'ACTIVE');

insert into user_health_profile (user_id, height_cm, weight_kg, gender, bmi) values
    (1001, 170.00, 65.00, 'OTHER', 22.49);

insert into student_verifications (verification_id, user_id, university_id, student_email, status, verified_at) values
    (1001, 1001, 2, 'test@inha.edu', 'VERIFIED', current_timestamp);
