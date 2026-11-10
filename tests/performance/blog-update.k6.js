import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const postIds = JSON.parse(__ENV.POST_IDS || '[]');
const users = Number(__ENV.VUS || 20);

if (!__ENV.ACCESS_TOKEN || postIds.length < users) {
  throw new Error('ACCESS_TOKEN과 VU별 POST_IDS가 필요합니다.');
}
if (baseUrl !== 'http://localhost:8080') {
  throw new Error('이 스크립트는 로컬 목 API 테스트용입니다.');
}

export const options = {
  scenarios: {
    update_blog: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: users },
        { duration: '2m', target: users },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<5000'],
    checks: ['rate>0.99'],
  },
  discardResponseBodies: true,
};

export default function () {
  const response = http.put(
    `${baseUrl}/posts/${postIds[__VU - 1]}`,
    JSON.stringify({
      title: `[k6 임시] VU ${__VU}`,
      content: `# 부하 테스트\n\n${'일정한 크기의 Markdown 본문입니다.\n'.repeat(40)}\n수정 ${__ITER}`,
      category: __ENV.CATEGORY || '부하테스트',
      tags: [],
      representativeImageUrl: null,
      publicPost: true,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Cookie: `access_token=${__ENV.ACCESS_TOKEN}`,
      },
      tags: { name: 'PUT /posts/:id' },
      timeout: '30s',
    },
  );
  check(response, { '수정 성공 (200)': (r) => r.status === 200 });
  sleep(1);
}
