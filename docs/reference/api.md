# API 사용 계약

## API 문서

springdoc-openapi 3.1.1로 Swagger UI를 제공합니다. 서버 실행 후:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

운영 문서 주소는 `https://api.codeiary.com/swagger-ui/index.html`입니다.
Swagger는 인증 없이 조회할 수 있습니다. 인증 API는 로그인 후 브라우저에 설정된
`access_token` 쿠키를 사용하며, OpenAPI에는 `cookieAuth`로 표시합니다.

## 게시글 API

| 메서드 | 경로 | 동작 | 성공 응답 |
| --- | --- | --- | --- |
| POST | `/posts` | 로그인한 사용자의 게시글 생성 | 201 + 본문, Location |
| GET | `/posts/{postId}` | 게시글 단건 조회 | 200 + 본문 |
| PUT | `/posts/{postId}` | 작성자 본인의 게시글 전체 수정 | 200 + 본문 |
| DELETE | `/posts/{postId}` | 작성자 본인의 게시글 삭제 | 204 |
| GET | `/posts` | 공개 게시글 목록 조회 | 200 + 페이지 응답 |
| GET | `/posts/mine` | 내 게시글 목록 조회 | 200 + 페이지 응답 |

생성과 수정은 같은 JSON 형식을 사용합니다. 제목(최대 200자), Markdown 본문,
카테고리(최대 100자), `publicPost`는 필수입니다. 태그는 최대 10개, 각 30자까지 허용하고
공백을 제거한 소문자로 저장하며 중복은 하나로 합칩니다. 빈 태그는 허용하지 않습니다.
태그와 대표 이미지 URL(최대 2048자)은 선택이며, 수정 시 생략하거나 비우면 삭제됩니다.
본문의 줄바꿈과 공백은 그대로 저장합니다.

```json
{
  "title": "Spring 트랜잭션 정리",
  "content": "# 트랜잭션\n\n학습한 내용을 기록합니다.",
  "category": "Spring",
  "tags": ["java", "spring"],
  "representativeImageUrl": "https://img.codeiary.com/images/1/example.jpg",
  "publicPost": true
}
```

작성자는 요청한 인증 사용자를 기준으로 지정합니다. `USER`·`ADMIN`은 생성할 수 있고,
관리자를 포함해 다른 작성자의 글은 수정·삭제할 수 없습니다. 익명 변경 요청은 401,
온보딩 전 계정의 변경 요청과 타인의 공개 글 변경 요청은 403입니다.
공개 글은 로그인 없이 조회할 수 있고, 비공개 글은 작성자에게만 보입니다.
존재하지 않거나 접근할 수 없는 비공개 글은 `404 / POST_NOT_FOUND`로 응답합니다.

응답은 `id`, `author`(`id`, `nickname`, `profileImageUrl`), `title`, `content`,
`category`, `tags`, `representativeImageUrl`, `publicPost`, `viewCount`, `createdAt`, `updatedAt`을
포함합니다. 작성자의 로그인 이메일·OAuth 정보는 반환하지 않습니다.
조회수는 저장된 값을 반환하며 단건 조회 자체로 증가시키지 않습니다. 조회수 집계 정책은 별도 작업입니다.
대표 이미지는 기존 Presigned URL API로 업로드한 뒤 URL만 전달합니다.

목록 API의 기본 페이지 크기는 12개이며 요청 크기는 최대 50개로 제한합니다.
`page`는 0부터 시작하고, `sort`는 `LATEST`(기본값) 또는 `VIEWS`를 사용합니다.
`search`는 제목·카테고리·태그 이름을 부분 검색하며 Markdown 본문은 검색하지 않습니다.
`category`와 `tag`는 각각 정확히 일치하는 카테고리·태그 이름으로 필터링합니다.
공개 목록(`/posts`)에는 공개 글만 포함되며, 내 목록(`/posts/mine`)에는 본인의 공개·비공개 글이 모두 포함됩니다.
내 목록은 항상 최신순으로 반환합니다. 응답은 `content`, `page`, `size`, `totalElements`,
`totalPages`, `first`, `last`를 포함합니다. 목록 항목은 단건 응답의 본문을 제외한
작성자·제목·카테고리·태그·대표 이미지·공개 여부·조회수·시간 정보를 반환합니다.

게시글과 카테고리는 N:1이며, 같은 작성자의 같은 이름인 카테고리를 공유합니다.
서로 다른 사용자의 카테고리는 이름이 같아도 별개입니다. 게시글과 태그는
`blog_post_tag` 연결 엔티티로 N:M을 구성하며 태그는 모든 사용자가 공유합니다.
글에서 태그를 빼거나 글을 삭제해도 다른 글에서 사용하는 카테고리·태그는 삭제하지 않습니다.
목록은 작성자·카테고리를 함께 페이징한 뒤 해당 페이지의 태그를 일괄 조회합니다.

`V1__create_schema.sql`에 사용자·인증·게시글·카테고리·태그·댓글·좋아요 테이블과
관련 제약 조건 및 검색 인덱스가 정의되어 있습니다. 운영 데이터와 Flyway 이력은 유지합니다.

### 사용자 온보딩과 공개 프로필

사용자 모델·권한·저장소와 사용자·관리자 API는 `domain/users`에서 관리합니다.
인증 필터는 검증한 `User`를 principal로 저장하며, 본인 정보 조회·수정은
`@AuthenticationPrincipal User user`의 식별자를 사용합니다.
`UserMapper`가 본인용 `UserProfileResponse`와 공개용 `PublicUserProfileResponse`를 구분합니다.
사용자·관리자의 본인 프로필 조회는 `UserService.getProfile`을 공유하며, 관리자 권한 검사는 Security 설정에서 처리합니다.

| 요청 | 경로 | 인증 / 입력 | 성공 응답 |
| --- | --- | --- | --- |
| GET | `/users/me` | 인증 쿠키 | 본인 프로필 |
| GET | `/users/nickname-availability?nickname=...` | 인증 쿠키, 확인할 닉네임 | `{ "available": true/false }` |
| POST | `/users/me/onboarding` | 인증 쿠키, multipart 폼 | 저장한 본인 프로필 |
| POST | `/images/presigned-url` | USER·ADMIN 인증 쿠키, `contentType`·`contentLength` JSON | S3 업로드 URL·공개 이미지 URL·필수 헤더·만료 시각 |
| PUT | `/users/me/profile` | USER·ADMIN 인증 쿠키, 프로필 전체를 담은 JSON | 저장한 본인 프로필 |
| GET | `/users/{userId}` | 공개 | 공개 프로필 |
| GET | `/users/by-nickname/{nickname}` | 공개, 닉네임 대소문자 무관 | 공개 프로필 |

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
`POST /images/presigned-url`로 업로드 URL을 발급받고, 파일을 S3로 직접 전송합니다.
`PENDING` 계정은 업로드 URL을 발급받을 수 없습니다.

S3 업로드 성공 후 응답의 `imageUrl`을 `PUT /users/me/profile`의 `profileImageUrl`로
저장해야 사용자 프로필에 적용됩니다. URL 발급과 S3 업로드는 사용자 DB를 변경하지 않습니다.
이미지 URL 검증은 `ImageUrlValidator`가 담당하며, `UserService`는 S3 서명 서비스에 의존하지 않습니다.
기존 multipart `POST /users/me/profile-image`는 제거했으므로 프론트와 백엔드를 함께 배포해야 합니다.

프로필 수정은 전체 수정입니다. 선택 필드를 생략하거나 `null` 또는 빈 문자열로 보내면
기존 값을 삭제하므로 유지할 값도 함께 전송합니다. 로그인 이메일·실명·권한은 수정 입력에 포함하지 않습니다.

본인 프로필은 `id`, `email`, `name`, `nickname`, `profileImageUrl`, `onboardingCompleted`,
`role`, `githubUrl`, `contactEmail`을 반환합니다. 공개 프로필은 `id`, `nickname`,
`profileImageUrl`, `githubUrl`, `contactEmail`만 반환하며 로그인 이메일·실명·권한·Google 식별자는
포함하지 않습니다. `contactEmail`은 사용자가 직접 공개한 별도 연락처이며 로그인 이메일을 자동으로 복사하지 않습니다.
공개 조회는 활성 상태이고 온보딩을 완료한 계정만 제공하며, 그 외에는 404를 반환합니다.
