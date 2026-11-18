// 회차 취소: 확정 예매 RESERVATIONS건으로 매진된 회차(cancel.sql이 만든 ID 1)를 관리자가 취소한다(ADR 0009).
// 취소하는 동안 두 가지 요청을 계속 보낸다.
// - 그 회차를 산 회원 100명의 포인트 충전: 취소가 회원 지갑을 잠근 동안 얼마나 기다리는지 본다.
// - 공연 목록 조회(관계없는 요청): 취소가 길어질 때 앱 전체가 느려지는지 본다.
import http from 'k6/http';
import exec from 'k6/execution';
import { fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { BASE_URL, login, loginUsers, readers, report } from './lib/common.js';

const PERFORMANCE_ID = 1;
const CHARGERS = 100;
const BACKGROUND_SECONDS = __ENV.BACKGROUND_SECONDS || '20';

const cancelDuration = new Trend('cancel_duration', true);
const canceledReservations = new Counter('canceled_reservations');
const refundedPayments = new Counter('refunded_payments');
const chargeDuration = new Trend('charge_duration', true);
const chargeFailed = new Counter('charge_failed');
const browseDuration = new Trend('browse_duration', true);
const browseFailed = new Counter('browse_failed');

export const options = {
    setupTimeout: '3m',
    summaryTrendStats: ['count', 'med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        cancel: {
            executor: 'shared-iterations', vus: 1, iterations: 1, startTime: '2s', maxDuration: '15m', exec: 'cancel',
        },
        charges: {
            executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: `${BACKGROUND_SECONDS}s`,
            preAllocatedVUs: 50, maxVUs: 1000, exec: 'charge',
        },
        browse: {
            executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: `${BACKGROUND_SECONDS}s`,
            preAllocatedVUs: 10, maxVUs: 300, exec: 'browse',
        },
    },
};

export function setup() {
    return { admin: login('perf-admin@ticatch.test'), tokens: loginUsers(CHARGERS) };
}

export function cancel(data) {
    const response = http.post(`${BASE_URL}/api/v1/admin/performances/${PERFORMANCE_ID}/cancel`, null,
        { headers: { Authorization: data.admin }, timeout: '15m' });
    if (response.status !== 200) {
        fail(`회차 취소 실패: ${response.status} ${response.body}`);
    }
    cancelDuration.add(response.timings.duration);
    canceledReservations.add(response.json('canceledReservations'));
    refundedPayments.add(response.json('refundedPayments'));
}

export function charge(data) {
    const iteration = exec.scenario.iterationInTest;
    const response = http.post(`${BASE_URL}/api/v1/me/points/charges`, JSON.stringify({ amount: 1000 }), {
        headers: {
            'Content-Type': 'application/json',
            Authorization: data.tokens[iteration % CHARGERS],
            'Idempotency-Key': `perf-charge-${iteration}`,
        },
        timeout: '15m',
    });
    chargeDuration.add(response.timings.duration);
    if (response.status >= 300) {
        chargeFailed.add(1);
    }
}

export function browse() {
    const response = http.get(`${BASE_URL}/api/v1/concerts?size=20`, { timeout: '15m' });
    browseDuration.add(response.timings.duration);
    if (response.status !== 200) {
        browseFailed.add(1);
    }
}

export function handleSummary(data) {
    const { count, ms, metric, trend } = readers(data);
    return report(data, `cancel-${__ENV.RESERVATIONS || '?'}`, [
        `  취소   ${ms(metric('cancel_duration').max)} 취소한 예매=${count('canceled_reservations')} `
        + `환불한 결제=${count('refunded_payments')}`,
        `  충전   요청=${count('charge_duration')} 실패=${count('charge_failed')}  ${trend('charge_duration')}`,
        `  목록   요청=${count('browse_duration')} 실패=${count('browse_failed')}  ${trend('browse_duration')}`,
        `  부하   놓친 반복=${count('dropped_iterations')}`,
    ]);
}
