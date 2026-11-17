import http from 'k6/http';

export const options = {
    scenarios: {
        create: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RPS || 1),
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 10
        }
    }, thresholds: {http_req_failed: ['rate<0.01']}
};
export default function () {
    http.post(`${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1/links`, JSON.stringify({originalUrl: 'https://example.com'}), {
        headers: {
            'Authorization': `Bearer ${__ENV.TOKEN}`,
            'Content-Type': 'application/json'
        }
    });
}
