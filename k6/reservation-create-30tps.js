import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    reservation_create_30tps: {
      executor: 'constant-arrival-rate',
      rate: 30,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
};

export default function () {
  const unique = (__VU * 1000) + __ITER;

  const day = 1 + (unique % 28);                // 1~28일
  const hour = 9 + (Math.floor(unique / 28) % 10); // 9~18시
  const minute = Math.floor(unique / (28 * 10)) % 60;

  const payload = JSON.stringify({
    cakeId: 5,
    resDate: `2026-10-${String(day).padStart(2, '0')}`,
    resTime: `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`,
    contact: `010-${String(1000 + (__VU % 9000)).padStart(4, '0')}-${String(1000 + (__ITER % 9000)).padStart(4, '0')}`,
    paid: true
  });

  const res = http.post('http://localhost:8080/api/reservations', payload, {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.TOKEN}`,
    },
    redirects: 0,
  });

  if (__ITER < 5 && __VU <= 2) {
    console.log(`VU=${__VU}, ITER=${__ITER}, payload=${payload}, status=${res.status}`);
  }

  check(res, {
    'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
    'not server error': (r) => r.status < 500,
  });
}