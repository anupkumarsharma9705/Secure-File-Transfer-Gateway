package com.securetransfer.SFTG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLogDto {
    private String action;       // LOGIN, LOGOUT, FILE_DOWNLOAD
    private Instant timestamp;
    private String metadata;     // filename (optional)
    private String ipAddress;
    private String userAgent;
}