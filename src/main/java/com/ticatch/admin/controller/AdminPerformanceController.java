package com.ticatch.admin.controller;

import com.ticatch.admin.controller.dto.CancelPerformanceResponse;
import com.ticatch.admin.controller.dto.PerformanceSalesResponse;
import com.ticatch.admin.controller.dto.RegisterPerformanceRequest;
import com.ticatch.admin.controller.dto.RegisterPerformanceResponse;
import com.ticatch.admin.service.AdminPerformanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminPerformanceController {

    private final AdminPerformanceService adminPerformanceService;

    @PostMapping("/api/v1/admin/concerts/{concertId}/performances")
    public ResponseEntity<RegisterPerformanceResponse> register(
            @PathVariable Long concertId, @Valid @RequestBody RegisterPerformanceRequest request) {
        RegisterPerformanceResponse response =
                RegisterPerformanceResponse.from(adminPerformanceService.register(request.toCommand(concertId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/v1/admin/performances/{performanceId}/cancel")
    public CancelPerformanceResponse cancel(@PathVariable Long performanceId) {
        return CancelPerformanceResponse.from(adminPerformanceService.cancel(performanceId));
    }

    @GetMapping("/api/v1/admin/performances/{performanceId}/sales")
    public PerformanceSalesResponse sales(@PathVariable Long performanceId) {
        return PerformanceSalesResponse.from(adminPerformanceService.getSales(performanceId));
    }
}
