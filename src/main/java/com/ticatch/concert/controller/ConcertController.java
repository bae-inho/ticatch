package com.ticatch.concert.controller;

import com.ticatch.concert.controller.dto.ConcertDetailResponse;
import com.ticatch.concert.controller.dto.ConcertResponse;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.global.web.PageQuery;
import com.ticatch.global.web.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관람객용 공연 조회. 로그인 없이 부를 수 있다. */
@RestController
@RequestMapping("/api/v1/concerts")
@RequiredArgsConstructor
public class ConcertController {

    private final ConcertService concertService;

    @GetMapping
    public PageResponse<ConcertResponse> list(@Valid PageQuery query) {
        return PageResponse.of(
                concertService.getOpenConcerts(query.toPageable(Sort.by(Sort.Direction.DESC, "id"))),
                ConcertResponse::from);
    }

    @GetMapping("/{concertId}")
    public ConcertDetailResponse detail(@PathVariable Long concertId) {
        return ConcertDetailResponse.from(concertService.getOpenConcertDetail(concertId));
    }
}
