package com.securetransfer.SFTG.repository;

import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {
    Optional<SharedLink> findByStoredFilename(String storedFilename);
    Optional<SharedLink> findByTokenAndActiveTrue(String token);
    List<SharedLink> findAllByFile(FileEntity file);
    void deleteByFile(FileEntity file);
}
