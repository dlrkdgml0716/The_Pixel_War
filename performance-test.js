import http from 'k6/http';
import { sleep, check } from 'k6';

// 1. 테스트 설정: 반드시 export const options 안에 stages를 넣어야 합니다.
export const options = {
  stages: [
    { duration: '30s', target: 20 }, // 30초 동안 20명까지 서서히 증가 (Warm-up)
    { duration: '1m', target: 50 },  // 1분 동안 50명 유지 (본격적인 측정 구간)
    { duration: '30s', target: 0 },  // 30초 동안 다시 0명으로 감소 (Cool-down)
  ],
};

export default function () {
  // 1. 요청할 URL (EC2 탄력적 IP)
  const url = 'http://13.124.236.83:8080/api/pixels';

  // 2. 보낼 데이터
  const payload = JSON.stringify({
    lat: 37.1234,
    lng: 127.1234,
    color: '#FF0000',
    userId: `user_${__VU}` // 가상 사용자별로 다른 ID 부여
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // 3. POST 요청 보내기
  const res = http.post(url, payload, params);

  // 4. 검증
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  sleep(0.1);
}