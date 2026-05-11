# MySQL 실행 설정

런타임 DB는 MySQL을 기본으로 사용한다. 애플리케이션 기본 연결값은 다음과 같다.

```properties
DB_URL=jdbc:mysql://localhost:3306/kkirok?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=utf8
DB_USERNAME=root
DB_PASSWORD=
SQL_INIT_MODE=always
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

`schema.sql`과 `data.sql`은 초기 개발용 부트스트랩 데이터다. 이미 스키마와 샘플 데이터가 들어간 DB에서 재시작할 때 중복 insert 오류가 나면 `SQL_INIT_MODE=never`로 실행한다.

```sh
DB_USERNAME=your_user DB_PASSWORD=your_password SQL_INIT_MODE=never ./gradlew bootRun
```

테스트는 로컬 MySQL 서버 없이도 실행되도록 `src/test/resources/application.properties`에서 H2 MySQL 호환 모드를 사용한다.
