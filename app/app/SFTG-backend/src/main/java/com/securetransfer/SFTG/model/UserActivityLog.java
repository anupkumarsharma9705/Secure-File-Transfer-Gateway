package com.securetransfer.SFTG.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Why String? -> avoids lazy loading of User entity for audit reads
    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String action;
    // Examples:
    // LOGIN
    // LOGOUT
    // FILE_UPLOAD
    // FILE_DOWNLOAD
    // LINK_GENERATE

    @Column(nullable = false)
    private Instant timestamp;

    private String metadata;
    // Optional extra info like filename
    @Column(length = 100)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;
}