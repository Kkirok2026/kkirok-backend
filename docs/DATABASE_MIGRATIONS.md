# 데이터베이스 Migration 정책

본 프로젝트는 Flyway로 데이터베이스 변경 이력을 관리한다. Spring Boot는 애플리케이션 시작 시 `classpath:db/migration`의 SQL 파일을 버전 순서대로 적용하고, 적용 결과와 체크섬을 `flyway_schema_history` 테이블에 기록한다.

## 파일 규칙

- 위치: `src/main/resources/db/migration`
- 이름: `V{순번}__{설명}.sql`
- 예시: `V3__add_food_source_index.sql`
- 순번은 증가만 허용한다. 이미 main에 병합되거나 DB에 적용된 migration 파일은 수정하지 않는다.

## 변경 절차

1. ERD 또는 테이블 명세 변경 내용을 먼저 정리한다.
2. 기존 migration을 수정하지 않고 새 migration 파일을 추가한다.
3. 변경된 ERD, 테이블 명세서, 정규화 검토 문서가 있다면 같은 변경 단위에서 함께 갱신한다.
4. `./gradlew test`로 테스트 DB에 migration이 처음부터 끝까지 적용되는지 확인한다.
5. 이미 운영/공유 DB에 적용된 migration을 수정해야 하는 상황이면 수정 대신 보정 migration을 추가한다.

## 현재 migration

- `V1__create_initial_schema.sql`: 초기 테이블, 제약 조건, 인덱스 생성
- `V2__seed_initial_data.sql`: 초기 학교, 식당, 음식, 영양소, 기준 영양성분 샘플 데이터 삽입

## 금지 사항

- 적용된 migration 파일의 SQL 수정
- 적용된 migration 파일명 변경
- `schema.sql` 또는 `data.sql`을 통한 별도 초기화 경로 추가
- ERD만 바꾸고 migration을 추가하지 않는 변경
- migration만 바꾸고 API/테이블 문서와 불일치시키는 변경
