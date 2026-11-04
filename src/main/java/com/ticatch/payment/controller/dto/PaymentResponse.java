package com.ticatch.payment.controller.dto;

import com.ticatch.global.web.KoreaTime;
import com.ticatch.payment.domain.PaymentStatus;
import com.ticatch.payment.service.dto.PaymentResult;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long paymentId,
        Long reservationId,
        long amount,
        PaymentStatus status,
        OffsetDateTime paidAt,
        OffsetDateTime refundedAt,
        long pointBalance) {

    public static PaymentResponse from(PaymentResult payment) {
        return new PaymentResponse(
                payment.id(),
                payment.reservationId(),
                payment.amount(),
                payment.status(),
                KoreaTime.of(payment.paidAt()),
                KoreaTime.of(payment.refundedAt()),
                payment.pointBalance());
    }
}
