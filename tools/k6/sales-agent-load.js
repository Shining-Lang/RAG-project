import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const USERNAME = __ENV.USERNAME || 'admin';
const PASSWORD = __ENV.PASSWORD || 'demo123';
const KB_IDS = (__ENV.KB_IDS || '1').split(',').map((id) => Number(id.trim()));

export const options = {
  scenarios: {
    steady_sales_agent: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: Number(__ENV.VUS || 3) },
        { duration: __ENV.DURATION || '2m', target: Number(__ENV.VUS || 3) },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<20000'],
    sales_agent_latency: ['p(95)<20000'],
    sales_agent_ok: ['rate>0.95'],
  },
};

const salesAgentLatency = new Trend('sales_agent_latency');
const salesAgentOk = new Rate('sales_agent_ok');

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
    '本季度销售冠军是谁？请给出原因。',
    '近 6 个月销售趋势怎么样？',
    '哪些大区存在异常下滑风险？',
    '结合销售手册，给我一份客户续约跟进建议。',
  ];
  const message = questions[__ITER % questions.length];

  const started = Date.now();
  const res = http.post(
    `${BASE_URL}/api/v1/sales-agent/chat`,
    JSON.stringify({
      sessionId: `k6-sales-${__VU}`,
      message,
      kbIds: KB_IDS,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${data.token}`,
      },
    },
  );
  const elapsed = Date.now() - started;
  salesAgentLatency.add(elapsed);

  const ok = check(res, {
    'agent status is 200': (r) => r.status === 200,
    'agent api code is 200': (r) => r.json('code') === 200,
    'agent has route': (r) => Boolean(r.json('data.route')),
    'agent has answer': (r) => Boolean(r.json('data.answer')),
  });
  salesAgentOk.add(ok);

  sleep(Number(__ENV.SLEEP_SECONDS || 1));
}
