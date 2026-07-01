import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
};

export default function () {
  const res = http.get('http://localhost:8080/reservations?date=2026-09-10');

check(res, {
  'status is 200': (r) => r.status === 200,
  'response time < 50ms': (r) => r.timings.duration < 50,
});

  sleep(1);
}