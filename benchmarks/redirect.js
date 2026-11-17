import http from 'k6/http';
import {check} from 'k6';

const rate = Number(__ENV.RPS || 10000);
export const options = {
    scenarios: {
        redirects: {
            executor: 'constant-arrival-rate',
            rate,
            timeUnit: '1s',
            duration: __ENV.DURATION || '60s',
            preAllocatedVUs: Number(__ENV.VUS || 1000),
            maxVUs: Number(__ENV.MAX_VUS || 5000)
        }
    },
    thresholds: {http_req_failed: ['rate<0.001'], http_req_duration: ['p(99)<20'], dropped_iterations: ['count==0']}
};
const codes = (__ENV.CODES || 'seeded-code').split(',');
export default function () {
    const code = codes[Math.floor(Math.random() * codes.length)];
    const res = http.get(`${__ENV.BASE_URL || 'http://localhost:8080'}/${code}`, {redirects: 0});
    check(res, {'redirect': r => r.status === 302 || r.status === 307});
}
