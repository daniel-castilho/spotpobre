import http from 'k6/http';
import { check, sleep } from 'k6';
import { registerAndAuthenticate } from '../lib/auth.js';

// Baseline: authenticated profile read (cache-hit path after the first request).
// Exercises JWT filter chain -> cache -> DynamoDB fallback -> serialization.

export const options = {
    vus: 10,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        // Consultative budgets for the foundation phase; tighten after 2-3 runs
        // of collected data (see perf/README.md).
        http_req_duration: ['p(95)<150', 'p(99)<300'],
    },
};

export function setup() {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    return { token: registerAndAuthenticate(baseUrl), baseUrl };
}

export default function (data) {
    const res = http.get(`${data.baseUrl}/api/v1/users/me`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
        'returns e-mail': (r) => r.json('email') !== undefined,
    });
    sleep(0.2);
}
