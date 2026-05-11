# MySQL 실행 설정

런타임 DB는 MySQL을 기본으로 사용한다. 애플리케이션 기본 연결값은 다음과 같다.

```properties
DB_URL=jdbc:mysql://localhost:3306/kkirok?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=utf8
DB_USERNAME=root
DB_PASSWORD=
FLYWAY_BASELINE_ON_MIGRATE=false
```

## 초기 DB 생성

MySQL에 접속한 뒤 데이터베이스를 먼저 만든다.

```sql
create database kkirok
  default character set utf8mb4
  collate utf8mb4_unicode_ci;
```

실행 예시:

```sh
DB_USERNAME=your_user DB_PASSWORD=your_password ./gradlew bootRun
```

애플리케이션 시작 시 Flyway가 `src/main/resources/db/migration`의 migration을 순서대로 적용한다. 적용 이력은 MySQL의 `flyway_schema_history` 테이블에 기록된다.

## 기존 DB 편입 주의

이전에 `schema.sql`/`data.sql`로 직접 초기화한 DB처럼 이미 테이블이 있는 DB를 Flyway로 편입하려면 먼저 실제 DB 상태가 현재 migration 결과와 같은지 확인해야 한다. 확인 없이 `FLYWAY_BASELINE_ON_MIGRATE=true`를 사용하면 누락된 테이블이나 데이터가 있어도 Flyway가 이미 적용된 것으로 간주할 수 있다.

테스트는 로컬 MySQL 서버 없이도 실행되도록 `src/test/resources/application.properties`에서 H2 MySQL 호환 모드와 동일한 Flyway migration을 사용한다.
