package com.ticatch.point.service.dto;

import com.ticatch.point.domain.PointTransaction;
import com.ticatch.point.domain.PointTransactionType;
import java.time.Instant;

public record PointTransactionResult(
        Long id, PointTransactionType type, long amount, long balanceAfter, Instant createdAt) {

    public static PointTransactionResult from(PointTransaction transaction) {
        return new PointTransactionResult(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt());
    }
}
