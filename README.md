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
Swagger는 인증 없이 조회할 수 있습니다. 인증 API는 로그인 후 브라우저에 설정된
`access_token` 쿠키를 사용하며, OpenAPI에는 `cookieAuth`로 표시합니다.

## OAuth2 로그인과 JWT 쿠키

기존 이메일·비밀번호 로그인 API 대신 Google OAuth2/OIDC 로그인을 사용합니다.
`/oauth2/authorization/google`에서 시작하며, 현재는 Google 이메일과 일치하는
기존 활성 `users` 계정만 로그인할 수 있습니다. 신규 가입·닉네임 중복 확인·온보딩 API는 아직 없습니다.
Google 설정에는 `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`이 필요하며,
로그인 성공 후 이동할 주소는 `oauth2.redirect-home`으로 설정합니다.

로그인 성공 시 JJWT로 Access Token과 Refresh Token을 발급하고 `HttpOnly` 쿠키로 전달합니다.
운영에서는 `Secure`, `SameSite=Strict`를 사용합니다. 쿠키 이름·경로·도메인 등은
`src/main/resources/application.yml`의 `token.cookie`에서 설정합니다.

| 요청 | 경로 | 입력 / 인증 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/api/auth/reissue` | `refresh_token` 쿠키, 본문 없음 | 204, 새 인증 쿠키 설정 |
| POST | `/api/auth/refresh` | `/reissue`와 같은 동작 | 204, 새 인증 쿠키 설정 |
| POST | `/api/auth/logout` | 인증 쿠키, 본문 없음 | 204, 인증 쿠키 삭제 |
| GET | `/api/users/me` | USER·ADMIN 권한의 `access_token` 쿠키 | 사용자 프로필 JSON |
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
`TokenCleanupService`가 매일 새벽 3시(Asia/Seoul)에 만료된 토큰과 블랙리스트 기록을 삭제합니다.
실행 시간과 활성화 여부는 `auth.token-cleanup.cron`, `auth.token-cleanup.enabled`로 설정합니다.

`/api/admin/**`에는 ADMIN 권한이 필요합니다. 공개 GET `/api/**`, OAuth2 진입·콜백,
재발급·로그아웃을 제외한 요청은 인증이 필요합니다. 인증 오류는 401, 권한 부족은 403이며
기존 `{ "message": "...", "code": "..." }` 형식을 사용합니다.
현재 CSRF 검사는 비활성화되어 있고, 인증 쿠키의 SameSite 기본값은 Strict입니다.

공통 인증 코드는 `global/security`에 둡니다. OAuth2 사용자 조회는 `service`,
로그인 결과 처리는 `handler`에서 담당합니다. 재발급·로그아웃 API는
`global/security/token/controller/TokenController`에 둡니다.
`global/security/token`의 `service/TokenSessionService`는 로그인 세션·재발급·폐기를,
`service/TokenService`는 쿠키 처리를 담당합니다. JWT 생성·검증은 `provider`,
인증 필터는 `cookie`, 토큰 엔티티와 저장소는 `entity`, `repository`에서 관리합니다.

사용자 모델·권한·저장소와 관리자 API는 `domain/users`에서 관리합니다.
인증 필터는 검증한 `User`를 principal로 저장하고, 관리자 컨트롤러는
`@AuthenticationPrincipal User user`를 받아 `AdminService`에 위임합니다.
`UserMapper`가 `UserProfileResponse`로 변환합니다.
프론트 모델에 맞춰 응답은 `id`, `email`, `name`, `nickname`, `profileImageUrl`,
`onboardingCompleted`, `role`을 포함합니다. 실명과 닉네임은 별도 필드이며,
닉네임은 한글·영문·숫자·밑줄 2~20자로 대소문자를 구분하지 않고 중복을 제한합니다.
프로필 이미지는 선택 사항이고, 온보딩 완료 여부는 닉네임 설정 여부로 계산합니다.

### 사용자 계정과 JWT 키

사용자 계정은 `users` 테이블에 직접 등록합니다. 소문자 `email`, `name`,
`enabled`, `created_at`, `updated_at`을 설정하며 `id`는 DB가 자동 생성합니다.
Google OAuth2 전용이므로 사용자 비밀번호를 보관하지 않습니다.
관리자 계정의 `role`은 `ADMIN`으로 지정하고, 생략하면 `USER`입니다.
닉네임·프로필 이미지가 없는 기존 계정은 그대로 유지됩니다.
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
예외 처리·OpenAPI·코드 생성·사용자 저장소 테스트와,
`global/security/token`의 JWT 검증·쿠키 처리·로그아웃·토큰 재발급·블랙리스트 단위 테스트가 있습니다.
사용자 테스트 객체는 `domain/users/fixture/UserFixture`에서 관리합니다.
새 인증 테스트는 mock 저장소와 MockMvc를 사용하며 DB 없이 실행할 수 있습니다.

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

Flyway의 `V1__create_auth_tables.sql` 하나로 `users`, `refresh_tokens`, `token_blacklist`와
관련 제약 조건·인덱스를 생성합니다. 닉네임·프로필 이미지와 대소문자를 구분하지 않는
닉네임 중복 방지 인덱스를 포함하며 사용자 비밀번호 컬럼은 없습니다.
DB를 초기화하고 적용하는 기준 스키마이므로 기존 DB의 `flyway_schema_history`도 함께 초기화해야 합니다.
새 계정의 기본 권한은 `USER`이며, 관리자 계정은 `role`을 `ADMIN`으로 지정합니다.

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
