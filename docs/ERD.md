# 끼록 MVP ERD

이번 1차 구현은 기능보다 데이터베이스 설계를 우선해, MySQL용 Flyway migration 기반으로 구성한다. 실제 구현 SQL과 Java migration은 `src/main/resources/db/migration`, `src/main/java/db/migration`에 버전별로 저장한다.

## 반영한 설계 기준

- 학교는 사용자가 직접 선택하지 않고 이메일 도메인으로 자동 판별한다. 도메인이 등록된 학교 이메일이면 `user_account.university_id`, `student_email`, `is_student_verified`에 저장한다.
- 이메일 인증코드는 회원가입이 완료되기 전 검증이 필요하므로 `school_email_verification_code`에 해시, 만료시간, 사용시간을 저장한다.
- 로그인 세션 테이블은 제거했고, JWT 로그아웃 처리를 위해 무효화된 토큰만 `auth_token_revocation`에 저장한다.
- `diet_entry`, `diet_entry_item`은 서비스 용어에 맞춰 `meal_log`, `meal_log_item`으로 변경했다.
- `meal_type` 테이블은 제거하고 `meal_log.meal_type`, `cafeteria_menu.meal_type` 코드값으로 직접 저장한다.
- 식단 제외는 삭제가 아니라 `meal_log_item.is_excluded`로 처리해 원본 기록을 보존한다.
- 학생식당 점심의 한상한담/ONE PLATE/Noodle/셀프라면 같은 분류와 생활관식당 점심/저녁 메뉴 구분은 `cafeteria_menu_option.category_id`로 표현한다.
- 음식 별칭 검색 확장은 `food_alias` 테이블을 둬서 데이터만 추가해도 검색에 반영되도록 했다.
- 법정 알레르기 전용 테이블은 제거했고, 우유/계란/땅콩 같은 알레르기 가능 재료도 일반 `ingredient` 및 `ingredient_alias` 데이터로 관리한다.
- 사용자 알레르기는 음식과 원재료를 나누어 별도 테이블에 저장하지 않고 `user_allergy` 하나에 `FOOD`, `INGREDIENT` 타입으로 저장한다.
- 개인별 권장섭취량은 프로필 기반 공식으로 조회 시 계산하므로 `nutrition_standard_group`, `nutrition_standard_value` 테이블은 제거했다.

```mermaid
erDiagram
    UNIVERSITIES ||--o{ UNIVERSITY_EMAIL_DOMAINS : has
    UNIVERSITIES ||--o{ USER_ACCOUNT : matched_by_email
    UNIVERSITIES ||--o{ SCHOOL_EMAIL_VERIFICATION_CODE : issues
    UNIVERSITIES ||--o{ DINING_PLACE : operates

    USER_ACCOUNT ||--|| USER_HEALTH_PROFILE : has
    USER_ACCOUNT ||--o{ AUTH_TOKEN_REVOCATION : revokes
    USER_ACCOUNT ||--o{ MEAL_LOG : records
    USER_ACCOUNT ||--o{ USER_CUSTOM_FOOD : owns
    USER_ACCOUNT ||--o{ USER_ALLERGY : has

    DINING_PLACE ||--o{ CAFETERIA_MENU : publishes
    CAFETERIA_MENU ||--o{ CAFETERIA_MENU_OPTION : has
    MENU_CATEGORY ||--o{ CAFETERIA_MENU_OPTION : categorizes
    CAFETERIA_MENU_OPTION ||--o{ CAFETERIA_MENU_ITEM : includes
    CAFETERIA_MENU_OPTION ||--o{ MEAL_LOG_ITEM : source_menu

    FOOD ||--o{ FOOD_ALIAS : has
    FOOD ||--o{ CAFETERIA_MENU_ITEM : mapped_to
    FOOD ||--o{ MEAL_LOG_ITEM : logged_as
    FOOD ||--o{ USER_CUSTOM_FOOD : custom_master
    FOOD ||--o{ USER_ALLERGY : allergy_food_target
    FOOD ||--o{ FOOD_INGREDIENT : made_of

    MEAL_LOG ||--o{ MEAL_LOG_ITEM : contains

    INGREDIENT ||--o{ INGREDIENT_ALIAS : has
    INGREDIENT ||--o{ FOOD_INGREDIENT : used_in
    INGREDIENT ||--o{ USER_ALLERGY : allergy_ingredient_target

    UNIVERSITIES {
        bigint university_id PK
        varchar university_name UK
    }

    UNIVERSITY_EMAIL_DOMAINS {
        bigint domain_id PK
        bigint university_id FK
        varchar email_domain UK
    }

    USER_ACCOUNT {
        bigint user_id PK
        bigint university_id FK
        varchar email UK
        varchar password_hash
        varchar name
        int age
        varchar status
        varchar student_email
        boolean is_student_verified
    }

    SCHOOL_EMAIL_VERIFICATION_CODE {
        bigint verification_id PK
        bigint university_id FK
        varchar student_email
        varchar purpose
        varchar code_hash
        timestamp expires_at
        timestamp consumed_at
    }

    USER_HEALTH_PROFILE {
        bigint user_id PK,FK
        decimal height_cm
        decimal weight_kg
        decimal target_weight_kg
        int target_period_value
        varchar target_period_unit
        varchar gender
        decimal bmi
        varchar activity_level
    }

    AUTH_TOKEN_REVOCATION {
        varchar token_jti PK
        bigint user_id FK
        timestamp expires_at
    }

    DINING_PLACE {
        bigint dining_place_id PK
        bigint university_id FK
        varchar dining_place_name
        varchar dining_place_type
        varchar menu_source_url
        boolean is_active
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
        varchar meal_type
        date served_date
    }

    CAFETERIA_MENU_OPTION {
        bigint option_id PK
        bigint menu_id FK
        bigint category_id FK
        varchar option_name
        varchar source_label
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
        decimal calories_kcal
        decimal carb_g
        decimal protein_g
        decimal fat_g
        decimal sugar_g
        decimal sodium_mg
    }

    FOOD_ALIAS {
        bigint alias_id PK
        bigint food_id FK
        varchar alias_name
        varchar normalized_alias
        varchar alias_type
        int priority
    }

    USER_CUSTOM_FOOD {
        bigint custom_food_id PK
        bigint user_id FK
        bigint food_id FK
        varchar food_name
        varchar normalized_food_name
        decimal serving_amount_g
    }

    MEAL_LOG {
        bigint meal_log_id PK
        bigint user_id FK
        varchar meal_type
        date log_date
        varchar memo
    }

    MEAL_LOG_ITEM {
        bigint meal_log_item_id PK
        bigint meal_log_id FK
        bigint food_id FK
        bigint source_menu_option_id FK
        varchar item_name_snapshot
        decimal amount_g
        boolean is_excluded
    }

    INGREDIENT {
        bigint ingredient_id PK
        varchar source_name
        varchar source_code
        varchar ingredient_name
        varchar normalized_name UK
        varchar large_category
        varchar middle_category
        varchar english_name
    }

    INGREDIENT_ALIAS {
        bigint alias_id PK
        bigint ingredient_id FK
        varchar alias_name
        varchar normalized_alias
        varchar alias_type
    }

    FOOD_INGREDIENT {
        bigint food_id PK,FK
        bigint ingredient_id PK,FK
        varchar source_name
        varchar source_reference
        varchar raw_ingredient_name
        varchar confidence
    }

    USER_ALLERGY {
        bigint allergy_id PK
        bigint user_id FK
        varchar allergy_type
        bigint food_id FK
        bigint ingredient_id FK
        varchar allergy_name
        varchar normalized_allergy_name
        varchar reaction_note
    }
```

## 식당 메뉴 비교용 조회 View

식당 메뉴 비교 화면은 기본 테이블을 직접 모두 조인하지 않고 `v_menu_option_comparison` view를 읽는다. 저장 구조는 계속 정규화된 테이블을 기준으로 유지한다.

- `cafeteria_menu`, `dining_place`, `cafeteria_menu_option`, `menu_category`는 학교/식당/날짜/끼니/옵션 정보를 분리해 저장한다.
- `cafeteria_menu_item`은 옵션에 포함된 원문 메뉴 항목을 저장하고, 가능한 경우 `food`와 연결한다.
- `food`는 표준 음식의 100g 기준 영양값만 저장한다.
- `v_menu_option_comparison`은 비교 API가 쓰기 쉬운 읽기 전용 결과를 제공한다. 옵션 자체에 영양값이 있으면 그 값을 우선 사용하고, 없으면 메뉴 항목과 `food`를 조인해 옵션 단위 영양 합계를 계산한다.

## 핵심 제약

- `user_account.email`은 전역 유니크다.
- `user_account.age`는 1 이상 120 이하 값으로 저장한다.
- 학교 이메일 인증 결과는 `user_account.university_id`, `student_email`, `is_student_verified`에 저장한다.
- `school_email_verification_code`는 인증코드 원문이 아니라 해시만 저장한다.
- `user_health_profile.user_id`는 PK이자 FK라 사용자당 프로필은 1개만 존재한다.
- `cafeteria_menu`는 `(dining_place_id, meal_type, served_date)` 유니크다.
- `cafeteria_menu_option`은 `(menu_id, option_name)` 유니크다.
- `meal_log`는 `(user_id, meal_type, log_date)` 유니크라 하루 한 끼 기록을 하나로 모은다.
- `food_alias`는 `(food_id, normalized_alias)` 유니크다.
- `user_custom_food`는 `(user_id, normalized_food_name)` 유니크라 같은 사용자가 같은 직접 등록 음식을 중복 저장하지 않는다.
- `food`는 100g 기준 영양값(`calories_kcal`, `carb_g`, `protein_g`, `fat_g`, `sugar_g`, `sodium_mg`)을 직접 가진다.
- `user_allergy`는 `(user_id, allergy_type, normalized_allergy_name)` 유니크라 같은 타입의 알레르기를 중복 등록하지 않는다.
