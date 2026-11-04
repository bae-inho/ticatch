package com.ticatch.global.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 응답(docs/api.md 1절). 스프링의 {@link Page}를 그대로 응답하지 않고 이 모양으로 옮긴다.
 * {@code Page}의 JSON 모양은 스프링 버전에 따라 바뀔 수 있다.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
