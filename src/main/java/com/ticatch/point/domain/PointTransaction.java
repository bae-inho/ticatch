package com.ticatch.point.domain;

import com.ticatch.global.jpa.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잔액이 바뀐 기록 하나. 만든 뒤 바뀌지 않는다.
 *
 * <p>지갑 애그리거트에 속하지만 지갑이 목록으로 들고 있지 않는다. 거래는 계속 쌓이므로,
 * 지갑을 읽을 때마다 거래 전체를 함께 읽게 되는 것을 피하려고 자기 리포지토리로 저장·조회한다.
 */
@Entity
@Table(name = "point_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointTransaction extends CreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PointTransactionType type;

    @Column(nullable = false, updatable = false)
    private long amount;

    @Column(nullable = false, updatable = false)
    private long balanceAfter;

    @Column(updatable = false)
    private Long paymentId;

    @Column(updatable = false)
    private String idempotencyKey;

    public static PointTransaction charge(Long walletId, long amount, long balanceAfter, String idempotencyKey) {
        PointTransaction transaction = new PointTransaction();
        transaction.walletId = walletId;
        transaction.type = PointTransactionType.CHARGE;
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.idempotencyKey = idempotencyKey;
        return transaction;
    }

    /** 결제로 차감한 기록. 멱등성 키는 결제가 갖는다. */
    public static PointTransaction pay(Long walletId, long amount, long balanceAfter, Long paymentId) {
        return ofPayment(PointTransactionType.PAY, walletId, amount, balanceAfter, paymentId);
    }

    /** 환불로 돌려준 기록. */
    public static PointTransaction refund(Long walletId, long amount, long balanceAfter, Long paymentId) {
        return ofPayment(PointTransactionType.REFUND, walletId, amount, balanceAfter, paymentId);
    }

    private static PointTransaction ofPayment(
            PointTransactionType type, Long walletId, long amount, long balanceAfter, Long paymentId) {
        PointTransaction transaction = new PointTransaction();
        transaction.walletId = walletId;
        transaction.type = type;
        transaction.amount = amount;
        transaction.balanceAfter = balanceAfter;
        transaction.paymentId = paymentId;
        return transaction;
    }

    /** 같은 멱등성 키로 다시 온 충전 요청이 처음 요청과 같은가. */
    public boolean isSameChargeAs(Long walletId, long amount) {
        return type == PointTransactionType.CHARGE && this.walletId.equals(walletId) && this.amount == amount;
    }
}
