// 티켓 오픈 순간: 회원 500명이 좌석 200석에 한꺼번에 몰린다(경합 지점 1).
// 회원은 빈 좌석 하나를 골라 선점하고, 남이 먼저 잡았으면 좌석 배치를 다시 보고 다른 좌석을 고른다(최대 5번).
// 선점에 성공하면 바로 결제한다.
import exec from 'k6/execution';
import {
    availableSeats, booked, bookingGaveUp, createPerformance, hold, login, loginUsers, pay, summary,
} from './lib/common.js';

const USERS = 500;
const SEATS = 200;
const MAX_ATTEMPTS = 5;

export const options = {
    setupTimeout: '3m',
    summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        rush: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '3m' },
    },
};

export function setup() {
    const performance = createPerformance(login('perf-admin@ticatch.test'), SEATS);
    return { ...performance, tokens: loginUsers(USERS) };
}

export default function (data) {
    const token = data.tokens[exec.vu.idInTest - 1];
    let seats = data.seatIds;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS && seats.length > 0; attempt++) {
        const seatId = seats[Math.floor(Math.random() * seats.length)];
        const result = hold(token, data.performanceId, [seatId]);
        if (result.reservationId) {
            if (pay(token, result.reservationId)) {
                booked();
            }
            return;
        }
        if (result.code !== 'SEAT_NOT_AVAILABLE') {
            break;
        }
        seats = availableSeats(data.performanceId);
    }
    bookingGaveUp.add(1);
}

export function handleSummary(data) {
    return summary(data, 'rush');
}
