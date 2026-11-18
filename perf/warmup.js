// 앱을 띄운 직후 한 번 돌린다. JVM은 처음 실행하는 코드를 느리게 돌리므로(JIT 컴파일 전),
// 측정할 시나리오와 같은 선점·결제 경로를 30초 동안 미리 실행해 둔다. 결과는 기록하지 않는다.
import exec from 'k6/execution';
import { createPerformance, hold, login, loginUsers, pay } from './lib/common.js';

const RATE = 20;
const DURATION_SECONDS = 30;
const USERS = 200;

export const options = {
    setupTimeout: '3m',
    scenarios: {
        warmup: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: `${DURATION_SECONDS}s`,
            preAllocatedVUs: 50,
            maxVUs: 100,
        },
    },
};

export function setup() {
    const performance = createPerformance(login('perf-admin@ticatch.test'), RATE * (DURATION_SECONDS + 1));
    return { ...performance, tokens: loginUsers(USERS) };
}

export default function (data) {
    const iteration = exec.scenario.iterationInTest;
    const token = data.tokens[iteration % USERS];
    const result = hold(token, data.performanceId, [data.seatIds[iteration]]);
    if (result.reservationId) {
        pay(token, result.reservationId);
    }
}

export function handleSummary() {
    return { stdout: '[warmup] 끝\n' };
}
