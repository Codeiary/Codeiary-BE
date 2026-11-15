# 로컬 부하 테스트 실행

저장소 루트에서 명령을 실행합니다. 이 문서는 현재 저장소에 있는 부하 테스트 구성만 다룹니다. 측정 결과는 [TEST-001](<TEST-001 블로그 글 깃허브 자동화 테스트 결과.md>)을 참고하세요.

로컬 부하 테스트에서는 `local,loadtest` 프로필로 실행하면 GitHub 전송 클라이언트가 목 객체로 교체됩니다.
GitHub가 연동된 테스트 사용자의 글 생성·수정·삭제는 아웃박스와 Redis Stream을 거쳐 목 응답을 받습니다.
HTTP 요청은 목의 지연 시간을 기다리지 않습니다. 미연동 사용자는 아웃박스 기록을 건너뜁니다.
목 동기화에서는 실제 GitHub 토큰 발급·HTTP 요청·커밋이 발생하지 않습니다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local,loadtest'
```

기본 대기는 동기화 작업 전체당 500ms입니다. `GITHUB_MOCK_DELAY=2s`처럼 바꾸거나 `0ms`로 기준값을 측정할 수 있습니다.
이는 GitHub API의 충돌·호출 제한·파일 저장 동작을 재현하지 않고, 소비자 처리 지연과 대기열을 측정하는 용도입니다.
SQL 상세 로그는 이 프로필에서 끕니다. `prod`와 `loadtest` 프로필을 함께 사용하면 시작을 차단합니다.
`loadtest`는 블로그 동기화만 대체하므로 OAuth 연동 자체는 여전히 실제 GitHub를 사용합니다.

로컬 모니터링 구성인 `monitoring/`와 `compose.monitoring.yaml`은 Git·Docker 빌드 컨텍스트에서 제외합니다.
모니터링 서버는 별도로 준비하고 백엔드의 `/actuator/prometheus`를 수집합니다.
응답 시간 p95용 히스토그램은 `loadtest` 프로필에서 활성화합니다.

백엔드 자체의 자원을 제한하려면 호스트에서 실행 중인 8080 서버를 종료한 뒤 전용 컨테이너를 사용합니다.

```bash
./gradlew bootJar
docker compose -f compose.loadtest.yaml up -d --build
```

기본값은 알려진 EC2 t4g.micro의 자원 수에 맞춰 CPU 2코어·컨테이너 메모리 1GiB·추가 스왑 0·JVM `-Xms128m -Xmx512m`입니다.
다른 제한과 비교할 때는 `LOADTEST_CPUS`, `LOADTEST_MEMORY`, `LOADTEST_JAVA_OPTIONS`를 변경합니다.

```bash
LOADTEST_CPUS=1.0 LOADTEST_MEMORY=2g LOADTEST_JAVA_OPTIONS='-Xms256m -Xmx1g' \
  docker compose -f compose.loadtest.yaml up -d --no-build
```

이 로컬 CPU 제한은 Graviton2의 코어 성능·CPU 크레딧을 재현하지 않으며,
EC2의 OS·에이전트 메모리 사용량과 RDS 인스턴스·스토리지·네트워크 제한도 포함하지 않습니다.
먼저 Git에서 제외된 `.env.loadtest`에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `TOKEN_JWT_SECRET`을 설정합니다.
호스트 DB 접속 주소는 예를 들어 `jdbc:postgresql://host.docker.internal:5432/codeiary`를 사용합니다.
기존 로그인 쿠키를 유지하려면 호스트 백엔드와 동일한 JWT 서명 키를 사용합니다.
부하 발생기와 Prometheus·Grafana·PostgreSQL의 자원에는 이 제한이 적용되지 않습니다.
`Dockerfile.loadtest`는 `build/libs`의 실행 JAR만 복사하며 개인 키와 환경변수 파일을 이미지에 포함하지 않습니다.

`tests/performance/blog-update.k6.js`는 VU별 글 하나를 반복 수정하는 유지형 테스트입니다.
`tests/performance/blog-two-updates.k6.js`는 VU마다 다른 글 두 개를 순서대로 한 번씩 수정하는 버스트 테스트입니다.
두 스크립트 모두 로컬 주소만 허용하며 `ACCESS_TOKEN`, `POST_IDS`, `CATEGORY`, `VUS`를 환경변수로 받습니다.
테스트 데이터는 실행 전에 별도로 준비하고 종료 후 정리합니다. 버스트의 `POST_IDS`는 VU 수의 두 배가 필요합니다.
