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

## API 문서

springdoc-openapi 3.1.1로 Swagger UI를 제공합니다. 서버 실행 후:

- Swagger UI: `http://localhost:8080/api/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v3/api-docs`

문서와 정적 리소스는 운영 Nginx의 `/api/*` 프록시 경로를 사용합니다.
현재 인증 구현 전이므로 모든 요청을 `permitAll`로 허용하고 CSRF 검사를 비활성화했습니다.

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
코드 생성 테스트는 테스트 전용 엔티티로 QueryDSL 조회와 Lombok·MapStruct 연동을 확인합니다.
Swagger 테스트는 문서·UI 리소스 접근과 인증·CSRF 토큰 없는 일반 API 요청을 확인합니다.

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
