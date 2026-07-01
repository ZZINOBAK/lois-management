import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    reservation_read: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
};

export default function () {
  const res = http.get('http://localhost:8080/reservations?date=2026-09-10');

  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
}