package com.ticatch.point.service;

import com.ticatch.point.domain.PointWallet;
import com.ticatch.point.repository.PointWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointService {

    private final PointWalletRepository pointWalletRepository;

    /** 새 회원의 지갑을 잔액 0으로 만든다. 회원 가입과 같은 트랜잭션에서 부른다. */
    @Transactional
    public void openWallet(Long userId) {
        pointWalletRepository.save(PointWallet.open(userId));
    }
}
