package com.meridian.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * README §10 Dashboard API — 팀/제안 데이터를 조합해 조회하므로 자체 엔티티는 없다.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/timezones")
    public ResponseEntity<DashboardTimezonesResponse> timezones(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam Long teamId
    ) {
        return ResponseEntity.ok(dashboardService.timezones(authorizationHeader, teamId));
    }

    @GetMapping("/status")
    public ResponseEntity<DashboardStatusResponse> status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam Long proposalId
    ) {
        return ResponseEntity.ok(dashboardService.status(authorizationHeader, proposalId));
    }
}
