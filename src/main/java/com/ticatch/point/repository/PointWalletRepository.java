package com.ticatch.point.repository;

import com.ticatch.point.domain.PointWallet;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointWalletRepository extends JpaRepository<PointWallet, Long> {
}
