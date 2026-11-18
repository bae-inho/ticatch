package com.ticatch.payment.repository.projection;

/** 회차 취소로 환불할 결제 한 건. 지갑을 돌려줄 때 필요한 값만 담는다. */
public record RefundTargetProjection(Long paymentId, Long userId, long amount) {
}
