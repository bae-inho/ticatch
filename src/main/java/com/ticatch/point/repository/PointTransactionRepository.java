package com.ticatch.point.repository;

import com.ticatch.point.domain.PointTransaction;
import com.ticatch.point.domain.PointTransactionType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {

    Optional<PointTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<PointTransaction> findAllByWalletId(Long walletId, Pageable pageable);

    Optional<PointTransaction> findByPaymentIdAndType(Long paymentId, PointTransactionType type);
}
