package com.ticatch.concert.controller;

import com.ticatch.concert.controller.dto.AdminConcertDetailResponse;
import com.ticatch.concert.controller.dto.ConcertRequest;
import com.ticatch.concert.controller.dto.ConcertResponse;
import com.ticatch.concert.domain.ConcertStatus;
import com.ticatch.concert.service.ConcertService;
import com.ticatch.global.web.PageQuery;
import com.ticatch.global.web.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/concerts")
@RequiredArgsConstructor
public class AdminConcertController {

    private final ConcertService concertService;

    @PostMapping
    public ResponseEntity<ConcertResponse> create(@Valid @RequestBody ConcertRequest request) {
        ConcertResponse concert = ConcertResponse.from(concertService.create(request.title(), request.description()));
        return ResponseEntity.created(URI.create("/api/v1/admin/concerts/" + concert.concertId())).body(concert);
    }

    @GetMapping
    public PageResponse<ConcertResponse> list(
            @RequestParam(required = false) ConcertStatus status, @Valid PageQuery query) {
        return PageResponse.of(
                concertService.getConcerts(status, query.toPageable(Sort.by(Sort.Direction.DESC, "id"))),
                ConcertResponse::from);
    }

    @GetMapping("/{concertId}")
    public AdminConcertDetailResponse detail(@PathVariable Long concertId) {
        return AdminConcertDetailResponse.from(concertService.getDetail(concertId));
    }

    @PatchMapping("/{concertId}")
    public ConcertResponse update(@PathVariable Long concertId, @Valid @RequestBody ConcertRequest request) {
        return ConcertResponse.from(concertService.update(concertId, request.title(), request.description()));
    }

    @PostMapping("/{concertId}/open")
    public ResponseEntity<Void> open(@PathVariable Long concertId) {
        concertService.open(concertId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{concertId}/end")
    public ResponseEntity<Void> end(@PathVariable Long concertId) {
        concertService.end(concertId);
        return ResponseEntity.noContent().build();
    }
}
