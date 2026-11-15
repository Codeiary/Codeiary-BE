# 운영 배포와 관측

## 운영 구조

백엔드는 EC2에서 실행하고 PostgreSQL은 RDS의 `codeiary` DB를 사용합니다.
프론트는 Lightsail, API는 `https://api.codeiary.com`으로 분리되어 있습니다.

main 반영 시 GitHub Actions가 OIDC로 AWS에 인증하고 ARM64 이미지를 비공개 ECR에
등록한 뒤 SSM `CodeiaryDeployApi`로 배포합니다. `/health` 확인에 실패하면 이전 이미지로 복구합니다.

GitHub Actions 변수는 `EC2_AWS_ROLE_ARN`, `EC2_SSM_INSTANCE_ID`입니다.
배포 스크립트 `deploy/deploy-api.sh`는 애플리케이션 포트 `8080`과 Prometheus 지표 포트 `9091`을 게시합니다.
이 스크립트 변경 후에는 EC2의 `/usr/local/sbin/codeiary-deploy-api`에도 같은 버전을 반영해야 합니다.
스크립트는 서울 리전 `/codeiary/prod/`의 `db-url`,
`db-username`, `db-password`, `jwt-secret`, Google OAuth, GitHub App,
`redis/host`, `redis/port` 및 미디어 설정을 읽습니다. GitHub App 키는
`github-app-private-key` SecureString에서 읽어 EC2의 `/opt/codeiary/secrets/`에 저장하고
컨테이너에는 읽기 전용으로 마운트합니다. 다른 비밀값은 임시 환경 파일로 전달하고
배포 종료 시 임시 파일을 삭제합니다. 노드형 Valkey 연결에는 TLS를 사용하며,
`redis/host`와 `redis/port`를 애플리케이션에 전달합니다.
S3 업로드는 EC2 IAM 역할과 버킷 정책으로 허용하며 정적 AWS 키를 사용하지 않습니다.

운영 애플리케이션은 요청마다 trace ID와 사용자 ID를 MDC에 넣어 시작·종료 로그를 남기며, `X-Request-Id` 응답 헤더로 같은 요청을 확인할 수 있습니다. 4xx 응답은 `WARN`, 5xx 응답은 `ERROR` 레벨입니다.
Docker `json-file` 드라이버는 로그 파일당 10MiB, 최대 3개까지 보관하고, Grafana Alloy는 로그 파일을 읽기 전용으로 마운트해 `172.31.14.120:3100`의 Loki로 전송합니다.
Alloy에는 Docker 소켓을 마운트하지 않아 Docker 데몬 제어 권한을 주지 않습니다.
모니터링 보안 그룹의 TCP 3100 인바운드는 백엔드 보안 그룹에서만 허용해야 합니다.

운영 DB는 기존 데이터와 Flyway 이력을 유지합니다. 마이그레이션은 `src/main/resources/db/migration/`에서 버전별로 관리합니다.
`compose.yaml`은 로컬 개발용 PostgreSQL만 실행하며 운영 배포에는 사용하지 않습니다.

## GitHub App 설정

OAuth 콜백은 `/login/oauth2/code/github`로 등록한다. 앱 설치 화면의 `Request user authorization (OAuth) during installation`은 끈다. 설치 후 이동 주소는 `Setup URL`에서 별도로 지정한다.

GitHub에서 받은 개인 키가 `BEGIN RSA PRIVATE KEY` 형식이면 PKCS#8로 변환한다. 개인 키는 Git·이미지·로그에 넣지 않는다.

```bash
openssl pkcs8 -topk8 -nocrypt -in github-app.pem -out github-app-pkcs8.pem
chmod 600 github-app-pkcs8.pem
```
