import http from 'k6/http';
import exec from 'k6/execution';
import { fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

const PASSWORD = 'password1234';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// 409(좌석 선점 실패 등)는 업무상 정상 응답이라 http_req_failed에 넣지 않는다.
http.setResponseCallback(http.expectedStatuses(200, 201, 204, 409));

export const holdDuration = new Trend('hold_duration', true);
export const payDuration = new Trend('pay_duration', true);
export const holdOk = new Counter('hold_ok');
export const holdSeatTaken = new Counter('hold_seat_taken');
export const holdFailed = new Counter('hold_failed');
export const payOk = new Counter('pay_ok');
export const payFailed = new Counter('pay_failed');
export const bookingOk = new Counter('booking_ok');
export const bookingGaveUp = new Counter('booking_gave_up');
export const serverErrors = new Counter('server_errors');
/** 시나리오가 시작된 뒤 예매가 끝나기까지 걸린 시간. 최댓값이 매진까지 걸린 시간이다. */
export const bookedAt = new Trend('booked_at', true);

export function userEmail(number) {
    return `perf-user-${number}@ticatch.test`;
}

export function login(email) {
    const response = http.post(`${BASE_URL}/api/v1/auth/login`,
        JSON.stringify({ email, password: PASSWORD }), { headers: JSON_HEADERS });
    if (response.status !== 200) {
        fail(`로그인 실패 ${email}: ${response.status} ${response.body}`);
    }
    return `Bearer ${response.json('accessToken')}`;
}

/** 회원 1번부터 {@code count}명을 로그인시켜 access token을 모은다. 측정 전 setup에서만 부른다. */
export function loginUsers(count) {
    const tokens = [];
    for (let start = 1; start <= count; start += 50) {
        const requests = [];
        for (let number = start; number < Math.min(start + 50, count + 1); number++) {
            requests.push(['POST', `${BASE_URL}/api/v1/auth/login`,
                JSON.stringify({ email: userEmail(number), password: PASSWORD }), { headers: JSON_HEADERS }]);
        }
        for (const response of http.batch(requests)) {
            if (response.status !== 200) {
                fail(`회원 로그인 실패: ${response.status} ${response.body}`);
            }
            tokens.push(`Bearer ${response.json('accessToken')}`);
        }
    }
    return tokens;
}

/** 관리자 API로 공연장, 공연, 회차(등급 하나, 좌석 {@code seatCount}석)를 만들고 공개한다. */
export function createPerformance(adminToken, seatCount) {
    const headers = { ...JSON_HEADERS, Authorization: adminToken };
    const venueId = post('/api/v1/admin/venues', { name: '측정홀', address: '서울' }, headers).json('venueId');
    const concertId = post('/api/v1/admin/concerts', { title: '측정 공연', description: null }, headers).json('concertId');
    const day = 24 * 60 * 60 * 1000;
    const performanceId = post(`/api/v1/admin/concerts/${concertId}/performances`, {
        venueId,
        startsAt: new Date(Date.now() + 30 * day).toISOString(),
        bookingOpensAt: new Date(Date.now() - day).toISOString(),
        seatGrades: [{ name: 'S', price: 100000, seatCount }],
    }, headers).json('performanceId');
    post(`/api/v1/admin/concerts/${concertId}/open`, null, headers);
    return { performanceId, seatIds: availableSeats(performanceId) };
}

export function availableSeats(performanceId) {
    const response = http.get(`${BASE_URL}/api/v1/performances/${performanceId}/seats`);
    if (response.status !== 200) {
        fail(`좌석 조회 실패: ${response.status} ${response.body}`);
    }
    return response.json('seats').filter((seat) => seat.available).map((seat) => seat.seatId);
}

/** 좌석을 선점한다. 성공하면 예매 ID, 실패하면 에러 코드를 돌려준다. */
export function hold(token, performanceId, seatIds) {
    const response = http.post(`${BASE_URL}/api/v1/reservations`, JSON.stringify({ performanceId, seatIds }),
        { headers: { ...JSON_HEADERS, Authorization: token }, tags: { name: 'hold' } });
    holdDuration.add(response.timings.duration);
    if (response.status === 201) {
        holdOk.add(1);
        return { reservationId: response.json('reservationId') };
    }
    const code = errorCode(response);
    if (code === 'SEAT_NOT_AVAILABLE') {
        holdSeatTaken.add(1);
    } else {
        holdFailed.add(1);
    }
    return { code };
}

export function pay(token, reservationId) {
    const response = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({ reservationId }), {
        headers: { ...JSON_HEADERS, Authorization: token, 'Idempotency-Key': `perf-${reservationId}` },
        tags: { name: 'pay' },
    });
    payDuration.add(response.timings.duration);
    if (response.status === 201) {
        payOk.add(1);
        return true;
    }
    errorCode(response);
    payFailed.add(1);
    return false;
}

/** 예매 하나가 끝났음을 기록한다. */
export function booked() {
    bookingOk.add(1);
    bookedAt.add(Date.now() - exec.scenario.startTime);
}

/** 측정에 쓰는 숫자만 짧게 출력하고, 전체 결과는 perf/results에 JSON으로 남긴다. */
export function summary(data, name) {
    const metric = (key) => data.metrics[key] ? data.metrics[key].values : {};
    const count = (key) => metric(key).count || 0;
    const ms = (value) => (value === undefined ? '-' : `${Math.round(value)}ms`);
    const trend = (key) => `p50=${ms(metric(key).med)} p95=${ms(metric(key)['p(95)'])} `
        + `p99=${ms(metric(key)['p(99)'])} max=${ms(metric(key).max)}`;
    const lastBookedSeconds = (metric('booked_at').max || 0) / 1000;
    const bookingsPerSecond = lastBookedSeconds > 0 ? (count('booking_ok') / lastBookedSeconds).toFixed(1) : '-';
    const text = [
        `[${name}] ${new Date().toISOString()}`,
        `  예매   성공=${count('booking_ok')} 포기=${count('booking_gave_up')} `
        + `마지막 성공=${ms(metric('booked_at').max)} 초당 예매=${bookingsPerSecond}`,
        `  선점   성공=${count('hold_ok')} 좌석 선점 실패=${count('hold_seat_taken')} 그 밖의 실패=${count('hold_failed')}  ${trend('hold_duration')}`,
        `  결제   성공=${count('pay_ok')} 실패=${count('pay_failed')}  ${trend('pay_duration')}`,
        `  오류   5xx·연결 실패=${count('server_errors')} 놓친 반복=${count('dropped_iterations')}`,
        '',
    ].join('\n');
    const file = `/perf/results/${name}-${new Date().toISOString().replace(/[:.]/g, '-')}.json`;
    return { stdout: text, [file]: JSON.stringify(data, null, 2) };
}

function post(path, body, headers) {
    const response = http.post(`${BASE_URL}${path}`, body === null ? null : JSON.stringify(body), { headers });
    if (response.status >= 300) {
        fail(`${path} 실패: ${response.status} ${response.body}`);
    }
    return response;
}

function errorCode(response) {
    if (response.status >= 500 || response.status === 0) {
        serverErrors.add(1);
    }
    try {
        return response.json('code') || `HTTP_${response.status}`;
    } catch (e) {
        return `HTTP_${response.status}`;
    }
}
