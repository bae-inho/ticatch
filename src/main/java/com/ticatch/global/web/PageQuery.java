package com.ticatch.global.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 목록 API의 {@code ?page=0&size=20} 파라미터(docs/api.md 1절). 컨트롤러에서 {@code @Valid PageQuery query}로 받는다.
 *
 * <p>정렬은 클라이언트가 고르지 못하게 하고 API마다 서버가 정한다. 아무 컬럼으로나 정렬하게 두면 인덱스가 없는 정렬이 생긴다.
 */
public record PageQuery(
        @Min(value = 0, message = "page는 0 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "size는 1 이상이어야 합니다.")
        @Max(value = 100, message = "size는 100 이하여야 합니다.") Integer size) {

    public PageQuery {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }
}
