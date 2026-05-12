# 끼록 API 명세서 (MVP v1)

## 기본

| 항목 | 값 |
|---|---|
| Base URL | `/api/v1` |
| Content-Type | `application/json; charset=utf-8` |
| 인증 | `Authorization: Bearer <access_token>` |
| DB | MySQL, Flyway migration |
| Swagger UI | `/swagger-ui.html` |
| OpenAPI JSON | `/v3/api-docs` |
| 날짜 형식 | `YYYY-MM-DD` |

학교 이메일 인증코드는 실제 SMTP 메일로 발송한다. 로컬 실행 전에 아래 환경변수를 설정해야 한다.

| 환경변수 | 예시 |
|---|---|
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USERNAME` | `your-email@gmail.com` |
| `MAIL_PASSWORD` | Gmail 앱 비밀번호 또는 SMTP 비밀번호 |
| `MAIL_FROM` | `your-email@gmail.com` |

메일 설정이 없으면 인증코드 발급 API는 `MAIL_CONFIGURATION_REQUIRED` 오류를 반환한다.

개발용 테스트 계정:

| 항목 | 값 |
|---|---|
| 이메일 | `test@inha.edu` |
| 이름 | `test` |
| 비밀번호 | `test` |
| 학교 | 인하대학교 |

응답 형식:

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "uuid"
  }
}
```

## 1. 인증/회원

### 회원가입

`POST /auth/signup`

학교를 선택한 경우 학교 이메일 인증코드를 검증하고, 인증 성공 시 사용자의 대학교와 키/몸무게/성별 기반 BMI를 저장한다. 대학교를 선택하지 않는 회원가입도 허용한다.

대학교를 선택한 회원가입은 학교 이메일 인증코드를 먼저 발급해야 한다.

`POST /auth/school-email-verifications`

```json
{
  "email": "tester@inha.edu",
  "universityId": 2
}
```

인증코드는 입력한 학교 이메일로 발송되며, 10분 동안 유효하다.

요청:

```json
{
  "email": "tester@inha.edu",
  "verificationCode": "123456",
  "password": "P@ssw0rd!",
  "name": "테스터",
  "universityId": 2,
  "gender": "FEMALE",
  "heightCm": 164.3,
  "weightKg": 58.2
}
```

대학교를 선택하지 않는 경우에는 `universityId`, `verificationCode`를 `null` 또는 생략할 수 있다.

응답:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "universityId": 2,
    "accessToken": "opaque-token",
    "bmi": 21.56
  }
}
```

### 로그인

`POST /auth/login`

대학교를 선택한 사용자는 학교 이메일 인증이 완료되어야 로그인할 수 있다. 대학교를 선택하지 않은 사용자는 일반 계정으로 로그인할 수 있지만 식당 메뉴 비교는 사용할 수 없다.

```json
{
  "email": "tester@inha.edu",
  "password": "P@ssw0rd!"
}
```

### 로그아웃

`POST /auth/logout`

현재 bearer token을 폐기한다.

## 2. 사용자

### 내 정보 조회

`GET /users/me`

프로필, BMI, 학교 인증 상태를 반환한다.

### 건강 프로필 수정

`PUT /users/me/profile`

키/몸무게/성별을 수정하고 BMI를 다시 계산한다.

```json
{
  "gender": "FEMALE",
  "heightCm": 164.3,
  "weightKg": 57.4
}
```

## 3. 학교/식당/메뉴

### 대학교 목록

`GET /universities`

초기 데이터로 `인하대학교`를 제공하며, DB는 여러 대학교와 학교별 이메일 도메인을 지원한다. 프론트에서 대학 미선택 옵션을 그릴 수 있도록 첫 항목으로 `{"universityId": null, "universityCode": "NONE", "universityName": "선택 안함"}`을 내려준다.

### 식당 목록

`GET /dining-places?universityId=2`

### 날짜/끼니별 메뉴

`GET /menus/daily?universityId=2&date=2026-05-11&mealType=LUNCH`

학생식당 점심은 `한식`, `양식` 옵션으로 내려오고, 기숙사식당은 `단일 메뉴` 옵션으로 내려온다.

### 식당 메뉴 탄단지 비교

`GET /menus/compare?universityId=2&date=2026-05-11&mealType=LUNCH`

`Authorization: Bearer <access_token>`이 필요하다. 대학을 선택하지 않은 사용자는 `UNIVERSITY_SELECTION_REQUIRED` 오류를 받으며, 자신의 선택 대학이 아닌 학교의 메뉴 비교는 `UNIVERSITY_SELECTION_MISMATCH` 오류를 받는다. 응답의 각 옵션은 `caloriesKcal`, `carbG`, `proteinG`, `fatG`, `sugarG`, `sodiumMg` 합계를 포함한다.

### 인하대 학생식당 메뉴 크롤링

`POST /menus/crawl/inha/student`

인하대 학생식당 메뉴 페이지를 크롤링해 `cafeteria_menu`, `cafeteria_menu_option`, `cafeteria_menu_item`에 저장한다. 해당 페이지가 SSO 인증 화면을 반환하면 `INHA_MENU_REQUIRES_AUTH` 오류를 반환한다.

## 4. 음식 검색

### 음식 검색

`GET /foods/search?q=닭가슴살&limit=20`

음식명과 `food_alias`를 함께 검색한다. 프론트에서는 한글 쿼리를 URL 인코딩해야 한다.

### 음식 상세

`GET /foods/{foodId}`

기본 제공량 기준 영양 정보를 반환한다.

## 5. 식단 기록

### 식단 생성

`POST /meal-logs`

`foodId` 또는 `menuOptionId` 중 하나로 항목을 추가한다. `menuOptionId`를 넣으면 해당 식당 메뉴 옵션의 구성 음식을 식단 항목으로 펼쳐 저장한다.

```json
{
  "logDate": "2026-05-11",
  "mealType": "LUNCH",
  "memo": "학생식당 한식 선택",
  "items": [
    {
      "menuOptionId": 2021
    },
    {
      "foodId": 3001,
      "amountG": 100
    }
  ]
}
```

### 날짜별 식단 조회

`GET /meal-logs?date=2026-05-11`

### 식단 상세 조회

`GET /meal-logs/{mealLogId}`

### 식단 항목 추가

`POST /meal-logs/{mealLogId}/items`

```json
{
  "foodId": 1,
  "amountG": 200
}
```

### 식단 항목 제외/복구

`PATCH /meal-logs/{mealLogId}/items/{dietItemId}/exclude?excluded=true`

제외된 항목은 기록에는 남지만 홈 요약 계산에서는 빠진다.

## 6. 홈 요약

### 일일 영양 요약

`GET /home/daily-summary?date=2026-05-11`

그날 제외되지 않은 식단 항목의 총 열량/탄단지/당/나트륨을 계산하고, `nutrition_standard_group/value` 기준 상한을 넘으면 경고를 반환한다.

탄수화물 초과 예시:

```json
{
  "nutrientCode": "CARB_G",
  "nutrientName": "탄수화물",
  "actualAmount": 698.70,
  "recommendedAmount": 260.0000,
  "upperLimitAmount": 320.0000,
  "message": "오늘 탄수화물 섭취량이 기준 상한보다 높습니다. 다음 끼니는 밥/면류 양을 줄여보세요."
}
```

## 공통 Enum

| 항목 | 값 |
|---|---|
| `gender` | `MALE`, `FEMALE`, `OTHER` |
| `mealType` | `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` |
| `diningPlaceType` | `STUDENT`, `DORMITORY` |
| `studentVerification.status` | `PENDING`, `DOMAIN_VERIFIED`, `VERIFIED`, `REJECTED` |
