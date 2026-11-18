// 평소 부하: 1초에 예매 50건이 60초 동안 일정하게 들어온다. 회원마다 서로 다른 좌석을 골라 경합이 없다.
// 경합 없이 선점·결제 자체에 드는 시간을 잰다.
import exec from 'k6/execution';
import { booked, bookingGaveUp, createPerformance, hold, login, loginUsers, pay, summary } from './lib/common.js';

const RATE = 50;
const DURATION_SECONDS = 60;
const USERS = 1000;

export const options = {
    setupTimeout: '3m',
    summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        steady: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 100,
            maxVUs: 300,
        },
    },
};

export function setup() {
    // k6는 정해진 시간 끝에서 반복을 하나 더 시작할 수 있어, 1초 분량의 좌석을 더 만든다.
    const performance = createPerformance(login('perf-admin@ticatch.test'), RATE * (DURATION_SECONDS + 1));
    return { ...performance, tokens: loginUsers(USERS) };
}

export default function (data) {
    const iteration = exec.scenario.iterationInTest;
    const token = data.tokens[iteration % USERS];
    const result = hold(token, data.performanceId, [data.seatIds[iteration]]);
    if (result.reservationId && pay(token, result.reservationId)) {
        booked();
        return;
    }
    bookingGaveUp.add(1);
}

export function handleSummary(data) {
    return summary(data, 'steady');
}
