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

## API 문서

springdoc-openapi 3.1.1로 Swagger UI를 제공합니다. 서버 실행 후:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`

문서와 정적 리소스는 운영 Nginx의 `/api/*` 프록시 경로를 사용합니다.
Swagger는 인증 없이 조회할 수 있습니다. 인증 API는 로그인 후 브라우저에 설정된
`access_token` 쿠키를 사용하며, OpenAPI에는 `cookieAuth`로 표시합니다.

## OAuth2 로그인과 JWT 쿠키

기존 이메일·비밀번호 로그인 API 대신 Google OAuth2/OIDC 로그인을 사용합니다.
`/oauth2/authorization/google`에서 시작하며, 처음 로그인한 사용자는 온보딩 대기 권한인 `PENDING`으로 가입합니다.
Google의 검증된 이메일(`email_verified=true`)을 요구하고, 계정은 제공자(`provider`)와
Google 사용자 식별자(`sub`)로 식별합니다. 이미 연결된 계정은 이메일이 변경되어도 같은 사용자로 조회합니다.
OAuth 연결 정보가 없는 기존 계정은 검증된 Gmail 주소 또는 Google Workspace의
`hd` 클레임이 있는 이메일일 때만 이메일 일치로 연결합니다. 다른 OAuth 계정에 이미 연결된
이메일은 충돌로 처리하며, 비활성 계정은 로그인할 수 없습니다.
Google 설정에는 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`이 필요하며,
로그인 성공 후 이동할 주소는 `OAUTH2_REDIRECT_HOME`으로 설정합니다.
로컬 실행은 `.env.example`을 참고해 IDE 실행 환경에 세 값을 등록하고,
운영 배포는 SSM Parameter Store의 `/codeiary/prod/google-client-id`와
`/codeiary/prod/google-client-secret`을 사용합니다. 운영 리디렉션 주소는
`https://codeiary.com/auth/callback`입니다.

로그인 성공 시 JJWT로 Access Token과 Refresh Token을 발급하고 `HttpOnly` 쿠키로 전달합니다.
운영에서는 `Secure`, `SameSite=Strict`를 사용합니다. 쿠키 이름·경로·도메인 등은
`src/main/resources/application.yml`의 `token.cookie`에서 설정합니다.

| 요청 | 경로 | 입력 / 인증 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/api/auth/reissue` | `refresh_token` 쿠키, 본문 없음 | 204, 새 인증 쿠키 설정 |
| POST | `/api/auth/refresh` | `/reissue`와 같은 동작 | 204, 새 인증 쿠키 설정 |
| POST | `/api/auth/logout` | 인증 쿠키, 본문 없음 | 204, 인증 쿠키 삭제 |
| GET | `/api/users/me` | PENDING·USER·ADMIN 권한의 `access_token` 쿠키 | 사용자 프로필 JSON |
| GET | `/api/admin/me` | ADMIN 권한의 `access_token` 쿠키 | 사용자 프로필 JSON |

토큰 재발급·로그아웃은 Access Token이 만료되어도 호출할 수 있습니다.
재발급은 유효한 Refresh Token이 필요하며, 토큰 원문을 응답 JSON에 포함하지 않습니다.
프론트는 `credentials: "include"`로 인증 쿠키를 보내고 `/api/users/me`로 로그인 상태를 복원합니다.
로그인 후 이동 주소는 `OAUTH2_REDIRECT_HOME`으로 지정하며 프론트의 `/auth/callback`을 사용합니다.

Access Token은 30분, Refresh Token은 최초 로그인부터 7일 동안 유효합니다.
`token.jwt.access-token-ttl`, `token.jwt.refresh-token-ttl`로 변경할 수 있습니다.
재발급해도 최초 7일 만료 시점은 연장되지 않고, Access Token도 이 시점을 넘지 않습니다.
Refresh Token은 원문 대신 SHA-256 해시를 DB에 저장하고, 재발급 시 기존 토큰을 폐기합니다.
폐기된 Refresh Token을 다시 사용하면 해당 로그인 전체를 차단합니다.
클라이언트는 동시에 여러 재발급 요청을 보내지 않아야 합니다.

로그아웃은 로그인 식별자(`sid`)를 PostgreSQL `token_blacklist`에 등록하여
해당 로그인에서 발급한 Access Token·Refresh Token을 모두 차단합니다.
다른 기기의 별도 로그인은 유지됩니다. 유효한 Refresh Token이 없으면 Access Token으로
로그아웃할 수 있고, 유효한 토큰이 없어도 쿠키 삭제와 204 응답을 반환합니다.
계정이 삭제되거나 비활성화되면 인증과 재발급을 차단하며,
요청마다 DB의 현재 권한을 사용하므로 권한 변경도 다음 요청부터 반영됩니다.
`TokenCleanupScheduler`가 매일 새벽 3시(Asia/Seoul)에 만료된 토큰과 블랙리스트 기록을 삭제합니다.
실행 시간과 활성화 여부는 `auth.token-cleanup.cron`, `auth.token-cleanup.enabled`로 설정합니다.

`/api/admin/**`에는 ADMIN 권한이 필요합니다. `PENDING` 계정은 본인 조회,
닉네임 중복 확인, 온보딩 완료, 토큰 재발급·로그아웃 API와 공개 조회를 사용할 수 있습니다.
프로필 수정 등 나머지 일반 기능은 온보딩을 완료한 `USER` 또는 `ADMIN` 권한이 필요합니다.
GET `/api/users/me`와 `/api/users/nickname-availability`에도 인증이 필요하며,
공개 GET `/api/**`, OAuth2 진입·콜백, 재발급·로그아웃 외의 요청은 인증이 필요합니다.
인증 오류는 401, 권한 부족은 403이며
기존 `{ "message": "...", "code": "..." }` 형식을 사용합니다.
현재 CSRF 검사는 비활성화되어 있고, 인증 쿠키의 SameSite 기본값은 Strict입니다.

인증 업무는 `domain/auth`에서 관리합니다. `controller/TokenController`는 재발급·로그아웃 API를,
`service/OAuthAccountService`는 OAuth 계정 생성·연결을,
`service/TokenSessionService`는 로그인 세션·재발급·폐기를 담당합니다.
리프레시 토큰과 블랙리스트는 `entity`, `repository`에 두고,
만료된 기록은 `scheduler/TokenCleanupScheduler`에서 정리합니다.
같은 `domain/auth`의 `provider/JwtTokenProvider`는 JWT 생성·검증을,
`cookie/TokenCookieManager`는 쿠키 처리를 담당합니다. 쿠키 관리와 예약 작업은 `@Component`로 등록합니다.
인증 정보인 `AuthInfo`, JWT 발급 결과인 `TokenPair`와 공통 토큰 오류인 `TokenErrorCode`도 이 패키지에서 관리합니다.
`User`가 기존 OAuth 연결의 덮어쓰기를 막으며, 서비스는 이메일·OAuth 식별자 중복만 가입 충돌로 처리합니다.

Spring Security 연동은 `global/security`에 둡니다. OAuth2/OIDC 사용자 정보 조회는 `service`,
인증·인가 오류 응답 처리는 `exception`, 로그인 결과 처리는 `handler`, 사용자 객체와 어댑터는 `dto`에서 담당합니다. 이 연동 클래스들은 `@Component`로 등록하고, 업무 규칙을 처리하는 서비스는 `@Service`로 구분합니다.
요청 인증 필터는 `global/security/filter/TokenAuthenticationFilter`에 둡니다.

### 사용자 온보딩과 공개 프로필

사용자 모델·권한·저장소와 사용자·관리자 API는 `domain/users`에서 관리합니다.
인증 필터는 검증한 `User`를 principal로 저장하며, 본인 정보 조회·수정은
`@AuthenticationPrincipal User user`의 식별자를 사용합니다.
`UserMapper`가 본인용 `UserProfileResponse`와 공개용 `PublicUserProfileResponse`를 구분합니다.
사용자·관리자의 본인 프로필 조회는 `UserService.getProfile`을 공유하며, 관리자 권한 검사는 Security 설정에서 처리합니다.

| 요청 | 경로 | 인증 / 입력 | 성공 응답 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 인증 쿠키 | 본인 프로필 |
| GET | `/api/users/nickname-availability?nickname=...` | 인증 쿠키, 확인할 닉네임 | `{ "available": true/false }` |
| POST | `/api/users/me/onboarding` | 인증 쿠키, multipart 폼 | 저장한 본인 프로필 |
| POST | `/api/images/presigned-url` | USER·ADMIN 인증 쿠키, `contentType`·`contentLength` JSON | S3 업로드 URL·공개 이미지 URL·필수 헤더·만료 시각 |
| PUT | `/api/users/me/profile` | USER·ADMIN 인증 쿠키, 프로필 전체를 담은 JSON | 저장한 본인 프로필 |
| GET | `/api/users/{userId}` | 공개 | 공개 프로필 |
| GET | `/api/users/by-nickname/{nickname}` | 공개, 닉네임 대소문자 무관 | 공개 프로필 |

| 입력 필드 | 온보딩 | 프로필 수정 | 검증 |
| --- | --- | --- | --- |
| `nickname` | 필수 | 필수 | 한글·영문·숫자·밑줄 2~20자, 대소문자 무관 중복 제한 |
| `profileImageUrl` | — | 선택 | HTTPS URL, 최대 2,048자 (로컬 실행은 로컬 S3 주소도 허용) |
| `githubUrl` | — | 선택 | `https://github.com/{사용자명}` 형태, 최대 255자 |
| `contactEmail` | — | 선택 | 공개할 연락 이메일, 최대 254자 |

닉네임 중복 확인은 현재 사용자 본인의 닉네임을 제외합니다. 온보딩을 완료하면 `PENDING` 계정을
`USER`로 승격하며, 기존 `ADMIN` 권한은 유지합니다. 응답의 `onboardingCompleted` 필드는 유지하되
`role != PENDING`으로 계산합니다. 온보딩은 `multipart/form-data`로 닉네임만 받습니다.
프로필 사진·GitHub·공개 연락 이메일은 가입 후 내 집에서 설정합니다.
프로필 사진은 선택 사항입니다. 프론트는 선택한 사진을 256px JPEG로 변환한 뒤
`POST /api/images/presigned-url`로 업로드 URL을 발급받고, 파일을 S3로 직접 전송합니다.
`PENDING` 계정은 업로드 URL을 발급받을 수 없습니다.

S3 업로드 성공 후 응답의 `imageUrl`을 `PUT /api/users/me/profile`의 `profileImageUrl`로
저장해야 사용자 프로필에 적용됩니다. URL 발급과 S3 업로드는 사용자 DB를 변경하지 않습니다.
이미지 URL 검증은 `ImageUrlValidator`가 담당하며, `UserService`는 S3 서명 서비스에 의존하지 않습니다.
기존 multipart `POST /api/users/me/profile-image`는 제거했으므로 프론트와 백엔드를 함께 배포해야 합니다.

프로필 수정은 전체 수정입니다. 선택 필드를 생략하거나 `null` 또는 빈 문자열로 보내면
기존 값을 삭제하므로 유지할 값도 함께 전송합니다. 로그인 이메일·실명·권한은 수정 입력에 포함하지 않습니다.

본인 프로필은 `id`, `email`, `name`, `nickname`, `profileImageUrl`, `onboardingCompleted`,
`role`, `githubUrl`, `contactEmail`을 반환합니다. 공개 프로필은 `id`, `nickname`,
`profileImageUrl`, `githubUrl`, `contactEmail`만 반환하며 로그인 이메일·실명·권한·Google 식별자는
포함하지 않습니다. `contactEmail`은 사용자가 직접 공개한 별도 연락처이며 로그인 이메일을 자동으로 복사하지 않습니다.
공개 조회는 활성 상태이고 온보딩을 완료한 계정만 제공하며, 그 외에는 404를 반환합니다.

### 사용자 계정과 JWT 키

일반 사용자는 Google 최초 로그인으로 가입하며, 이메일은 소문자로 저장합니다.
Google OAuth2 전용이므로 사용자 비밀번호를 보관하지 않습니다. 신규 계정의 기본 권한은
`PENDING`이며 온보딩 완료 후 `USER`가 됩니다. 관리자 권한은 DB에서 `role`을 `ADMIN`으로 지정합니다.
기존 `USER` 중 닉네임이 없는 계정은 `PENDING`으로 보정하여 로그인 후 온보딩을 진행합니다.
기존 `ADMIN` 권한은 닉네임 유무와 관계없이 유지합니다.
로컬 임시 DB(`bootTestRun`)는 종료 시 정리되므로 계정을 유지하려면 기존 DB에 연결합니다.

운영 애플리케이션의 `TOKEN_JWT_SECRET`은 `openssl rand -base64 32`로 생성한 값을 사용합니다.
`TOKEN_JWT_SECRET`을 우선 사용하며 기존 배포의 `JWT_SECRET`도 대체 설정으로 지원합니다.
설정이 없거나 Base64 디코딩 후 32바이트보다 짧으면 운영 서버 시작을 거절합니다.
모든 API 인스턴스는 같은 키를 사용해야 하며, 키를 변경하면 기존 Access Token과 Refresh Token이 무효화됩니다.
운영 이외 환경에서는 키를 생략하면 실행마다 임시 키를 생성합니다.

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

JWT 쿠키 인증 배포 전에 다음 파라미터도 준비합니다.

- `/codeiary/prod/jwt-secret`: Base64로 인코딩한 32바이트 이상의 랜덤 키 (`SecureString`)

기존 서버에서는 새 파일을 아래 명령으로 반영한 뒤 배포합니다 (저장소 루트에서 실행).

```sh
sudo install -m 644 compose.yaml /opt/codeiary/compose.yaml
sudo install -m 755 deploy/deploy-api.sh /usr/local/sbin/codeiary-deploy-api
```

Flyway의 기존 `V1__create_auth_tables.sql`은 보존합니다. V1은 `users`, `refresh_tokens`,
`token_blacklist`와 관련 제약 조건·인덱스를 생성하며, 닉네임·프로필 이미지와 대소문자를 구분하지 않는
닉네임 중복 방지 인덱스를 포함합니다. 사용자 비밀번호 컬럼은 없습니다.
`V2__add_user_profiles_and_oauth_identity.sql`이 `github_url`, `contact_email`,
`oauth_provider`, `oauth_subject`와 OAuth 식별자 유니크 인덱스를 추가합니다.
OAuth 제공자는 `OAuthProvider` enum으로 관리하며, DB에는 기존 `google` 문자열을 그대로 저장합니다.
제공자별 이메일 인증·소유권 판단은 어댑터가 담당하고, `OAuthAccountService`는 공통 `AuthInfo`로 가입·조회·계정 연결을 처리합니다.
역할 CHECK 제약에 `PENDING`을 추가하고 기본값을 `PENDING`으로 변경하며,
기존 `USER` 중 닉네임이 없는 계정도 `PENDING`으로 보정합니다.
기존 DB는 데이터와 `flyway_schema_history`를 유지한 채 V2를 순차 적용합니다.
새 계정은 온보딩 완료 시 `USER`로 승격하며, 관리자 계정은 `role`을 `ADMIN`으로 지정합니다.

운영 인스턴스는 Lightsail `small_3_0` 플랜(2GB RAM)입니다. 방화벽은 `22`, `80`, `443`만
허용하고 `8080`, `5432`는 열지 않습니다.

## 이미지 업로드 API

### 로컬 이미지 저장소

Docker가 실행된 상태에서 백엔드 폴더에서 다음 명령을 실행합니다.

```bash
docker compose -f compose.local.yaml up -d
```

`local` 프로필은 `http://localhost:9090`의 S3Mock과 `codeiary-local` 버킷을 사용합니다.
STS에서 백엔드를 재시작하면 Presigned URL 발급, 브라우저 PUT 업로드, 프로필 저장을
로컬에서 사용할 수 있습니다. 실제 AWS 자격 증명이나 CloudFront는 필요하지 않으며,
AWS 사용 요금이 발생하지 않습니다. 이미지는 Docker의 `images` 볼륨에 유지됩니다.
`docker compose -f compose.local.yaml down`으로 중지하고, 데이터를 유지하려면 `-v`를 붙이지 않습니다.

S3 설정은 `cloud.aws.s3` 아래에 둡니다.
`S3PresignerConfig`에서 `@Value`로 리전, 자격 증명, 로컬 endpoint를 주입받아
`S3Presigner`를 등록합니다. 버킷과 공개 주소는 `ImageService`에서 직접 주입받습니다.
JWT와 쿠키 설정도 사용하는 클래스에서 `@Value`로 주입받습니다.
쿠키 수명은 발급된 JWT의 남은 유효시간을 따르며, 만료 시간은 `token.jwt`에서만 설정합니다.
`local` 프로필은 Docker 주소와 로컬 전용 자격 증명을 사용합니다.
운영에서는 `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` 환경 변수와
기존 버킷·CloudFront 주소를 사용하며, 별도 endpoint를 설정하지 않습니다.

프론트 개발 서버에서만 `http://localhost:9090/codeiary-local/` 주소를 허용합니다.
운영 빌드와 기본 백엔드 설정은 HTTPS를 요구합니다. 로컬 저장소 포트는 이 컴퓨터에만 노출됩니다.
[S3Mock](https://github.com/adobe/S3Mock)은 업로드 흐름을 개발하기 위한 에뮬레이터이며,
Presigned URL의 서명·만료와 AWS IAM 권한을 검증하는 환경은 아닙니다.

### 업로드 계약

`POST /api/images/presigned-url`은 S3에 직접 업로드할 URL을 발급합니다.
로그인 쿠키가 있는 `USER`·`ADMIN`만 사용할 수 있으며, 비로그인은 401,
온보딩 전 `PENDING` 계정은 403으로 응답합니다.

요청은 `application/json`이며, `contentLength`는 전송할 파일의 정확한 바이트 수입니다.

```json
{
  "contentType": "image/jpeg",
  "contentLength": 12345
}
```

`contentType`은 `image/jpeg` 또는 `image/png`, `contentLength`는 1바이트 이상
10MiB(10,485,760바이트) 이하를 허용합니다. 성공 응답은 `200 OK`이며,
응답 캐시는 `Cache-Control: no-store`로 차단합니다.

```json
{
  "uploadUrl": "https://<bucket>.s3.<region>.amazonaws.com/images/1/<uuid>.jpg?<signature>",
  "imageUrl": "https://img.codeiary.com/images/1/<uuid>.jpg",
  "headers": {
    "Content-Type": "image/jpeg",
    "Cache-Control": "public, max-age=31536000, immutable",
    "x-amz-server-side-encryption": "AES256",
    "If-None-Match": "*"
  },
  "expiresAt": "2026-10-08T01:05:00Z"
}
```

브라우저는 발급 요청에만 인증 쿠키를 보내고, 반환된 `uploadUrl`에는 `File` 또는 `Blob`을
본문으로 직접 PUT합니다. `FormData`로 감싸지 않고 `headers`를 그대로 적용합니다.

```js
const issued = await fetch('/api/images/presigned-url', {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ contentType: file.type, contentLength: file.size }),
})
if (!issued.ok) throw new Error('이미지 업로드 URL 발급 실패')
const { uploadUrl, imageUrl, headers } = await issued.json()
const uploaded = await fetch(uploadUrl, {
  method: 'PUT',
  credentials: 'omit',
  headers,
  body: file,
})
if (!uploaded.ok) throw new Error('이미지 업로드 실패')
// 업로드 성공 후 imageUrl을 프로필·게시글 저장 요청에 사용합니다.
```

- URL 유효기간은 5분입니다. 발급에 사용한 임시 AWS 자격증명이 먼저 만료되면 URL도 만료됩니다.
- `Content-Length`도 서명에 포함하지만 브라우저가 `File`/`Blob` 크기로 자동 설정하므로 직접 헤더를 지정하지 않습니다.
- 인증된 사용자 ID와 UUID로 `images/{userId}/{uuid}.jpg|png` 경로를 만듭니다.
  `If-None-Match: *`를 서명하여 같은 URL로 기존 객체를 덮어쓰지 못하게 합니다.
- `imageUrl`은 기존 공개 CloudFront 이미지 주소입니다. CloudFront Signed URL은 발급하지 않습니다.
- 이미지 바이트는 백엔드를 거치지 않습니다. 백엔드는 선언한 MIME·길이를 검증하여 서명하고,
  S3가 PUT 요청의 서명을 확인합니다. 실제 이미지 내용·메타데이터·픽셀 크기는 서버에서 검사하거나 재인코딩하지 않습니다.
  프론트의 프로필 256px JPEG 변환은 유지하지만 서버 검증을 대체하지 않습니다.
- DB 저장이나 업로드 완료 확정 API는 추가하지 않습니다. S3 PUT 성공을 확인한 후 반환된 URL을 저장해야 합니다.
- URL 발급 실패는 기존 `{ "message": "...", "code": "..." }` 형식을 사용합니다.
  S3 직접 PUT의 오류는 S3 응답이므로 프론트에서 별도로 처리합니다.

기존 multipart `POST /api/images`와 `POST /api/users/me/profile-image`는 제거했습니다.
프론트와 백엔드를 함께 배포해야 하며, 이미지 업로드 때문에 API 프록시의 본문 크기 제한을 늘릴 필요는 없습니다.
단위 테스트와 기존 Testcontainers 기반 보안·OpenAPI 테스트에서 발급 계약과 접근 권한을 검증합니다.

## 미디어 저장소

`infra/media-storage.yaml`은 비공개 S3 버킷과 CloudFront 배포를 생성합니다. S3 객체는
CloudFront OAC를 통해서만 읽을 수 있고, 정적 미디어 배포는 HTTPS와 압축을 사용합니다.
스택은 `ap-northeast-2`에 배포하며 CloudFront 기본 도메인을 출력합니다.
프로필·게시글 이미지는 브라우저가 `images/{userId}/{uuid}.jpg|png` 경로에 직접 PUT합니다.
SSE-S3(`AES256`), 이미지 형식에 맞는 `Content-Type`, 1년 `immutable` 캐시를 서명에 포함합니다.
객체 ACL로 공개 권한을 추가하지 않고, 설정된 HTTPS 배포 주소로 이미지 URL을 반환합니다.

| 환경 변수 | 용도 |
| --- | --- |
| `MEDIA_S3_BUCKET` | 업로드할 기존 비공개 S3 버킷 이름 |
| `MEDIA_PUBLIC_BASE_URL` | 이미지 배포용 HTTPS 기본 주소(예: `https://img.codeiary.com`) |
| `AWS_REGION` | S3 버킷 리전, 기본값 `ap-northeast-2` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | SSM에서 전달받는 미디어 업로드 전용 IAM 자격증명 |

S3 Presigner는 `S3PresignerConfig`가 주입받은 키로 `StaticCredentialsProvider`를 구성합니다. Lightsail의 API 컨테이너에는
전용 IAM 사용자 `codeiary-media-uploader`의 자격증명을 환경 변수로 전달합니다.
이 사용자는 `arn:aws:s3:::<bucket>/images/*`에 대한 `s3:PutObject` 권한만 가지며,
호스트의 SSM 관리 자격증명을 API와 공유하지 않습니다.
권한 문서는 `deploy/media-upload-policy.json`에 있으며 HTTPS와 SSE-S3 암호화를 조건으로 제한합니다.
발급 주체에 `s3:GetObject`나 `s3:DeleteObject` 권한은 필요하지 않습니다.
기존 CloudFront OAC의 읽기 전용 권한과 S3 Block Public Access 설정은 유지합니다.
배포용 GitHub OIDC 역할 설정만으로 실행 중인 API 컨테이너에 자격증명이 전달되지는 않습니다.
버킷 이름이나 유효한 HTTPS 공개 주소가 없으면 URL 발급은 503으로 응답합니다.

서울 리전 Parameter Store에 다음 값을 저장합니다.

| 파라미터 | 형식 |
| --- | --- |
| `/codeiary/prod/media-s3-bucket` | `String` |
| `/codeiary/prod/media-public-base-url` | `String` |
| `/codeiary/prod/media-access-key-id` | `SecureString` |
| `/codeiary/prod/media-secret-access-key` | `SecureString` |

`deploy/deploy-api.sh`는 기존 DB·JWT 값과 함께 이 값을 읽고 서울 리전을 지정합니다.
자격증명은 권한 `600`인 `/run`의 임시 Compose env 파일로만 전달하며 배포 종료 시 삭제합니다.
운영 `.env`나 이미지에 키를 저장하지 않습니다. 키 교체 시 SSM 값을 갱신하고 API를 다시 배포합니다.

S3 CORS는 기존 GET·HEAD 규칙을 유지하고 직접 PUT용 규칙을 별도로 사용합니다.
PUT 허용 출처는 `https://codeiary.com`, `https://www.codeiary.com`,
`http://localhost:5173`, `http://127.0.0.1:5173`으로 제한합니다.
운영 버킷에도 반환된 필수 헤더를 preflight에서 허용하는 CORS 설정을 적용합니다.
CORS는 버킷 접근 권한을 부여하지 않으며, Presigned URL의 서명과 IAM 권한은 계속 적용됩니다.
`uploadUrl`은 임시 업로드 권한을 담으므로 URL 전체와 서명 쿼리·임시 자격증명을 로그에 남기거나 공유하지 않습니다.
진단에는 요청 ID와 객체 경로 등 비밀이 없는 값만 사용합니다.

서버의 배포 스크립트·Compose와 SSM 값을 먼저 갱신한 뒤 API를 배포합니다.
배포 후에는 브라우저에서 URL 발급·S3 PUT·CloudFront 조회·프로필 저장을 확인합니다.
동일한 URL로 재업로드하면 412, 미등록 출처의 preflight와 서명 없는 S3 조회는 403이어야 합니다.
추가 서비스는 생성하지 않으며 기존 S3 저장·PUT 요청과 CloudFront 전송 사용량에 따라 비용이 발생합니다.
운영 추적에는 S3 접근 로그, CloudTrail 데이터 이벤트와 CloudWatch 지표를 권장하며,
로그 저장소도 암호화합니다. 활성화 시 해당 로그·이벤트 비용을 함께 확인합니다.

관련 AWS 문서: [Presigned URL](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html),
[S3 CORS](https://docs.aws.amazon.com/AmazonS3/latest/userguide/cors.html),
[조건부 쓰기](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html).

`img.codeiary.com` 사용자 지정 도메인을 연결하려면 `us-east-1` ACM 인증서를 DNS 검증하고,
Cloudflare에 ACM 검증 CNAME과 CloudFront 대상 CNAME(`img` → 배포 도메인, DNS only)을 등록한 뒤
`DomainCertificateArn` 파라미터로 스택을 갱신합니다.

로컬 개발용 Compose 설정은 `.env.example`을 참고합니다. 운영 환경에서는 `.env` 파일을
사용하지 않습니다. ECR Public 이미지는 서버에서 별도 로그인 없이 내려받습니다.
