package com.ticatch.payment.domain;

import com.ticatch.global.exception.BusinessException;
import com.ticatch.global.jpa.BaseEntity;
import com.ticatch.payment.exception.PaymentErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 예매 하나의 포인트 결제. 예매당 하나이고, 전액 환불만 한다(docs/domain.md 2절). */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long reservationId;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant paidAt;

    private Instant refundedAt;

    public static Payment pay(Long reservationId, Long userId, long amount, String idempotencyKey, Instant now) {
        Payment payment = new Payment();
        payment.reservationId = reservationId;
        payment.userId = userId;
        payment.amount = amount;
        payment.status = PaymentStatus.PAID;
        payment.idempotencyKey = idempotencyKey;
        payment.paidAt = now;
        return payment;
    }

    public void refund(Instant now) {
        if (status == PaymentStatus.REFUNDED) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_ALREADY_REFUNDED);
        }
        status = PaymentStatus.REFUNDED;
        refundedAt = now;
    }

    /** 같은 멱등성 키로 다시 온 결제 요청이 처음 요청과 같은가. */
    public boolean isSameRequestAs(Long userId, Long reservationId) {
        return this.userId.equals(userId) && this.reservationId.equals(reservationId);
    }
}
