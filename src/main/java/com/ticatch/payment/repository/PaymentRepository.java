package com.ticatch.payment.repository;

import com.ticatch.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /** 회원 본인의 결제를 잠그고 읽는다. 같은 결제의 환불 요청이 동시에 와도 한 번만 환불된다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByIdAndUserId(Long id, Long userId);
}
