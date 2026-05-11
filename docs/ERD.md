# 끼록 MVP ERD

이번 1차 구현은 기능보다 데이터베이스 설계를 우선해, MySQL용 `schema.sql` 기반으로 구성한다. 기존 초안 대비 주요 수정점은 다음과 같다.

- 학교 인증은 `user_account.university_id` 하나로 끝내지 않고 `universities`, `university_email_domains`, `student_verifications`로 분리했다. 현재는 한 학교만 쓰지만, 다른 대학교 도메인/인증 정책을 추가할 수 있다.
- 가입 시 입력한 키, 몸무게, 성별은 `user_health_profile`에 저장하고 BMI는 서버에서 계산해 `bmi` 컬럼에 저장한다.
- 학생식당 점심의 한식/양식 같은 분류는 `cafeteria_menu_option.category_id`로 표현한다. 기숙사식당 중식/석식은 `DEFAULT` 단일 옵션으로 저장한다.
- 식단 제외는 삭제가 아니라 `diet_entry_item.is_excluded`로 처리해 원본 기록을 보존한다.
- 음식 별칭 검색 확장은 `food_alias` 테이블을 둬서 데이터만 추가해도 검색에 반영되도록 했다.

```mermaid
erDiagram
    UNIVERSITIES ||--o{ UNIVERSITY_EMAIL_DOMAINS : has
    UNIVERSITIES ||--o{ USER_ACCOUNT : primary_school
    UNIVERSITIES ||--o{ STUDENT_VERIFICATIONS : verifies
    UNIVERSITIES ||--o{ DINING_PLACE : operates

    USER_ACCOUNT ||--|| USER_HEALTH_PROFILE : has
    USER_ACCOUNT ||--o{ STUDENT_VERIFICATIONS : owns
    USER_ACCOUNT ||--o{ AUTH_SESSIONS : logs_in
    USER_ACCOUNT ||--o{ DIET_ENTRY : records

    MEAL_TYPE ||--o{ CAFETERIA_MENU : classifies
    MEAL_TYPE ||--o{ DIET_ENTRY : classifies

    DINING_PLACE ||--o{ CAFETERIA_MENU : publishes
    CAFETERIA_MENU ||--o{ CAFETERIA_MENU_OPTION : has
    MENU_CATEGORY ||--o{ CAFETERIA_MENU_OPTION : categorizes
    CAFETERIA_MENU_OPTION ||--o{ CAFETERIA_MENU_ITEM : includes
    CAFETERIA_MENU_OPTION ||--o{ DIET_ENTRY_ITEM : source_of

    FOOD ||--o{ FOOD_ALIAS : has
    FOOD ||--o{ FOOD_NUTRIENT_VALUE : measured_as
    FOOD ||--o{ CAFETERIA_MENU_ITEM : mapped_to
    FOOD ||--o{ DIET_ENTRY_ITEM : eaten_as
    NUTRIENT ||--o{ FOOD_NUTRIENT_VALUE : defines
    NUTRIENT ||--o{ NUTRITION_STANDARD_VALUE : targets

    DIET_ENTRY ||--o{ DIET_ENTRY_ITEM : contains

    NUTRITION_STANDARD_GROUP ||--o{ NUTRITION_STANDARD_VALUE : defines

    UNIVERSITIES {
        bigint university_id PK
        varchar university_code UK
        varchar university_name UK
        boolean is_active
        timestamp created_at
    }

    UNIVERSITY_EMAIL_DOMAINS {
        bigint domain_id PK
        bigint university_id FK
        varchar email_domain UK
        varchar verification_method
        boolean is_active
    }

    USER_ACCOUNT {
        bigint user_id PK
        bigint primary_university_id FK
        varchar email UK
        varchar password_hash
        varchar name
        varchar status
        timestamp created_at
        timestamp last_login_at
    }

    STUDENT_VERIFICATIONS {
        bigint verification_id PK
        bigint user_id FK
        bigint university_id FK
        varchar student_email
        varchar status
        timestamp verified_at
    }

    USER_HEALTH_PROFILE {
        bigint user_id PK,FK
        decimal height_cm
        decimal weight_kg
        varchar gender
        decimal bmi
        timestamp updated_at
    }

    AUTH_SESSIONS {
        bigint session_id PK
        bigint user_id FK
        varchar access_token UK
        timestamp issued_at
        timestamp expires_at
        timestamp revoked_at
    }

    DINING_PLACE {
        bigint dining_place_id PK
        bigint university_id FK
        varchar dining_place_name
        varchar dining_place_type
        varchar menu_source_url
        boolean is_active
    }

    MEAL_TYPE {
        bigint meal_type_id PK
        varchar meal_type_code UK
        varchar meal_type_name
    }

    MENU_CATEGORY {
        bigint category_id PK
        varchar category_code UK
        varchar category_name
        int sort_order
    }

    CAFETERIA_MENU {
        bigint menu_id PK
        bigint dining_place_id FK
        bigint meal_type_id FK
        date served_date
        timestamp crawled_at
    }

    CAFETERIA_MENU_OPTION {
        bigint option_id PK
        bigint menu_id FK
        bigint category_id FK
        varchar option_name
        varchar source_label
        int price
        boolean is_available
    }

    CAFETERIA_MENU_ITEM {
        bigint menu_item_id PK
        bigint option_id FK
        bigint food_id FK
        varchar raw_item_name
        decimal amount_g
    }

    FOOD {
        bigint food_id PK
        varchar source_name
        varchar source_food_code
        varchar food_name
        decimal default_serving_g
        varchar source_category
    }

    FOOD_ALIAS {
        bigint alias_id PK
        bigint food_id FK
        varchar alias_name
        varchar normalized_alias
        varchar alias_type
        int priority
    }

    NUTRIENT {
        bigint nutrient_id PK
        varchar nutrient_code UK
        varchar nutrient_name
        varchar unit
        int sort_order
    }

    FOOD_NUTRIENT_VALUE {
        bigint food_id PK,FK
        bigint nutrient_id PK,FK
        decimal amount_per_100g
    }

    DIET_ENTRY {
        bigint diet_entry_id PK
        bigint user_id FK
        bigint meal_type_id FK
        date consumed_date
        varchar memo
        timestamp created_at
        timestamp updated_at
    }

    DIET_ENTRY_ITEM {
        bigint diet_item_id PK
        bigint diet_entry_id FK
        bigint food_id FK
        bigint source_option_id FK
        varchar item_name_snapshot
        decimal amount_g
        boolean is_excluded
    }

    NUTRITION_STANDARD_GROUP {
        bigint standard_group_id PK
        varchar gender
        decimal height_min_cm
        decimal height_max_cm
        decimal weight_min_kg
        decimal weight_max_kg
        decimal bmi_min
        decimal bmi_max
        varchar source_name
        varchar description
    }

    NUTRITION_STANDARD_VALUE {
        bigint standard_group_id PK,FK
        bigint nutrient_id PK,FK
        decimal recommended_amount
        decimal upper_limit_amount
        varchar basis
    }
```

## 핵심 제약

- `user_account.email`은 전역 유니크다.
- `student_verifications`는 `(university_id, student_email)` 유니크로 학교별 인증 이메일 중복을 막는다.
- `user_health_profile.user_id`는 PK이자 FK라 사용자당 프로필은 1개만 존재한다.
- `cafeteria_menu`는 `(dining_place_id, meal_type_id, served_date)` 유니크다.
- `cafeteria_menu_option`은 `(menu_id, option_name)` 유니크다.
- `diet_entry`는 `(user_id, meal_type_id, consumed_date)` 유니크라 하루 한 끼 기록을 하나로 모은다.
- `food_alias`는 `(food_id, normalized_alias)` 유니크다.
- `food_nutrient_value`와 `nutrition_standard_value`는 복합 PK로 영양소별 값을 관리한다.
