# 끼록 데이터베이스 설계 산출물

Flyway 내부 관리 테이블인 `flyway_schema_history`는 설계 산출물에서 제외한다.

## 1단계. 요구사항 명세서

### 목적

끼록은 사용자의 식단 기록, 하루 영양 분석, 알레르기 관리, 학교 식당 메뉴 비교를 제공하는 서비스다. 데이터베이스는 회원, 학교 인증, 건강 프로필, 음식 영양성분, 원재료, 식단 기록, 학교 식당 메뉴 데이터를 일관성 있게 저장하고 조회할 수 있어야 한다.

### 주요 데이터 요구사항

1. 회원은 이메일, 비밀번호, 이름, 나이를 이용해 가입한다.
2. 회원가입 시 이메일 도메인으로 대학교를 자동 판별한다.
3. 등록된 학교 도메인 사용자는 학교 회원으로 저장하고, 그 외 사용자는 일반 사용자로 저장한다.
4. 학교 회원은 학교 이메일 인증을 완료해야 학교 식당 메뉴 비교 기능을 사용할 수 있다.
5. 회원은 키, 몸무게, 목표 몸무게, 목표 기간, 성별, 활동 수준을 건강 프로필로 저장할 수 있다.
6. BMI는 키와 몸무게를 기준으로 계산되는 유도 속성이며, 프로필 조회와 권장 영양 계산에 사용한다.
7. 사용자는 날짜와 끼니별로 식단 기록을 생성하고, 여러 음식 항목을 추가할 수 있다.
8. 식단 항목은 삭제 대신 제외 처리할 수 있어야 하며, 제외 항목은 영양 합계 계산에서 빠진다.
9. 음식은 식약처 영양성분 데이터를 기반으로 저장하며, 열량, 탄수화물, 단백질, 지방, 당류, 나트륨을 관리한다.
10. 음식 영양값은 100g 기준으로 저장하고, 실제 섭취량에 따라 영양값을 계산한다.
11. 학교 식당 메뉴는 식당, 날짜, 끼니, 메뉴 옵션, 메뉴 항목 단위로 저장한다.
12. 학교 메뉴 원문은 보존하되, 식약처 음식 데이터와 매칭되는 경우에만 음식 마스터와 연결한다.
13. 사용자는 음식 또는 원재료를 알레르기 항목으로 등록할 수 있다.
14. 알레르기 경고는 사용자의 알레르기 정보와 식단 항목 또는 학교 메뉴 항목을 비교해 제공한다.
15. 외부 API 키, 메일 계정, 원본 식단 파일은 데이터베이스나 GitHub에 직접 저장하지 않는다.

### 처리 요구사항

1. 로그인 시 JWT를 발급하고, 로그아웃된 토큰은 재사용할 수 없도록 무효화 정보를 저장한다.
2. 하루 영양 합계는 식단 기록, 식단 항목, 음식 영양값을 조인해 조회 시점에 계산한다.
3. 학교 메뉴 비교 화면에서는 메뉴 옵션별 영양 합계를 빠르게 조회할 수 있어야 한다.
4. 식약처 원재료 API 결과는 필요한 경우 로컬 DB에 캐싱한다.
5. 인하대학교 학생식당 메뉴는 크롤링하여 DB에 저장하고, 주기적으로 갱신한다.

## 2단계. 개념적 설계 산출물: ERD

```mermaid
erDiagram
    UNIVERSITY ||--o{ UNIVERSITY_EMAIL_DOMAIN : has
    UNIVERSITY ||--o{ USER : belongs_to
    UNIVERSITY ||--o{ DINING_PLACE : operates
    UNIVERSITY ||--o{ SCHOOL_EMAIL_VERIFICATION : issues

    USER ||--o| HEALTH_PROFILE : has
    USER ||--o{ MEAL_LOG : records
    USER ||--o{ USER_ALLERGY : registers
    USER ||--o{ USER_CUSTOM_FOOD : owns
    USER ||--o{ AUTH_TOKEN_REVOCATION : revokes

    MEAL_LOG ||--o{ MEAL_LOG_ITEM : contains
    FOOD ||--o{ MEAL_LOG_ITEM : logged_as

    FOOD ||--o{ FOOD_ALIAS : has

    FOOD ||--o{ FOOD_INGREDIENT : made_of
    INGREDIENT ||--o{ FOOD_INGREDIENT : used_in
    INGREDIENT ||--o{ INGREDIENT_ALIAS : has

    DINING_PLACE ||--o{ CAFETERIA_MENU : publishes
    CAFETERIA_MENU ||--o{ CAFETERIA_MENU_OPTION : has
    MENU_CATEGORY ||--o{ CAFETERIA_MENU_OPTION : categorizes
    CAFETERIA_MENU_OPTION ||--o{ CAFETERIA_MENU_ITEM : includes
    FOOD ||--o{ CAFETERIA_MENU_ITEM : matched_to
    CAFETERIA_MENU_OPTION ||--o{ MEAL_LOG_ITEM : source_of

    FOOD ||--o{ USER_ALLERGY : food_target
    INGREDIENT ||--o{ USER_ALLERGY : ingredient_target

    UNIVERSITY {
        university_id
        university_name
    }

    USER {
        user_id
        email
        university_id
        student_email
        is_student_verified
    }

    HEALTH_PROFILE {
        user_id
        height_cm
        weight_kg
        bmi
        target_weight_kg
    }

    MEAL_LOG {
        meal_log_id
        user_id
        log_date
        meal_type
    }

    MEAL_LOG_ITEM {
        meal_log_item_id
        meal_log_id
        food_id
        amount_g
        is_excluded
    }

    FOOD {
        food_id
        food_name
        default_serving_g
        calories_kcal
        carb_g
        protein_g
        fat_g
        sugar_g
        sodium_mg
    }

    DINING_PLACE {
        dining_place_id
        university_id
        dining_place_type
    }

    CAFETERIA_MENU {
        menu_id
        dining_place_id
        served_date
        meal_type
    }

    CAFETERIA_MENU_OPTION {
        option_id
        menu_id
        option_name
    }

    CAFETERIA_MENU_ITEM {
        menu_item_id
        option_id
        raw_item_name
        food_id
    }

    INGREDIENT {
        ingredient_id
        ingredient_name
        normalized_name
    }

    USER_ALLERGY {
        allergy_id
        user_id
        allergy_type
        food_id
        ingredient_id
    }
```

### 개념적 설계 핵심

- 사용자는 식단 기록, 건강 프로필, 알레르기, 직접 등록 음식의 중심 개체다.
- 음식과 영양소는 다대다 관계이므로 별도 관계 개체가 필요하다.
- 음식과 원재료도 다대다 관계이므로 별도 관계 개체가 필요하다.
- 학교 식당 메뉴는 식당, 날짜별 메뉴, 메뉴 옵션, 메뉴 항목의 계층 구조로 표현한다.
- 학교 메뉴 항목은 음식 마스터와 항상 매칭되지 않으므로 원문 메뉴명과 음식 연결을 분리한다.

## 3단계. 논리적 설계 산출물: 릴레이션 스키마

밑줄 대신 `PK`, 외래키는 `FK`, 유일 속성은 `UK`로 표시한다.

### 회원 및 학교

- `UNIVERSITIES`(`university_id` PK, `university_name` UK)
- `UNIVERSITY_EMAIL_DOMAINS`(`domain_id` PK, `university_id` FK, `email_domain` UK)
- `USER_ACCOUNT`(`user_id` PK, `university_id` FK, `email` UK, `password_hash`, `name`, `status`, `age`, `student_email`, `is_student_verified`)
- `SCHOOL_EMAIL_VERIFICATION_CODE`(`verification_id` PK, `university_id` FK, `student_email`, `purpose`, `code_hash`, `expires_at`, `consumed_at`)
- `USER_HEALTH_PROFILE`(`user_id` PK/FK, `height_cm`, `weight_kg`, `gender`, `bmi`, `target_weight_kg`, `activity_level`, `target_period_value`, `target_period_unit`)
- `AUTH_TOKEN_REVOCATION`(`token_jti` PK, `user_id` FK, `expires_at`)

### 음식 및 영양

- `FOOD`(`food_id` PK, `source_name`, `source_food_code`, `food_name`, `default_serving_g`, `calories_kcal`, `carb_g`, `protein_g`, `fat_g`, `sugar_g`, `sodium_mg`)
- `FOOD_ALIAS`(`alias_id` PK, `food_id` FK, `alias_name`, `normalized_alias`, `alias_type`, `priority`)
- `USER_CUSTOM_FOOD`(`custom_food_id` PK, `user_id` FK, `food_id` FK/UK, `food_name`, `normalized_food_name`, `serving_amount_g`)

### 식단 기록

- `MEAL_LOG`(`meal_log_id` PK, `user_id` FK, `log_date`, `memo`, `meal_type`)
- `MEAL_LOG_ITEM`(`meal_log_item_id` PK, `meal_log_id` FK, `food_id` FK, `source_menu_option_id` FK, `item_name_snapshot`, `amount_g`, `is_excluded`)

### 학교 식당 메뉴

- `DINING_PLACE`(`dining_place_id` PK, `university_id` FK, `dining_place_name`, `dining_place_type`, `menu_source_url`, `is_active`)
- `MENU_CATEGORY`(`category_id` PK, `category_code` UK, `category_name`, `sort_order`)
- `CAFETERIA_MENU`(`menu_id` PK, `dining_place_id` FK, `served_date`, `meal_type`)
- `CAFETERIA_MENU_OPTION`(`option_id` PK, `menu_id` FK, `category_id` FK, `option_name`, `source_label`, `is_available`, `calories_kcal`, `carb_g`, `protein_g`, `fat_g`, `sugar_g`, `sodium_mg`)
- `CAFETERIA_MENU_ITEM`(`menu_item_id` PK, `option_id` FK, `food_id` FK, `raw_item_name`, `amount_g`)

### 원재료 및 알레르기

- `INGREDIENT`(`ingredient_id` PK, `source_name`, `source_code`, `ingredient_name`, `normalized_name` UK, `large_category`, `middle_category`, `english_name`, `scientific_name`, `region_name`, `status_name`, `use_condition`)
- `INGREDIENT_ALIAS`(`alias_id` PK, `ingredient_id` FK, `alias_name`, `normalized_alias`, `alias_type`)
- `FOOD_INGREDIENT`(`food_id` PK/FK, `ingredient_id` PK/FK, `source_name`, `source_reference`, `raw_ingredient_name` PK, `display_order`, `confidence`)
- `USER_ALLERGY`(`allergy_id` PK, `user_id` FK, `allergy_type`, `food_id` FK, `ingredient_id` FK, `allergy_name`, `normalized_allergy_name`, `reaction_note`)

### 논리적 변환 근거

- 1:N 관계는 N쪽 테이블에 외래키를 둔다. 예: `meal_log.user_id`, `cafeteria_menu.dining_place_id`.
- 1:1 관계는 종속 테이블의 기본키를 외래키로 사용한다. 예: `user_health_profile.user_id`.
- N:M 관계는 중간 릴레이션으로 변환한다. 예: `food_ingredient`.
- 다중 값 속성은 별도 릴레이션으로 분리한다. 예: `food_alias`, `ingredient_alias`.
- 학교 메뉴 원문과 음식 마스터 매칭은 선택적 관계이므로 `cafeteria_menu_item.food_id`는 nullable이다.

## 4단계. 물리적 설계 산출물: 물리적 스키마

### DBMS 및 구현 기준

- DBMS: MySQL
- 문자 데이터: 한글 메뉴명과 원재료명을 저장하므로 UTF-8 계열 문자셋 사용
- 마이그레이션: Flyway
- 기본키: 대부분 `bigint auto_increment`, 토큰 무효화는 JWT 식별자인 `varchar(80)` 사용
- 날짜: 메뉴 제공일과 식단 기록일은 `date`, 만료 시각은 `timestamp`
- 금액 정보: 최종 요구사항에서 제외되어 메뉴 옵션 가격 컬럼은 제거

### 핵심 물리 테이블 명세

| 테이블 | 핵심 물리 속성 | 주요 제약 |
|---|---|---|
| `user_account` | `user_id bigint`, `email varchar(255)`, `password_hash varchar(255)`, `age int`, `is_student_verified tinyint(1)` | `email` UNIQUE, `age` 1~120 |
| `user_health_profile` | `user_id bigint`, `height_cm decimal(5,2)`, `weight_kg decimal(5,2)`, `bmi decimal(5,2)`, `target_weight_kg decimal(5,2)` | `user_id` PK/FK, 사용자당 1개 프로필 |
| `meal_log` | `meal_log_id bigint`, `user_id bigint`, `log_date date`, `meal_type varchar(30)` | `(user_id, meal_type, log_date)` UNIQUE |
| `meal_log_item` | `meal_log_item_id bigint`, `meal_log_id bigint`, `food_id bigint`, `amount_g decimal(8,2)`, `is_excluded tinyint(1)` | `meal_log_id` FK, `amount_g > 0` |
| `food` | `food_id bigint`, `source_name varchar(100)`, `source_food_code varchar(100)`, `food_name varchar(255)`, `default_serving_g decimal(8,2)`, 영양값 `decimal(12,4)` | `(source_name, source_food_code)` UNIQUE, `default_serving_g > 0` |
| `cafeteria_menu` | `menu_id bigint`, `dining_place_id bigint`, `served_date date`, `meal_type varchar(30)` | `(dining_place_id, meal_type, served_date)` UNIQUE |
| `cafeteria_menu_option` | `option_id bigint`, `menu_id bigint`, `category_id bigint`, `option_name varchar(255)`, 영양 합계 `decimal(10,2)` | `(menu_id, option_name)` UNIQUE |
| `cafeteria_menu_item` | `menu_item_id bigint`, `option_id bigint`, `food_id bigint null`, `raw_item_name varchar(255)`, `amount_g decimal(8,2)` | `food_id` nullable, `amount_g > 0` |
| `user_allergy` | `allergy_id bigint`, `user_id bigint`, `allergy_type varchar(30)`, `food_id bigint null`, `ingredient_id bigint null`, `normalized_allergy_name varchar(255)` | `(user_id, allergy_type, normalized_allergy_name)` UNIQUE |
| `ingredient` | `ingredient_id bigint`, `ingredient_name varchar(255)`, `normalized_name varchar(255)` | `normalized_name` UNIQUE |

### 주요 인덱스

- `idx_food_name`: 음식명 검색 성능 향상
- `idx_food_alias_normalized`: 음식 별칭 검색 성능 향상
- `idx_menu_daily`: 날짜와 끼니 기준 메뉴 조회 성능 향상
- `idx_meal_log_user_date`: 사용자별 날짜 식단 조회 성능 향상
- `idx_meal_log_item_log`: 식단 상세 항목 조회 성능 향상
- `idx_user_allergy_user`: 사용자 알레르기 목록 조회 성능 향상
- `idx_ingredient_name`, `idx_ingredient_alias_normalized`: 원재료 검색 및 알레르기 매칭 성능 향상

### 유도 속성 및 저장 이유

- `user_health_profile.bmi`: 키와 몸무게로 계산되는 유도 속성이다. 프로필 조회와 권장 섭취량 계산에서 반복 사용되므로 저장한다.
- `cafeteria_menu_option.calories_kcal`, `carb_g`, `protein_g`, `fat_g`, `sugar_g`, `sodium_mg`: 메뉴 옵션에 포함된 항목들의 영양 합계다. 학교 메뉴 비교 화면에서 빠르게 표시하기 위해 옵션 단위 집계값을 저장할 수 있게 했다.
- 하루 영양 합계: `meal_log_item`과 `food`를 조인해 조회 시점에 계산한다. 식단 수정이 잦기 때문에 별도 저장하면 동기화 비용이 커진다.
- 알레르기 경고: 사용자의 알레르기와 음식/원재료/메뉴명을 비교해 조회 시점에 계산한다. 메뉴와 원재료 데이터가 계속 바뀔 수 있으므로 고정 저장하지 않는다.
