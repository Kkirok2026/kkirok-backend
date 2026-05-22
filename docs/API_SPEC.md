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
| 나이 | `22` |
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

이메일 도메인으로 대학교를 자동 판별한다. `inha.edu`, `inha.ac.kr`처럼 등록된 학교 이메일이면 학교 이메일 인증코드를 검증하고, 그 외 이메일은 일반 사용자로 가입한다. 키/현재 몸무게/목표 몸무게/성별은 회원가입 이후 건강 프로필 입력 단계에서 저장한다.

학교 이메일 회원가입은 학교 이메일 인증코드를 먼저 발급해야 한다.

`POST /auth/school-email-verifications`

```json
{
  "email": "tester@inha.edu"
}
```

인증코드는 입력한 학교 이메일로 발송되며, 10분 동안 유효하다. 대학교는 이메일 도메인으로 자동 판별된다.

요청:

```json
{
  "email": "tester@inha.edu",
  "verificationCode": "123456",
  "password": "P@ssw0rd!",
  "name": "테스터",
  "age": 22
}
```

일반 이메일로 가입하는 경우에는 `verificationCode`를 생략할 수 있다.

응답:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "universityId": 2,
    "accessToken": "opaque-token",
    "bmi": null,
    "profileCompleted": false
  }
}
```

### 로그인

`POST /auth/login`

학교 이메일 사용자는 학교 이메일 인증이 완료되어야 로그인할 수 있다. 일반 사용자는 로그인할 수 있지만 식당 메뉴 비교는 사용할 수 없다.

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

나이, 프로필, BMI, 학교 인증 상태를 반환한다. 프로필 입력 전에는 `profile`이 `null`, `profileCompleted`가 `false`다.

### 회원 탈퇴

`DELETE /users/me`

현재 로그인한 계정을 삭제한다. 삭제 시 계정, 토큰 무효화 기록, 건강 프로필, 학교 인증 정보, 식단 기록/항목, 학교 이메일 인증코드를 함께 삭제한다.

### 건강 프로필 수정

`PUT /users/me/profile`

회원가입 후 키/현재 몸무게/목표 몸무게/목표 기간/성별을 처음 입력하거나 수정하고 BMI를 다시 계산한다. `targetWeightKg`, `targetPeriodValue`, `targetPeriodUnit`은 선택 값이다. 목표 기간 값이 있고 단위를 생략하면 `MONTH`로 저장된다. 응답에는 목표 기간이 설정/변경된 기준일 `targetPeriodStartedOn`과 오늘 기준 남은 일수 `targetRemainingDays`가 포함된다.

```json
{
  "gender": "FEMALE",
  "heightCm": 164.3,
  "weightKg": 57.4,
  "targetWeightKg": 52.0,
  "targetPeriodValue": 3,
  "targetPeriodUnit": "MONTH"
}
```

## 3. 학교/식당/메뉴

### 대학교 목록

`GET /universities`

초기 데이터로 `인하대학교`를 제공하며, DB는 여러 대학교와 학교별 이메일 도메인을 지원한다. 회원가입 시 대학교를 직접 선택하지 않으므로 미선택 항목은 내려주지 않는다.

### 식당 목록

`GET /dining-places?universityId=2`

### 날짜/끼니별 메뉴

`GET /menus/daily?universityId=2&date=2026-05-11&mealType=LUNCH`

인하대 메뉴 구분:

| 식당 | 끼니 | 옵션 구분 |
|---|---|---|
| 생활관식당 | `LUNCH`, `DINNER` | 생활관 A/B/간편식/후식 등 PDF 원본 구분 |
| 학생식당 | `LUNCH` | 한상한담, ONE PLATE, Noodle, 셀프라면, 간편식 |
| 학생식당 | `DINNER` | 석식 |

프론트는 이 API에서 학생식당 옵션의 `optionId`를 받아 사용자가 비교할 학생식당 메뉴를 선택하게 한다.

### 식당 메뉴 탄단지 비교

`GET /menus/compare?universityId=2&date=2026-05-11&mealType=LUNCH`

`Authorization: Bearer <access_token>`이 필요하다. 일반 사용자는 `SCHOOL_EMAIL_USER_REQUIRED` 오류를 받으며, 자신의 학교가 아닌 대학교의 메뉴 비교는 `UNIVERSITY_SELECTION_MISMATCH` 오류를 받는다. 응답의 각 옵션은 `v_menu_option_comparison` view를 통해 내려간다. 이 view는 옵션 자체에 저장된 영양값이 있으면 그 값을 우선 사용하고, 없으면 요청 시점에 `cafeteria_menu_item`과 식약처 음식 영양값을 조인해 `caloriesKcal`, `carbG`, `proteinG`, `fatG`, `sugarG`, `sodiumMg` 합계를 계산한다.

사용자가 학생식당 메뉴를 하나 선택한 뒤 생활관식당과 비교할 때는 `studentOptionId`를 함께 보낸다.

`GET /menus/compare?universityId=2&date=2026-05-11&mealType=LUNCH&studentOptionId=3001`

이 경우 응답은 같은 날짜/끼니의 생활관식당 옵션들과 사용자가 선택한 학생식당 옵션만 포함한다. `studentOptionId`를 생략하면 기존처럼 해당 날짜/끼니의 모든 식당 옵션을 내려준다.

식당 메뉴 원문은 `food` 마스터에 저장하지 않는다. 메뉴 항목이 식약처 음식과 아직 매핑되지 않은 경우 해당 항목의 영양값은 0으로 계산되고, 원문 메뉴명은 메뉴 옵션/항목에 남는다.

### 인하대 학생식당 메뉴 크롤링

`POST /menus/crawl/inha/student`

인하대 학생식당 공개 메뉴 페이지(`https://www.inha.ac.kr/kr/1072/subview.do`)를 크롤링해 `cafeteria_menu`, `cafeteria_menu_option`, `cafeteria_menu_item`에 저장한다. 수동 로그인 쿠키는 사용하지 않는다. 해당 페이지가 SSO 인증 화면을 반환하면 `INHA_MENU_REQUIRES_AUTH` 오류를 반환한다.

자동 크롤링은 매주 월요일 오전 6시(`Asia/Seoul`)에 1회 실행된다. 위 API는 자동 크롤링 실패 또는 수동 갱신이 필요할 때 다시 실행하는 용도다.

## 4. 음식 검색

### 음식 검색

`GET /foods/search?q=닭가슴살&limit=20`

DB에 저장된 전국통합식품영양성분정보 표준데이터 음식 마스터(`NATIONAL_INTEGRATED`)와 내 직접 입력 음식(`USER_CUSTOM`)을 검색한다. DB 결과가 요청 limit보다 부족하면 전국통합식품영양성분정보 표준데이터 OpenAPI를 호출해 정확명 검색 결과를 저장하고, 그래도 부족하면 OpenAPI 전체 페이지를 읽어 `foodNm` 포함 결과를 저장한 뒤 다시 반환한다. API 응답에서 같은 `foodCd`가 반복되면 첫 번째 행만 저장하고, 표준데이터는 `sourceFoodCode`가 다르면 같은 음식명도 별도 결과로 반환한다. 식당 메뉴 원문은 검색 결과에 포함하지 않는다. 프론트에서는 한글 쿼리를 URL 인코딩해야 한다.

### 음식 검색어 추천

`GET /foods/suggestions?q=닭가&limit=10`

로컬 `food.food_name`, `food_alias.alias_name`에서 중복을 제거해 추천 검색어 문자열을 반환한다.

### 내 직접 입력 음식 등록

`POST /foods/custom`

검색 결과에 없는 음식을 사용자의 개인 음식으로 등록한다. `amountG`는 입력한 영양값의 기준량이며 생략하면 100g으로 저장한다. 열량은 입력받지 않아도 되며, 서버가 `carbG * 4 + proteinG * 4 + fatG * 9` 공식으로 자동 계산한다. 기존 클라이언트 호환을 위해 `caloriesKcal`을 보내도 요청은 받을 수 있지만 저장 열량은 탄수화물/단백질/지방 값으로 계산한다.

```json
{
  "foodName": "저지방 우유",
  "amountG": 200,
  "carbG": 10,
  "proteinG": 6,
  "fatG": 2,
  "sugarG": 8,
  "sodiumMg": 100
}
```

### 음식 상세

`GET /foods/{foodId}`

기본 제공량 기준 영양 정보를 반환한다.

## 5. 식단 기록

프론트 사용자 흐름:

1. 사용자가 식단 생성하기를 누른다.
2. `POST /meal-logs`로 빈 식단 기록을 만든다.
3. 사용자가 음식을 검색한다.
4. 검색 결과에서 하나 이상의 음식을 선택한다.
5. `POST /meal-logs/{mealLogId}/food-items`로 선택한 음식들을 추가한다.
6. 다른 음식을 더 추가할 때도 같은 식단의 `mealLogId`로 3-5번을 반복한다.

### 식단 기록 생성

`POST /meal-logs`

사용자가 식단 생성하기를 눌렀을 때 호출한다. 이 API는 날짜/끼니/메모만 저장하고, 음식은 아직 추가하지 않는다.

```json
{
  "logDate": "2026-05-13",
  "mealType": "LUNCH",
  "memo": "점심 기록"
}
```

응답의 `mealLogId`를 이후 음식 추가 API에 사용한다. 생성 직후에는 `items`가 빈 배열이고 `totals`는 0이다.

```json
{
  "mealLogId": 55,
  "logDate": "2026-05-13",
  "mealType": "LUNCH",
  "memo": "점심 기록",
  "items": [],
  "totals": {
    "caloriesKcal": 0.00,
    "carbG": 0.00,
    "proteinG": 0.00,
    "fatG": 0.00,
    "sugarG": 0.00,
    "sodiumMg": 0.00
  }
}
```

### 날짜별 식단 조회

`GET /meal-logs?date=2026-05-11`

### 식단 상세 조회

`GET /meal-logs/{mealLogId}`

### 검색한 음식들을 식단에 추가

`POST /meal-logs/{mealLogId}/food-items`

사용자가 음식 검색 결과에서 하나 이상 선택하고 식단 추가하기를 눌렀을 때 호출한다. 같은 식단에 음식을 더 추가할 때도 이 API를 반복 호출한다.

```json
{
  "items": [
    {
      "foodId": 3101,
      "amountG": 100
    },
    {
      "foodId": 3102,
      "amountG": 80
    }
  ]
}
```

`foodId`는 `GET /foods/search` 응답에서 받은 값을 사용한다. `amountG`를 생략하면 음식의 기본 제공량이 사용된다.

응답은 갱신된 식단 상세다. 방금 추가한 음식 목록과 영양 합계 `totals`가 함께 내려간다.

### 식단 항목 제외/복구

`PATCH /meal-logs/{mealLogId}/items/{mealLogItemId}/exclude?excluded=true`

제외된 항목은 기록에는 남지만 홈 요약 계산에서는 빠진다.

## 6. 홈 요약

### 일일 영양 요약

`GET /home/daily-summary?date=2026-05-11`

메인 홈 화면에서 사용하는 API다. 그날 제외되지 않은 식단 항목의 총 칼로리, 탄수화물, 단백질, 지방을 조회 시점에 계산해 반환한다. 당과 나트륨도 함께 내려간다.

총합은 DB에 별도로 저장하지 않고 `meal_log`, `meal_log_item`, `food`를 조인해 계산한다. 음식별 영양값은 `food` 테이블의 100g 기준 컬럼(`calories_kcal`, `carb_g`, `protein_g`, `fat_g`, `sugar_g`, `sodium_mg`)에 저장한다. 사용자가 식단을 추가/제외하면 다음 조회 때 자동으로 최신 값이 반영된다.

```json
{
  "date": "2026-05-11",
  "totals": {
    "caloriesKcal": 680.40,
    "carbG": 92.10,
    "proteinG": 28.30,
    "fatG": 21.70,
    "sugarG": 7.20,
    "sodiumMg": 1280.00
  },
  "warnings": []
}
```

사용자 건강 프로필이 있으면 나이, 성별, 키, 몸무게, 활동수준을 바탕으로 권장 섭취량을 조회 시점에 계산하고, 권장 범위를 넘을 때 경고를 반환한다. 프로필이 없으면 `recommendedTargets`는 `null`, `warnings`는 빈 배열이다.

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
| `studentVerification.status` | `VERIFIED` 또는 `null` |
