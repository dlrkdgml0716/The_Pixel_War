import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
  vus: 10,
  duration: '10s',
};

export default function () {
  // 1. 요청할 URL (본인의 Controller에 설정된 픽셀 작성 엔드포인트)
  const url = 'http://13.124.236.83:8080/api/pixels';

  // 2. 보낼 데이터 (PixelRequest DTO 구조와 일치해야 함)
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

  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  sleep(0.1);
}