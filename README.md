# Codeiary API

Spring Boot 4.1.1, Java 21, PostgreSQL, Flyway 기반 API입니다.

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

STS/Eclipse에서는 Gradle이 MapStruct·QueryDSL 코드를 생성하고 IDE가 해당 소스를 읽습니다.
Gradle 동기화와 IDE 자동 빌드에 Java·테스트 컴파일을 연결했습니다.
최초 설정 또는 생성된 Mapper를 찾지 못할 때 다음 명령을 실행한 뒤,
프로젝트에서 `Gradle > Refresh Gradle Project`, `Project > Clean`을 실행합니다.

```sh
./gradlew eclipseClasspath eclipseJdt eclipsePreferences
```

생성 소스는 `build/generated/sources/annotationProcessor/java/main`과
`build/generated/sources/annotationProcessor/java/test`에서 관리합니다. 해당 폴더의 선택적 경고는 제외하고,
직접 작성한 소스의 경고는 유지합니다. Java 소스 경로는 `src/main/java`, `src/test/java`입니다.

## API 문서

springdoc-openapi 3.1.1로 Swagger UI를 제공합니다. 서버 실행 후:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`

문서와 정적 리소스는 운영 Nginx의 `/api/*` 프록시 경로를 사용합니다.
Swagger는 인증 없이 조회할 수 있고, `Authorize`에 Access Token을 입력하면 인증이 필요한 API를 호출할 수 있습니다.

## JWT 로그인

이메일·비밀번호를 검증하고 JJWT 0.13.0으로 JWT를 발급합니다.
사용자 권한은 `Role.ADMIN`, `Role.USER`로 구분하며 DB에 문자열로 저장합니다.
사용자 엔티티 Enum은 `domain/users/entity/enums` 패키지에서 관리합니다.
이메일은 소문자로 입력해야 합니다. 대문자가 포함된 로그인 요청은 400으로 거절하며,
이메일을 변환하거나 대소문자를 무시하여 조회하지 않습니다. DB도 소문자 이메일만 허용합니다.
비밀번호는 8~20자이며 영문·숫자·특수문자 3개 이상을 포함해야 합니다.
특수문자는 `!@#` 같은 ASCII 기호이며, 같은 기호를 반복해도 개수에 포함합니다.
비밀번호는 BCrypt로 저장하고, Refresh Token 원문 대신 SHA-256 해시를 DB에 저장합니다.

| 요청 | 경로 | 입력 / 인증 |
| --- | --- | --- |
| POST | `/api/auth/login` | JSON `email`, `password` |
| POST | `/api/auth/refresh` | JSON `refreshToken` |
| POST | `/api/auth/logout` | JSON `refreshToken` |
| GET | `/api/admin/me` | ADMIN 권한의 `Authorization: Bearer <accessToken>` |

로그인과 갱신 응답은 `accessToken`, `refreshToken`, `tokenType: "Bearer"`,
`expiresIn`(초), `refreshExpiresIn`(초), `user`(`id`, `email`, `name`, `role`)를 포함합니다.
Access Token은 30분, Refresh Token은 최초 로그인부터 7일 동안 유효하며 `src/main/resources/application.yml`의 `auth.jwt` 설정으로 변경할 수 있습니다.
`AuthService`는 갱신 시 최초 만료 시점을 유지하고, 응답의 `refreshExpiresIn`에 남은 유효 시간(초)을 반환합니다.
Access Token도 이 만료 시점을 넘지 않도록 발급하므로, 로그인 후 7일이 지나면 다시 로그인해야 합니다. JWT 발급은 `JwtTokenService`에서 처리합니다.
갱신할 때 기존 Refresh Token은 폐기되고 새 토큰 쌍을 반환하므로 클라이언트는 둘 다 교체해야 합니다.
동일한 Refresh Token을 동시에 갱신하면 하나의 요청만 성공합니다.

로그아웃은 204를 반환하고, 해당 로그인 식별자(`sid`)를 PostgreSQL의 `token_blacklist`에 등록합니다.
같은 로그인에서 발급한 갱신 전·후 Access Token과 Refresh Token이 모두 즉시 차단됩니다.
다른 기기의 별도 로그인은 유지됩니다. 클라이언트도 두 토큰을 삭제해야 합니다.
로그아웃에는 JSON `refreshToken`만 보내면 되며 Authorization 헤더는 필요하지 않습니다.
계정이 삭제되거나 비활성화되면
Access Token 사용과 Refresh Token 갱신을 모두 차단합니다.
`TokenCleanupService`는 매일 새벽 3시(Asia/Seoul)에 만료된 Refresh Token과 블랙리스트 기록을 삭제합니다.
실행 시간은 `auth.token-cleanup`에서 변경합니다. 갱신으로 폐기된 Refresh Token도 최초 만료 시점까지 보관하여,
이전 Refresh Token으로 로그아웃하더라도 같은 로그인의 토큰을 모두 차단할 수 있습니다.
인증 오류는 401, 관리자 권한 부족은 403이며 기존 `{ "message": "...", "code": "..." }` 형식을 사용합니다.
JWT의 `roles`와 응답의 `user.role`에는 사용자의 실제 권한이 포함됩니다.
요청마다 DB의 현재 권한으로 접근을 판단하므로 ADMIN 토큰이 남아 있어도
DB에서 USER로 변경하면 관리자 API 접근이 즉시 차단됩니다.

`/api/admin/me`는 로그인한 ADMIN이 자신의 정보를 조회하는 경로입니다.
`domain/users/controller/AdminController`에서 관리하며 USER 권한은 403으로 거절합니다.
`/api/admin/**`에는 ADMIN 권한이 필요합니다. 공개 GET `/api/**`와 로그인·갱신·로그아웃은
인증 없이 호출할 수 있고, 나머지 요청은 인증이 필요합니다. HTTP 세션을 만들지 않으며,
토큰을 쿠키가 아닌 요청 헤더·JSON 본문으로 전달하므로 CSRF 검사를 비활성화했습니다.
프론트엔드의 목업 로그인은 별도로 이 API에 연결해야 합니다.

공통 인증 코드는 `global/auth`로 통합합니다. 로그인·갱신·로그아웃 API는 `controller`,
인증 처리·JWT 발급·검증·블랙리스트·만료 기록 정리는 `service`에 둡니다.
인증 필터는 `filter`, 인증·권한 오류 코드와 응답 처리기는 `exception`,
JWT와 Spring Security 설정은 `config`에서 관리합니다.
Refresh Token과 블랙리스트 엔티티·저장소는 `global/auth/entity`, `global/auth/repository`에 둡니다.
사용자 모델·권한·저장소와 관리자 API는 `domain/users`에서 관리합니다.
인증 필터가 JWT의 `subject`를 `Long`으로 변환해 `global/auth/service/AuthService.loadActiveUser(Long userId)`로 사용자를 조회합니다.
`AuthService`가 사용자 없음·비활성 상태를 인증 실패 예외로 처리하고, 인증 필터는 이를 401 응답으로 연결합니다.
인증 필터는 조회·검증한 `User` 엔티티를 인증 principal로 저장합니다.
관리자 컨트롤러는 `@AuthenticationPrincipal User user`를 받아 `domain/users/service/AdminService`에 위임합니다.
`AdminService.getProfile(User user)`가 `UserMapper`로 사용자 응답을 만들며 추가 DB 조회를 하지 않습니다.
API에는 사용자 ID·이메일·이름·권한만 담은 응답 DTO를 반환하며 엔티티의 비밀번호 해시는 포함하지 않습니다.
사용자 응답은 `domain/users/dto/response/UserProfileResponse`, 엔티티 변환은 `domain/users/dto/UserMapper`에 둡니다.
인증 요청 DTO는 `global/auth/dto/request`, 토큰 응답은 `global/auth/dto/response`에 둡니다.
`global/auth/dto/AuthMapper`는 `User` 엔티티와 토큰 정보를 받아 토큰 응답을 생성합니다.
중첩된 사용자 응답 변환은 `uses = UserMapper.class`로 위임하므로 서비스에서 DTO로 먼저 변환하지 않습니다.
두 매퍼는 공통 `MapStructConfig`를 사용하며 Spring이 생성자로 의존성을 주입합니다.

### 사용자 계정과 JWT 키

사용자 계정은 `users` 테이블에 직접 등록합니다. `email`에는 소문자 이메일,
`password_hash`에는 `BCryptPasswordEncoder`로 생성한 비밀번호 해시를 저장합니다.
비밀번호는 위의 로그인 입력 규칙을 만족해야 합니다. `name`, `enabled`, `created_at`, `updated_at`도
설정해야 하며, `id`는 DB가 자동 생성합니다. 로그인 API는 등록된 활성 계정을 검증합니다.
관리자 계정의 `role`은 `ADMIN`으로 지정합니다. 권한을 생략한 새 계정은 `USER`입니다.
로컬 임시 DB(`bootTestRun`)는 종료 시 정리되므로 계정을 유지하려면 기존 DB에 연결합니다.

운영 환경의 `JWT_SECRET`은 `openssl rand -base64 32`로 생성한 값을 사용합니다.
설정이 없거나 Base64 디코딩 후 32바이트보다 짧으면 운영 서버 시작을 거절합니다.
모든 API 인스턴스는 같은 키를 사용해야 하며 키를 변경하면 기존 Access Token이 무효화됩니다.
`local`·`test`에서는 키를 생략하면 실행마다 임시 키를 생성합니다.

## 로컬 실행

Java 21과 Docker를 실행한 상태에서 다음 명령으로 API와 임시 PostgreSQL을 함께 실행합니다.
DB 환경 변수 없이 Swagger를 확인할 수 있으며, 종료하면 임시 DB도 정리됩니다.

```sh
./gradlew bootTestRun
```

기존 DB에 연결할 때는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수를 설정합니다.
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

새 통합 테스트는 `IntegrationTestSupport`를 상속하면 테스트 프로필과 DB 설정을
공유할 수 있습니다. 예외 처리 테스트는 실제 PostgreSQL 중복 키 오류의 HTTP 응답도 검증합니다.
코드 생성 테스트는 테스트 전용 엔티티로 QueryDSL 조회와 생성·수정 시간 기록을 확인합니다.
MapStruct 변환은 실제 `AuthMapper`와 `UserMapper`를 사용하는 인증 서비스 테스트에서 검증합니다.
Swagger 테스트는 문서·UI 리소스 접근과 공개 조회·인증 없는 변경 요청 차단을 확인합니다.
인증 테스트는 `global/auth/repository`, `global/auth/service`, `global/auth/controller`로 나눕니다.
사용자 저장소 테스트는 `domain/users/repository`, 관리자 API 테스트는 `domain/users/controller`에 두며, 컨트롤러 테스트의 공통 설정은
`support/ControllerTestSupport`를 사용합니다. 각 테스트는 자신의 컨트롤러를 지정합니다.
Refresh Token과 블랙리스트 저장소 테스트는 `global/auth/repository`에 둡니다.
Repository 테스트는 `@DataJpaTest`와 실제 PostgreSQL로 조회·유일성·잠금을 검증합니다.
Service 테스트는 Mockito로 비밀번호 검증·토큰 발급·폐기를 확인하고,
JWT 검증은 `global/auth/service`에서 실제 JJWT로 테스트합니다.
동시 갱신은 Service 통합 테스트에서 PostgreSQL로 검증합니다.
Controller 테스트는 `@WebMvcTest`로 요청 검증·HTTP 응답과 JWT 인증을 확인합니다.
관리자 접근 제어와 DB 권한 변경 반영은 `domain/users/controller/AdminControllerTest`에서 검증합니다.

사용자 테스트 객체는 `domain/users/fixture/UserFixture`에서 관리합니다.
인증 테스트 객체는 `global/auth/fixture`의 `AuthRequestFixture`, `AuthResponseFixture`,
`JwtFixture`, `RefreshTokenFixture` 팩토리로 기본 객체를 생성하고,
특정 입력이나 만료·폐기 상태가 필요한 경우 해당 팩토리에 값을 전달합니다.
엔티티와 JWT 빌더는 호출마다 새 객체를 생성하고, 고정 시각·기본 값은 Fixture에 모읍니다.
테스트 대상 서비스 생성과 DB 저장은 각 테스트에서 명시적으로 수행합니다.

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

## 운영 구조

Lightsail에서 Docker Compose로 API와 PostgreSQL을 실행합니다. PostgreSQL은 Docker
내부 네트워크에서만 접근할 수 있고, API의 `8080` 포트도 `127.0.0.1`에만
바인딩되어 Nginx의 `/api/*` 프록시를 통해서만 외부에 노출됩니다.

`main` 브랜치에 push하면 GitHub Actions가 API 이미지를 Amazon ECR Public에 게시하고,
SSM Run Command로 Lightsail의 Docker Compose 서비스를 갱신합니다. 저장소는 비공개여도
ECR Public 이미지는 공개 다운로드할 수 있으므로 이미지에 비밀값을 넣지 않습니다.

GitHub Actions repository variables:

- `AWS_ROLE_ARN`, `AWS_REGION`, `SSM_INSTANCE_ID`

Access Key 대신 GitHub OIDC 임시 자격증명을 사용합니다. 프론트/백엔드 전용 IAM 역할은
각 저장소의 `AWS_ROLE_ARN` 변수에 설정합니다. 역할 신뢰 정책은 해당 저장소의 `main`
브랜치에만 OIDC 배포를 허용하고, SSM 대상은 `SSM_INSTANCE_ID`로 제한합니다.

Ubuntu Lightsail 인스턴스 생성 후 최초 한 번 다음 스크립트를 실행합니다.

```sh
chmod +x deploy/bootstrap-lightsail.sh
./deploy/bootstrap-lightsail.sh
```

운영 DB 설정은 서울 리전 Systems Manager Parameter Store의 `SecureString`에 저장합니다.
`/codeiary/prod/postgres-db`, `/codeiary/prod/postgres-user`, `/codeiary/prod/postgres-password`
파라미터를 준비하고 `deploy/ssm-parameter-store-policy.json`을 SSM hybrid activation의
IAM role에 연결해야 합니다. 이 정책은 해당 경로에서 `ssm:GetParameter`와 제한된
`kms:Decrypt`만 허용합니다. 배포 스크립트는 값을 `/run`의 임시 Compose env 파일로
받아 컨테이너를 갱신한 뒤 파일을 삭제합니다. 앱 이미지는 비밀값을 포함하지 않습니다.

JWT 로그인 배포 전에 다음 파라미터도 준비합니다.

- `/codeiary/prod/jwt-secret`: Base64로 인코딩한 32바이트 이상의 랜덤 키 (`SecureString`)

기존 서버에서는 새 파일을 아래 명령으로 반영한 뒤 배포합니다 (저장소 루트에서 실행).

```sh
sudo install -m 644 compose.yaml /opt/codeiary/compose.yaml
sudo install -m 755 deploy/deploy-api.sh /usr/local/sbin/codeiary-deploy-api
```

Flyway의 `V1__create_auth_tables.sql`이 `users`, `refresh_tokens`, `token_blacklist`와
관련 제약 조건·인덱스를 생성합니다. 새 계정의 기본 권한은 `USER`이며,
관리자 계정은 `role`을 `ADMIN`으로 지정합니다.

운영 인스턴스는 Lightsail `small_3_0` 플랜(2GB RAM)입니다. 방화벽은 `22`, `80`, `443`만
허용하고 `8080`, `5432`는 열지 않습니다.

## 미디어 저장소

`infra/media-storage.yaml`은 비공개 S3 버킷과 CloudFront 배포를 생성합니다. S3 객체는
CloudFront OAC를 통해서만 읽을 수 있고, 정적 미디어 배포는 HTTPS와 압축을 사용합니다.
스택은 `ap-northeast-2`에 배포하며 CloudFront 기본 도메인을 출력합니다. 현재 애플리케이션에는
이미지 업로드 API가 없으므로, 업로드 기능을 추가할 때 별도의 최소 권한 업로드 인증을 연결해야 합니다.
`img.codeiary.com` 사용자 지정 도메인을 연결하려면 `us-east-1` ACM 인증서를 DNS 검증하고,
Cloudflare에 ACM 검증 CNAME과 CloudFront 대상 CNAME(`img` → 배포 도메인, DNS only)을 등록한 뒤
`DomainCertificateArn` 파라미터로 스택을 갱신합니다.

로컬 개발용 Compose 설정은 `.env.example`을 참고합니다. 운영 환경에서는 `.env` 파일을
사용하지 않습니다. ECR Public 이미지는 서버에서 별도 로그인 없이 내려받습니다.
