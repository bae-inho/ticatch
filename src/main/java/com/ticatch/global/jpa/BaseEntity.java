package com.ticatch.global.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/** 바뀔 수 있는 행의 엔티티가 상속한다. {@code created_at}과 {@code updated_at}을 자동으로 채운다. */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
