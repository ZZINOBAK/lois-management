import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    reservation_create_concurrency: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 100,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const payload = JSON.stringify({
    cakeId: 5,
      resDate: '2026-09-11',
      resTime: '16:00:00',
      contact: `010-${String(9000 + __ITER)}-${String(1000 + __ITER)}`,
      paid: true
  });

  const res = http.post('http://localhost:8080/api/reservations', payload, {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.TOKEN}`,
    },
    redirects: 0,
  });

 if (__ITER < 5) {
   console.log(`status=${res.status}, body=${res.body}`);
 }
 if (__ITER === 0) {
   console.log(`TOKEN=${__ENV.TOKEN}`);
 }

  check(res, {
    'not redirected to login': (r) =>
      r.status !== 302 && !String(r.body).includes('<title>login</title>'),

    'reservation success or limit fail': (r) =>
      r.status === 200 || r.status === 201 || r.status === 400 || r.status === 409,

    'not server error': (r) =>
      r.status < 500,
  });
}