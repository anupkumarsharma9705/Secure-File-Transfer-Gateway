package com.securetransfer.SFTG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class FileDetailsResponseDTO {

    private Long id;
    private String originalFilename;
    private Long fileSize;
    private Instant uploadDate;
    private Instant expiryAt;

    private boolean active;
    private int downloadLimit;
    private int downloadCount;

    private List<DownloadLogDTO> downloadLogs;
}
