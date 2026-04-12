package com.securetransfer.SFTG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private String email;
    private String role;
    private Instant createdAt;       // ← added: "Member since"
    private List<UserActivityLogDto> logs;
}