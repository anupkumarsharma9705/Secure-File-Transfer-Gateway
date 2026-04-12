package com.securetransfer.SFTG.repository;

import com.securetransfer.SFTG.model.UserActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserActivityLogRepository
        extends JpaRepository<UserActivityLog, Long> {

    List<UserActivityLog> findByUserEmailOrderByTimestampDesc(String email);
    List<UserActivityLog> findAllByUserEmailOrderByTimestampDesc(String userEmail);
    // Count distinct IP addresses across all activity (unique visitors)
    @Query("SELECT COUNT(DISTINCT u.ipAddress) FROM UserActivityLog u WHERE u.ipAddress IS NOT NULL")
    long countDistinctIpAddresses();

    // Count all FILE_UPLOAD actions globally
    @Query("SELECT COUNT(u) FROM UserActivityLog u WHERE u.action = 'FILE_UPLOAD'")
    long countTotalUploads();

    // Count all FILE_DOWNLOAD actions globally
    @Query("SELECT COUNT(u) FROM UserActivityLog u WHERE u.action = 'FILE_DOWNLOAD'")
    long countTotalDownloads();

}