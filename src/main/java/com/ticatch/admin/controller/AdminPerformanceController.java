package com.ticatch.admin.controller;

import com.ticatch.admin.controller.dto.RegisterPerformanceRequest;
import com.ticatch.admin.controller.dto.RegisterPerformanceResponse;
import com.ticatch.admin.service.AdminPerformanceService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.created(URI.create("/api/v1/admin/performances/" + response.performanceId()))
                .body(response);
    }
}
