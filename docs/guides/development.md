# 개발 환경과 테스트

## 코드 스타일

Java 소스는 [Google Java Style](https://google.github.io/styleguide/javaguide.html)을 따르고,
`google-java-format` 2.0.0으로 정렬합니다. 기본 들여쓰기와 줄 길이는 `.editorconfig`에 둡니다.
객체 변수와 메서드 파라미터는 타입명을 소문자 카멜케이스로 바꿔 사용합니다
(`BlogPost blogPost`, `User user`). 값과 컬렉션은 역할이 드러나는 이름을 사용합니다
(`postId`, `posts`). JSON·DB 매핑 이름과 Lombok 빌더 생성자 파라미터는 외부 계약을 유지합니다.

## 공통 엔티티

시간 기록이 필요한 엔티티는 `com.codeiary.global.entity.TimeBaseEntity`를 상속합니다.
JPA Auditing이 최초 저장 시 `createdAt`과 `updatedAt`을 기록하고,
변경 시에는 `updatedAt`만 갱신합니다. 식별자는 각 엔티티에서 정의합니다.

## 매핑 및 쿼리

MapStruct 1.6.3과 QueryDSL의 OpenFeign 포크 7.7을 사용합니다.
MapStruct 매퍼에 `@Mapper(config = MapStructConfig.class)`를 지정하면
Spring 빈과 생성자 주입을 사용하고, 매핑하지 않은 대상 필드는 컴파일 오류로 확인합니다.
Lombok 연동을 위한 `lombok-mapstruct-binding`도 적용했습니다.

QueryDSL은 `JPAQueryFactory`를 주입받아 사용합니다. `@Entity`를 작성하고
`./gradlew compileJava`를 실행하면 Q 타입이
`build/generated/sources/annotationProcessor/java/main`에 자동으로 생성됩니다.
생성된 코드는 Git에 포함하지 않습니다.

STS에서는 의존성을 변경한 뒤 `Gradle > Refresh Gradle Project`로 동기화합니다.
라이브러리는 `Project and External Dependencies`로 묶어 관리하며 JAR를 직접 추가하지 않습니다.

생성 코드가 중복되지 않도록 어노테이션 처리는 Gradle에서 실행합니다.
`./gradlew compileJava compileTestJava`로 생성 코드를 갱신하고, STS 프로젝트 설정에서
`Java Compiler > Annotation Processing`을 비활성화합니다.
`Java Build Path > Source`에는 Gradle 생성 경로인
`build/generated/sources/annotationProcessor/java/main`과 `test`를 등록합니다.
기존 `.apt_generated`, `.apt_generated_tests` 또는 잘못된 절대 경로로 등록된
생성 소스 항목은 제거하고 프로젝트를 새로고침합니다.
생성 코드와 `.classpath`, `.settings` 등 IDE 로컬 설정은 Git에 포함하지 않습니다.
같은 폴더를 VS Code에서도 열 때는 `.vscode/settings.json`의
`java.autobuild.enabled`를 `false`로 설정해 두 IDE가 `bin/`을 동시에 덮어쓰지 않게 합니다.

## 로컬 실행

Java 21과 Docker를 실행한 상태에서 다음 명령으로 API와 임시 PostgreSQL을 함께 실행합니다.
DB 환경 변수 없이 Swagger를 확인할 수 있으며, 종료하면 임시 DB도 정리됩니다.

```sh
./gradlew bootTestRun
```

기존 DB에 연결할 때는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수를 설정합니다.
Google 로그인을 사용할 때는 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`,
`OAUTH2_REDIRECT_HOME`도 IDE 실행 구성에 등록합니다.
Gradle은 `.env` 파일을 자동으로 읽지 않으므로 IDE 실행 환경이나 셸에 변수를 적용해야 합니다.

`.env.example`을 참고해 환경 변수를 설정하고 PostgreSQL을 실행한 다음:

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 테스트

Java 21과 Docker를 실행한 상태에서 `./gradlew test`로 전체 테스트를 실행합니다.
통합 테스트는 Testcontainers가 `postgres:17-alpine`의 임시 DB를 시작하고,
`@ServiceConnection`으로 JDBC와 Flyway 연결을 자동으로 설정합니다.
DB 환경 변수나 별도의 PostgreSQL 실행은 필요하지 않습니다.
컨테이너는 테스트 컨텍스트 종료 시 정리됩니다.

통합 테스트는 `IntegrationTestSupport`로 DB 설정을 공유합니다.
실제 API의 인증·권한 통합 테스트와 사용자 저장소 테스트,
`domain/auth`의 로그인·로그아웃·토큰 재발급·블랙리스트·JWT 검증·쿠키 처리 단위 테스트가 있습니다.
사용자 테스트 객체는 `domain/users/fixture/UserFixture`에서 관리합니다.
OAuth 신규 가입·기존 계정 연결, 온보딩·프로필 수정·공개 응답 범위와 입력 검증도 테스트합니다.
서비스·컨트롤러 단위 테스트는 mock 저장소와 MockMvc를 사용하며 DB 없이 실행할 수 있습니다.
사용자 저장소 테스트는 실제 PostgreSQL에서 프로필 저장·생성/수정 시간과 이메일·닉네임·OAuth 식별자 유니크 제약을 확인합니다.
공통 예외 응답은 DB 없이 MockMvc로 확인합니다. 별도의 가짜 엔티티나 프레임워크 코드 생성 테스트는 두지 않습니다.
이미지 테스트는 요청 MIME·길이 검증, Presigned URL의 만료·경로·서명 헤더,
저장소 설정 오류와 업로드 URL 발급 권한을 확인하며 AWS에 파일을 전송하지 않습니다.

테스트는 `// given`, `// when`, `// then`으로 준비·실행·검증을 구분합니다.
Mock 설정은 BDDMockito `given(...).willReturn(...)` / `willThrow(...)`를,
호출 검증은 `then(mock).should()`를 사용합니다. 실패 시나리오도 예외를 When에서 포착하고
Then에서 검증하며, 기존 한국어 `~할 수 있다.` 테스트 이름을 유지합니다.

## CI와 dev 병합

`.github/workflows/ci.yml`은 `dev`·`main` 대상 PR과 `dev` push에서 Java 21과 Docker를 준비하고,
`./gradlew --no-daemon clean build`로 전체 빌드와 Testcontainers 테스트를 실행합니다.
PR의 필수 상태 검사 이름은 `Backend tests`이며, 경로 필터나 테스트 생략 없이 실행합니다.

GitHub의 `dev` 보호 규칙은 PR을 요구하고, 최신 `dev` 기준으로 `Backend tests`가
통과해야 병합할 수 있도록 설정했습니다. 관리자에게도 같은 규칙이 적용됩니다.
변경사항은 별도의 기능 브랜치에 push한 뒤 `dev`로 PR을 생성합니다.
