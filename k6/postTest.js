import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    reservation_create: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
};

export default function () {
  const payload = JSON.stringify({
    date: '2026-09-10',
    time: '15:00',
    type: 'CAKE',
    roomNo: '101',
    phone: '01012345678',
    paid: false,
    memo: 'k6 test'
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const res = http.post('http://localhost:8080/reservations', payload, params);

  check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'not server error': (r) => r.status < 500,
  });
}