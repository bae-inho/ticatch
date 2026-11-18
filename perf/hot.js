// 인기 좌석 쟁탈: 회원 300명이 좌석 60석에 몰리고, 한 명이 두 좌석을 한 번에 선점한다(경합 지점 1·2).
// 두 좌석은 무작위 순서로 보내, 서버가 잠그는 순서를 지키지 않으면 교착 상태가 생길 수 있게 한다.
// 남이 먼저 잡았으면 좌석 배치를 다시 보고 다른 두 좌석을 고른다(최대 5번). rush.js보다 행 잠금 경합이 잦다.
import exec from 'k6/execution';
import {
    availableSeats, booked, bookingGaveUp, createPerformance, hold, login, loginUsers, pay, summary,
} from './lib/common.js';

const USERS = 300;
const SEATS = 60;
const SEATS_PER_BOOKING = 2;
const MAX_ATTEMPTS = 5;

export const options = {
    setupTimeout: '3m',
    summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        hot: { executor: 'per-vu-iterations', vus: USERS, iterations: 1, maxDuration: '3m' },
    },
};

export function setup() {
    const performance = createPerformance(login('perf-admin@ticatch.test'), SEATS);
    return { ...performance, tokens: loginUsers(USERS) };
}

export default function (data) {
    const token = data.tokens[exec.vu.idInTest - 1];
    let seats = data.seatIds;
    for (let attempt = 1; attempt <= MAX_ATTEMPTS && seats.length >= SEATS_PER_BOOKING; attempt++) {
        const result = hold(token, data.performanceId, pickRandom(seats, SEATS_PER_BOOKING));
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
    return summary(data, 'hot');
}

/** 서로 다른 좌석 {@code count}개를 무작위 순서로 고른다. */
function pickRandom(seats, count) {
    const pool = [...seats];
    const picked = [];
    while (picked.length < count) {
        picked.push(pool.splice(Math.floor(Math.random() * pool.length), 1)[0]);
    }
    return picked;
}
