# Codeiary API

Spring Boot 3, Java 21, PostgreSQL, Flyway 기반 API입니다.

## 로컬 실행

`.env.example`을 참고해 환경 변수를 설정하고 PostgreSQL을 실행한 다음:

```sh
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

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
