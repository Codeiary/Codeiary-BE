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

GitHub Actions repository variables:

- `AWS_DEPLOY_ROLE_ARN`, `AWS_REGION`, `SSM_INSTANCE_ID`

Access Key 대신 GitHub OIDC 임시 자격증명을 사용합니다. 프론트/백엔드 역할과 S3
버킷은 `infra/aws/github-deploy.yml`로 생성하며, 출력되는 managed-node 정책은
Lightsail SSM hybrid activation 역할에 연결합니다.

Ubuntu Lightsail 인스턴스 생성 후 최초 한 번 다음 스크립트를 실행합니다.

```sh
chmod +x deploy/bootstrap-lightsail.sh
./deploy/bootstrap-lightsail.sh
```

Lightsail 네트워크 방화벽은 `22`, `80`, `443`만 허용하고 `8080`, `5432`는
열지 않습니다.

서버의 `/opt/codeiary/.env`에 DB 설정을 최초 한 번 저장하고 권한을 `600`으로
제한합니다. private GHCR 이미지라면 서버에서 read 권한 토큰으로 최초 한 번
`docker login ghcr.io`를 수행합니다.
