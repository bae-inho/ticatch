package com.ticatch.payment.service.dto;

import com.ticatch.payment.domain.Payment;
import com.ticatch.payment.domain.PaymentStatus;
import java.time.Instant;

/** @param pointBalance 이 결제(또는 환불)를 반영한 직후의 포인트 잔액 */
public record PaymentResult(
        Long id,
        Long reservationId,
        long amount,
        PaymentStatus status,
        Instant paidAt,
        Instant refundedAt,
        long pointBalance) {

    public static PaymentResult of(Payment payment, long pointBalance) {
        return new PaymentResult(
                payment.getId(),
                payment.getReservationId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getRefundedAt(),
                pointBalance);
    }
}
