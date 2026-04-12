package com.securetransfer.SFTG.controller;

import com.securetransfer.SFTG.dto.GlobalStatsDTO;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserActivityLogRepository userActivityLogRepository;
    private final UserRepository userRepository;

    @GetMapping("/global")
    public ResponseEntity<GlobalStatsDTO> getGlobalStats() {
        return ResponseEntity.ok(
                GlobalStatsDTO.builder()
                        .uniqueVisitors(userRepository.count())
                        .totalUploads(userActivityLogRepository.countTotalUploads())
                        .totalDownloads(userActivityLogRepository.countTotalDownloads())
                        .build()
        );
    }
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}