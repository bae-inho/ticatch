package com.ticatch.point.domain;

import com.ticatch.global.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원 한 명의 포인트 잔액. 회원 가입 때 잔액 0으로 만든다. */
@Entity
@Table(name = "point_wallet")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private long balance;

    public static PointWallet open(Long userId) {
        PointWallet wallet = new PointWallet();
        wallet.userId = userId;
        wallet.balance = 0;
        return wallet;
    }

    /** 잔액을 늘리고 늘어난 잔액을 돌려준다. 동시에 바뀌지 않도록 지갑 행을 잠근 상태에서 부른다. */
    public long charge(long amount) {
        balance += amount;
        return balance;
    }
}
