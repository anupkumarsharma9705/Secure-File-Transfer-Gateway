package com.securetransfer.SFTG.controller;

import com.securetransfer.SFTG.dto.UserActivityLogDto;
import com.securetransfer.SFTG.dto.UserProfileDto;
import com.securetransfer.SFTG.exception.ResourceNotFoundException;
import com.securetransfer.SFTG.model.UserActivityLog;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final UserRepository userRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getProfile(Authentication authentication) {

        String email = authentication.getName();

        // FIXED: was missing .orElseThrow() — would have been a compile error
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));

        List<UserActivityLog> logs =
                userActivityLogRepository.findAllByUserEmailOrderByTimestampDesc(email);

        List<UserActivityLogDto> logDtos = logs.stream()
                .map(log -> UserActivityLogDto.builder()
                        .action(log.getAction())
                        .timestamp(log.getTimestamp())
                        .metadata(log.getMetadata())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .build())
                .toList();

        UserProfileDto response = UserProfileDto.builder()
                .email(user.getEmail())
                .role(user.getRoles().iterator().next().name())
                .createdAt(user.getCreatedAt())   // ← now included
                .logs(logDtos)
                .build();

        return ResponseEntity.ok(response);
    }
}