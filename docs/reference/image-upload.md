# 이미지 업로드와 미디어 저장소

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
운영에서는 EC2 IAM 역할의 자격 증명과 `AWS_REGION`, 기존 버킷·CloudFront 주소를 사용하며, 별도 endpoint를 설정하지 않습니다.

프론트 개발 서버에서만 `http://localhost:9090/codeiary-local/` 주소를 허용합니다.
운영 빌드와 기본 백엔드 설정은 HTTPS를 요구합니다. 로컬 저장소 포트는 이 컴퓨터에만 노출됩니다.
[S3Mock](https://github.com/adobe/S3Mock)은 업로드 흐름을 개발하기 위한 에뮬레이터이며,
Presigned URL의 서명·만료와 AWS IAM 권한을 검증하는 환경은 아닙니다.

### 업로드 계약

`POST /images/presigned-url`은 S3에 직접 업로드할 URL을 발급합니다.
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
const issued = await fetch('/images/presigned-url', {
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

기존 multipart `POST /images`와 `POST /users/me/profile-image`는 제거했습니다.
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

S3 Presigner는 운영에서 EC2 IAM 역할을 사용하고, 로컬 S3 저장소에서만 로컬 자격 증명을 사용합니다.
