package com.meridian.dashboard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * README §10 Dashboard API — 팀/제안 데이터를 조합해 조회하므로 자체 엔티티는 없다.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/timezones")
    public ResponseEntity<Void> timezones(@RequestParam Long teamId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/status")
    public ResponseEntity<Void> status(@RequestParam Long proposalId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
