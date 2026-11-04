package com.ticatch.point.controller.dto;

import com.ticatch.global.web.KoreaTime;
import com.ticatch.point.domain.PointTransactionType;
import com.ticatch.point.service.dto.PointTransactionResult;
import java.time.OffsetDateTime;

/** 거래 하나. 충전 응답에서는 {@code balanceAfter}가 충전 후 잔액이다. */
public record PointTransactionResponse(
        Long transactionId, PointTransactionType type, long amount, long balanceAfter, OffsetDateTime createdAt) {

    public static PointTransactionResponse from(PointTransactionResult transaction) {
        return new PointTransactionResponse(
                transaction.id(),
                transaction.type(),
                transaction.amount(),
                transaction.balanceAfter(),
                KoreaTime.of(transaction.createdAt()));
    }
}
