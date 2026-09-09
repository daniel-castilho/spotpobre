import http from 'k6/http';
import { check, sleep } from 'k6';
import { vuToken } from '../lib/auth.js';

// Baseline: song search (title-prefix GSI query). The foundation phase runs
// against a minimal catalog, so this measures the infrastructure overhead
// floor (auth filter + rate-limit admission + use case + DynamoDB round-trip +
// pagination envelope), not search at scale — budgets are regression tripwires,
// not capacity tests.

export const options = {
    vus: 10,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<350', 'p(99)<600'],
    },
};

export default function () {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    // One user PER VU: the search rate limit (120/min/user) is part of the
    // measured contract. A single shared identity would exhaust its bucket
    // within seconds under 10 VUs and the scenario would measure 429s
    // (~88% fail rate) instead of the search read path.
    const token = vuToken(baseUrl);
    const res = http.get(
        `${baseUrl}/api/v1/songs/search?query=baseline&limit=20`,
        { headers: { Authorization: `Bearer ${token}` } },
    );
    check(res, {
        'status 200': (r) => r.status === 200,
        'page envelope': (r) => r.json('content') !== undefined,
    });
    sleep(0.2);
}
