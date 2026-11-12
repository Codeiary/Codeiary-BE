import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const postIds = JSON.parse(__ENV.POST_IDS || '[]');
const users = Number(__ENV.VUS || 100);
const firstUpdate = new Trend('first_update_duration', true);
const secondUpdate = new Trend('second_update_duration', true);

if (baseUrl !== 'http://localhost:8080' || !__ENV.ACCESS_TOKEN || postIds.length !== users * 2) {
  throw new Error('로컬 주소, ACCESS_TOKEN, VU마다 다른 글 2개가 필요합니다.');
}

export const options = {
  scenarios: {
    two_updates_per_user: {
      executor: 'per-vu-iterations',
      vus: users,
      iterations: 1,
      maxDuration: '2m',
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
  for (let index = 0; index < 2; index++) {
    const response = http.put(
      `${baseUrl}/posts/${postIds[(__VU - 1) * 2 + index]}`,
      JSON.stringify({
        title: `[k6 임시] VU ${__VU} 글 ${index + 1}`,
        content: `# 부하 테스트\n\n${'일정한 크기의 Markdown 본문입니다.\n'.repeat(40)}\n수정 ${index + 1}`,
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
        tags: { name: 'PUT /posts/:id', sequence: index === 0 ? 'first' : 'second' },
        timeout: '60s',
      },
    );
    (index === 0 ? firstUpdate : secondUpdate).add(response.timings.duration);
    check(response, { '수정 성공 (200)': (r) => r.status === 200 });
  }
}
