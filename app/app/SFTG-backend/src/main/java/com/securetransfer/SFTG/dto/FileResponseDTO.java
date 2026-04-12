package com.securetransfer.SFTG.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class FileResponseDTO {

    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private Instant uploadDate;
    private Instant expiryAt;

}
