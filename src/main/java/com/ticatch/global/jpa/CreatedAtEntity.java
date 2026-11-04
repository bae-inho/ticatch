package com.ticatch.global.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 만든 뒤 바뀌지 않는 행의 엔티티가 상속한다. {@code created_at}을 저장할 때 자동으로 채운다. */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class CreatedAtEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
