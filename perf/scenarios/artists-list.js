import http from 'k6/http';
import { check, sleep } from 'k6';
import { registerAndAuthenticate } from '../lib/auth.js';

// Baseline: cursor-paginated artist catalog listing (storage-native scan order).
// Minimal catalog in the foundation phase — same rationale as song-search.js.

export const options = {
    vus: 10,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<250', 'p(99)<500'],
    },
};

export function setup() {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    return { token: registerAndAuthenticate(baseUrl), baseUrl };
}

export default function (data) {
    const res = http.get(`${data.baseUrl}/api/v1/artists?limit=20`, {
        headers: { Authorization: `Bearer ${data.token}` },
    });
    check(res, {
        'status 200': (r) => r.status === 200,
        'page envelope': (r) => r.json('content') !== undefined,
    });
    sleep(0.2);
}
