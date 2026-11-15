# 인증과 JWT 쿠키

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
| POST | `/auth/reissue` | `refresh_token` 쿠키, 본문 없음 | 204, 새 인증 쿠키 설정 |
| POST | `/auth/logout` | 인증 쿠키, 본문 없음 | 204, 인증 쿠키 삭제 |
| GET | `/users/me` | PENDING·USER·ADMIN 권한의 `access_token` 쿠키 | 사용자 프로필 JSON |
| GET | `/admin/me` | ADMIN 권한의 `access_token` 쿠키 | 사용자 프로필 JSON |

토큰 재발급·로그아웃은 Access Token이 만료되어도 호출할 수 있습니다.
재발급은 유효한 Refresh Token이 필요하며, 토큰 원문을 응답 JSON에 포함하지 않습니다.
프론트는 `credentials: "include"`로 인증 쿠키를 보내고 `/users/me`로 로그인 상태를 복원합니다.
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

`/admin/**`에는 ADMIN 권한이 필요합니다. `PENDING` 계정은 본인 조회,
닉네임 중복 확인, 온보딩 완료, 토큰 재발급·로그아웃 API와 공개 조회를 사용할 수 있습니다.
프로필 수정 등 나머지 일반 기능은 온보딩을 완료한 `USER` 또는 `ADMIN` 권한이 필요합니다.
GET `/users/me`와 `/users/nickname-availability`에도 인증이 필요하며,
공개 GET `/**`, OAuth2 진입·콜백, 재발급·로그아웃 외의 요청은 인증이 필요합니다.
인증 오류는 401, 권한 부족은 403이며
기존 `{ "message": "...", "code": "..." }` 형식을 사용합니다.
현재 CSRF 검사는 비활성화되어 있고, 인증 쿠키의 SameSite 기본값은 Strict입니다.

JWT 인증 업무는 `domain/auth`에서 관리합니다. `controller/TokenController`는 재발급·로그아웃 API를,
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

[Google OIDC와 GitHub OAuth2 연동 구조](<../architecture/ARCH-003 Google OIDC와 GitHub OAuth2 연동.md>)

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
