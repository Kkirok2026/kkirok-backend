# 끼록 API 명세서 (v1)

## 1) 기본 규약

| 항목 | 값 |
|---|---|
| Base URL | `/api/v1` |
| Content-Type | `application/json; charset=utf-8` |
| 인증 | `Authorization: Bearer <access_token>` |
| 시간대 | `Asia/Seoul` |
| 날짜 형식 | `YYYY-MM-DD` |
| 일시 형식 | ISO 8601 (`2026-03-31T13:00:00+09:00`) |

## 2) 공통 응답 형식

### 성공

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "3f4c9f5b-6a7d-4f18-88fa-3dbe3f627f61"
  }
}
```

### 실패

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "mealType is required",
    "details": [
      {
        "field": "mealType",
        "reason": "must not be blank"
      }
    ]
  },
  "meta": {
    "requestId": "3f4c9f5b-6a7d-4f18-88fa-3dbe3f627f61"
  }
}
```

## 3) 공통 Enum

| 항목 | 값 |
|---|---|
| `gender` | `MALE`, `FEMALE`, `OTHER` |
| `activityLevel` | `LOW`, `LIGHT`, `MODERATE`, `HIGH`, `VERY_HIGH` |
| `goalType` | `LOSS`, `MAINTAIN`, `GAIN`, `HEALTH` |
| `targetStatus` | `ACTIVE`, `ACHIEVED`, `ENDED` |
| `mealType` | `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` |
| `severityLevel` | `LOW`, `MEDIUM`, `HIGH` |
| `riskType` | `CONTAINS`, `MAY_CONTAIN`, `CROSS_CONTACT` |
| `entrySource` | `FOOD_SEARCH`, `DINING_MENU`, `MANUAL` |
| `recommendationStatus` | `RECOMMENDED`, `CAUTION`, `EXCLUDED` |
| `suggestionType` | `ADD`, `REDUCE`, `REPLACE`, `AVOID` |
| `nutrientStatus` | `LOW`, `NORMAL`, `HIGH` |

## 4) 인증/회원

### 4-1. 회원가입

`POST /auth/signup`

요청:

```json
{
  "email": "user@univ.ac.kr",
  "password": "P@ssw0rd!",
  "name": "홍길동"
}
```

응답 `201`:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "accessToken": "<jwt>",
    "refreshToken": "<jwt>"
  }
}
```

### 4-2. 로그인

`POST /auth/login`

### 4-3. 토큰 재발급

`POST /auth/refresh`

### 4-4. 로그아웃

`POST /auth/logout`

## 5) 사용자 프로필/캠퍼스

### 5-1. 내 정보 조회

`GET /users/me`

응답:

```json
{
  "success": true,
  "data": {
    "userId": 1,
    "email": "user@univ.ac.kr",
    "name": "홍길동",
    "profile": {
      "birthDate": "2002-03-02",
      "gender": "FEMALE",
      "heightCm": 164.3,
      "currentWeightKg": 58.2,
      "activityLevel": "MODERATE"
    },
    "primaryCampus": {
      "campusId": 10,
      "campusName": "서울캠퍼스"
    }
  }
}
```

### 5-2. 프로필 생성/수정

`PUT /users/me/profile`

요청:

```json
{
  "birthDate": "2002-03-02",
  "gender": "FEMALE",
  "heightCm": 164.3,
  "currentWeightKg": 58.2,
  "activityLevel": "MODERATE"
}
```

### 5-3. 내 캠퍼스 이력 조회

`GET /users/me/campuses`

### 5-4. 캠퍼스 소속 추가/변경

`POST /users/me/campuses`

요청:

```json
{
  "campusId": 10,
  "isPrimary": true,
  "effectiveFrom": "2026-03-01"
}
```

## 6) 목표/체중/알레르기

### 6-1. 목표 목록 조회

`GET /users/me/targets?status=ACTIVE|ACHIEVED|ENDED`

### 6-2. 목표 생성 (활성 목표 교체)

`POST /users/me/targets`

설명: 새 목표 생성 시 기존 `ACTIVE` 목표는 자동으로 `ENDED` 처리.

요청:

```json
{
  "goalType": "LOSS",
  "startWeightKg": 58.2,
  "targetWeightKg": 54.0,
  "targetCalorieKcal": 1600,
  "targetCarbG": 180.0,
  "targetProteinG": 95.0,
  "targetFatG": 45.0,
  "sodiumLimitMg": 1800,
  "effectiveFrom": "2026-04-01"
}
```

### 6-3. 목표 달성 처리

`PATCH /users/me/targets/{targetId}/achieve`

### 6-4. 내 체중 기록 조회

`GET /users/me/weights?from=2026-03-01&to=2026-03-31`

### 6-5. 체중 기록 등록

`POST /users/me/weights`

요청:

```json
{
  "measuredAt": "2026-03-31T08:10:00+09:00",
  "weightKg": 57.6,
  "sourceType": "MANUAL",
  "note": "아침 공복"
}
```

### 6-6. 알레르기 마스터 조회

`GET /allergies`

### 6-7. 내 알레르기 조회

`GET /users/me/allergies`

### 6-8. 내 알레르기 등록

`POST /users/me/allergies`

요청:

```json
{
  "allergyId": 3,
  "severityLevel": "HIGH",
  "note": "미량도 반응"
}
```

### 6-9. 내 알레르기 삭제

`DELETE /users/me/allergies/{allergyId}`

## 7) 음식 검색/상세

### 7-1. 음식 검색 (별칭 포함)

`GET /foods/search?q=갈비찜&limit=20&cursor=...`

응답:

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "foodId": 101,
        "foodName": "갈비찜",
        "matchedAlias": "소갈비찜",
        "servingSizeG": 250.0,
        "caloriesKcal": 520,
        "carbG": 22.5,
        "proteinG": 30.1,
        "fatG": 31.0,
        "sodiumMg": 980
      }
    ],
    "nextCursor": null
  }
}
```

### 7-2. 음식 상세 조회

`GET /foods/{foodId}`

### 7-3. 음식 영양소 상세 조회

`GET /foods/{foodId}/nutrients`

### 7-4. 음식 카테고리 목록 조회

`GET /foods/categories`

### 7-5. 카테고리별 음식 조회

`GET /foods/categories/{categoryCode}/foods?limit=20&cursor=...`

## 8) 캠퍼스 식당/메뉴

### 8-1. 캠퍼스 목록

`GET /campuses`

### 8-2. 캠퍼스별 식당 목록

`GET /campuses/{campusId}/dining-halls`

### 8-3. 날짜/끼니별 메뉴 조회

`GET /menus/daily?campusId=10&date=2026-03-31&mealType=LUNCH`

응답:

```json
{
  "success": true,
  "data": {
    "campusId": 10,
    "date": "2026-03-31",
    "mealType": "LUNCH",
    "halls": [
      {
        "diningHallId": 20,
        "hallName": "학생식당",
        "items": [
          {
            "menuItemId": 3001,
            "menuName": "닭가슴살샐러드",
            "price": 5500,
            "servingSizeG": 320.0,
            "caloriesKcal": 430,
            "proteinG": 38.0,
            "sodiumMg": 650,
            "soldOutYn": false
          }
        ]
      }
    ]
  }
}
```

### 8-4. 메뉴 항목 상세 조회

`GET /menu-items/{menuItemId}`

## 9) 메뉴 추천

### 9-1. 개인 맞춤 메뉴 추천 조회

`GET /recommendations/menus?date=2026-03-31&mealType=LUNCH&campusId=10`

응답:

```json
{
  "success": true,
  "data": {
    "targetId": 88,
    "items": [
      {
        "recommendationId": 4001,
        "menuItemId": 3001,
        "menuName": "닭가슴살샐러드",
        "score": 92,
        "rankOrder": 1,
        "recommendationStatus": "RECOMMENDED",
        "allergyConflictYn": false,
        "reasonSummary": "단백질 목표 보완에 유리, 나트륨 낮음",
        "reasons": [
          {
            "reasonType": "NUTRIENT_BALANCE",
            "metricName": "protein_g",
            "currentValue": 48.0,
            "targetValue": 95.0,
            "contributionScore": 24.5,
            "reasonMessage": "단백질 보충에 효과적입니다."
          }
        ]
      }
    ]
  }
}
```

### 9-2. 추천 이력 조회

`GET /recommendations/menus/history?from=2026-03-01&to=2026-03-31`

## 10) 식단 기록

### 10-1. 식단 기록 목록 조회

`GET /meal-logs?from=2026-03-01&to=2026-03-31`

### 10-2. 식단 기록 상세 조회

`GET /meal-logs/{mealLogId}`

### 10-3. 식단 기록 생성

`POST /meal-logs`

요청:

```json
{
  "logDate": "2026-03-31",
  "mealType": "LUNCH",
  "memo": "배가 고파서 많이 먹음",
  "items": [
    {
      "entrySource": "DINING_MENU",
      "menuItemId": 3001,
      "intakeRatio": 1.0
    },
    {
      "entrySource": "FOOD_SEARCH",
      "foodId": 101,
      "intakeRatio": 0.5
    }
  ]
}
```

응답 `201`:

```json
{
  "success": true,
  "data": {
    "mealLogId": 9001,
    "logDate": "2026-03-31",
    "mealType": "LUNCH",
    "totals": {
      "caloriesKcal": 690,
      "carbG": 61.0,
      "proteinG": 53.1,
      "fatG": 22.4,
      "sodiumMg": 1140
    }
  }
}
```

### 10-4. 식단 기록 수정

`PUT /meal-logs/{mealLogId}`

### 10-5. 식단 기록 삭제

`DELETE /meal-logs/{mealLogId}`

## 11) 식사 직후 피드백

### 11-1. 식사 분석 실행

`POST /meal-logs/{mealLogId}/analyze`

설명: 동기 처리 또는 비동기 작업 ID 반환 방식 중 택1.

### 11-2. 식사 피드백 조회

`GET /meal-logs/{mealLogId}/feedback`

응답:

```json
{
  "success": true,
  "data": {
    "mealFeedbackId": 7101,
    "overallScore": 78,
    "summary": "단백질은 적정, 나트륨이 다소 높습니다.",
    "nextFocus": "다음 끼니는 저염 + 채소 추가",
    "details": [
      {
        "nutrientType": "SODIUM",
        "status": "HIGH",
        "actualValue": 1140,
        "targetValue": 700,
        "feedbackMessage": "국물류 섭취를 줄여보세요."
      }
    ],
    "nextMealGuides": [
      {
        "nextMealType": "DINNER",
        "suggestionType": "ADD",
        "recommendedMenuItemId": 3010,
        "message": "저염 단백질 메뉴를 선택하세요.",
        "priority": 1
      }
    ]
  }
}
```

## 12) 주간 피드백

### 12-1. 주간 피드백 생성

`POST /feedback/weekly`

요청:

```json
{
  "weekStartDate": "2026-03-30",
  "weekEndDate": "2026-04-05"
}
```

### 12-2. 주간 피드백 조회

`GET /feedback/weekly?weekStartDate=2026-03-30`

### 12-3. 주간 피드백 이력 조회

`GET /feedback/weekly/history?from=2026-01-01&to=2026-12-31`

응답:

```json
{
  "success": true,
  "data": {
    "feedbackId": 8101,
    "weekStartDate": "2026-03-30",
    "weekEndDate": "2026-04-05",
    "daysLogged": 6,
    "totalCaloriesKcal": 11120,
    "weekStartWeightKg": 58.0,
    "weekEndWeightKg": 57.4,
    "weightChangeKg": -0.6,
    "overallComment": "나트륨 관리가 필요하지만 감량 추세는 양호합니다.",
    "details": [
      {
        "nutrientType": "PROTEIN",
        "status": "LOW",
        "actualValue": 66.0,
        "targetValue": 95.0,
        "feedbackMessage": "단백질 섭취를 늘리세요.",
        "actionGuide": "다음 주 점심에 닭/두부 메뉴를 우선 선택"
      }
    ]
  }
}
```

## 13) 운영/마스터 데이터

### 13-1. 영양소 마스터 조회

`GET /nutrients`

### 13-2. 원재료 마스터 검색

`GET /ingredients/search?q=우유&limit=20`

## 14) 권장 HTTP 상태 코드

| 코드 | 의미 |
|---|---|
| `200` | 조회/수정 성공 |
| `201` | 생성 성공 |
| `204` | 삭제 성공 |
| `400` | 잘못된 요청 |
| `401` | 인증 실패 |
| `403` | 권한 없음 |
| `404` | 리소스 없음 |
| `409` | 충돌(예: ACTIVE 목표 중복) |
| `422` | 도메인 검증 실패 |
| `500` | 서버 에러 |

## 15) 핵심 도메인 에러 코드

| 코드 | 상황 |
|---|---|
| `TARGET_ACTIVE_ALREADY_EXISTS` | 활성 목표 중복 생성 |
| `TARGET_NOT_ACTIVE` | 비활성 목표에 대한 달성 처리 요청 |
| `ALLERGY_CONFLICT_MENU` | 알레르기 충돌 메뉴 기록/추천 시도 |
| `MEAL_LOG_ITEM_REFERENCE_INVALID` | `foodId`/`menuItemId` 참조 규칙 위반 |
| `MENU_NOT_FOUND_FOR_DATE` | 해당 날짜/끼니 메뉴 없음 |
| `FEEDBACK_NOT_READY` | 분석 미완료 상태 조회 |

## 16) 구현 우선순위 (MVP)

1. 인증/프로필/목표/알레르기
2. 음식 검색/상세
3. 메뉴 조회/추천
4. 식단 기록
5. 식사 직후 피드백
6. 주간 피드백/체중 이력
