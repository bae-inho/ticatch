package com.ticatch.payment.controller;

import com.ticatch.global.web.IdempotencyKeys;
import com.ticatch.payment.controller.dto.PayRequest;
import com.ticatch.payment.controller.dto.PaymentResponse;
import com.ticatch.payment.service.PaymentService;
import com.ticatch.payment.service.dto.PayCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** 같은 키로 다시 보내면 처음과 같은 201 응답을 준다. */
    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(name = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        PayCommand command =
                new PayCommand(userId(jwt), request.reservationId(), IdempotencyKeys.require(idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(paymentService.pay(command)));
    }

    @PostMapping("/{paymentId}/refund")
    public PaymentResponse refund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long paymentId) {
        return PaymentResponse.from(paymentService.refund(userId(jwt), paymentId));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
