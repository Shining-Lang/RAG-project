import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'demo123';
const KB_IDS = (__ENV.KB_IDS || '1').split(',').map((id) => Number(id.trim()));

export const options = {
  scenarios: {
    steady_rag_query: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: Number(__ENV.VUS || 5) },
        { duration: __ENV.DURATION || '2m', target: Number(__ENV.VUS || 5) },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<15000'],
    rag_query_latency: ['p(95)<15000'],
    rag_query_ok: ['rate>0.95'],
  },
};

const ragQueryLatency = new Trend('rag_query_latency');
const ragQueryOk = new Rate('rag_query_ok');

export function setup() {
  const login = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(login, {
    'login status is 200': (res) => res.status === 200,
    'login returns token': (res) => Boolean(res.json('data')),
  });

  return {
    token: login.json('data'),
  };
}

export default function (data) {
  const questions = [
    '客户续约异议应该怎么处理？',
    '销售管道停留时间过长时应该优先检查什么？',
    '华东区业绩下滑时应该如何诊断？',
    '报价折扣审批流程有哪些注意事项？',
  ];
  const question = questions[__ITER % questions.length];

  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/rag/query`,
    JSON.stringify({ question, kbIds: KB_IDS }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
      },
    },
  );
  const elapsed = Date.now() - started;
  ragQueryLatency.add(elapsed);

  const ok = check(res, {
    'rag status is 200': (r) => r.status === 200,
    'rag api code is 200': (r) => r.json('code') === 200,
    'rag has answer field': (r) => r.json('data.answer') !== undefined,
  });
  ragQueryOk.add(ok);

  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
