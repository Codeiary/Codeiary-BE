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

운영 인스턴스는 Lightsail `small_3_0` 플랜(2GB RAM)입니다. 방화벽은 `22`, `80`, `443`만
허용하고 `8080`, `5432`는 열지 않습니다.

서버의 `/opt/codeiary/.env`에 DB 설정을 최초 한 번 저장하고 권한을 `600`으로
제한합니다. ECR Public 이미지는 서버에서 별도 로그인 없이 내려받습니다.
