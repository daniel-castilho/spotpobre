import http from 'k6/http';

const ALPHANUMERIC = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

function randomString(length) {
    let out = '';
    for (let i = 0; i < length; i += 1) {
        out += ALPHANUMERIC[Math.floor(Math.random() * ALPHANUMERIC.length)];
    }
    return out;
}

/**
 * Registers a fresh user through the public API and returns a bearer token.
 *
 * Registration is durable-idempotent, so every call sends a unique e-mail and a
 * unique Idempotency-Key (16-128 chars of [A-Za-z0-9._:-]) to guarantee the
 * request is never treated as a replay of a stored outcome. Verification e-mail
 * delivery is best-effort server-side, so no SES identity setup is required.
 */
export function registerAndAuthenticate(baseUrl) {
    const suffix = `${Date.now().toString(36)}-${randomString(8)}`;
    const res = http.post(
        `${baseUrl}/api/v1/auth/register`,
        JSON.stringify({
            name: 'Perf User',
            email: `perf-${suffix}@example.com`,
            password: 'password123',
            country: 'US',
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': `perf-${suffix}-${randomString(16)}`,
            },
        },
    );
    if (res.status < 200 || res.status >= 300) {
        throw new Error(`registration failed: HTTP ${res.status} — ${res.body}`);
    }
    const token = res.json('token');
    if (!token) {
        throw new Error(`registration returned no token: ${res.body}`);
    }
    return token;
}
