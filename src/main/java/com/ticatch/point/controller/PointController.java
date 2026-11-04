package com.ticatch.point.controller;

import com.ticatch.global.web.IdempotencyKeys;
import com.ticatch.global.web.PageQuery;
import com.ticatch.global.web.PageResponse;
import com.ticatch.point.controller.dto.ChargePointRequest;
import com.ticatch.point.controller.dto.PointBalanceResponse;
import com.ticatch.point.controller.dto.PointTransactionResponse;
import com.ticatch.point.service.PointService;
import com.ticatch.point.service.dto.ChargePointCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping
    public PointBalanceResponse balance(@AuthenticationPrincipal Jwt jwt) {
        return new PointBalanceResponse(pointService.getBalance(userId(jwt)));
    }

    /** 같은 키로 다시 보내면 처음과 같은 201 응답을 준다. */
    @PostMapping("/charges")
    public ResponseEntity<PointTransactionResponse> charge(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody ChargePointRequest request) {
        ChargePointCommand command =
                new ChargePointCommand(userId(jwt), request.amount(), IdempotencyKeys.require(idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PointTransactionResponse.from(pointService.charge(command)));
    }

    @GetMapping("/transactions")
    public PageResponse<PointTransactionResponse> transactions(
            @AuthenticationPrincipal Jwt jwt, @Valid PageQuery query) {
        return PageResponse.of(
                pointService.getTransactions(userId(jwt), query.toPageable(Sort.by(Sort.Direction.DESC, "id"))),
                PointTransactionResponse::from);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
