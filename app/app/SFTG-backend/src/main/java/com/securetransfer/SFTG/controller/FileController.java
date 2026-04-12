// src/main/java/com/example/sftg/controller/FileController.java
package com.securetransfer.SFTG.controller;

import com.securetransfer.SFTG.dto.FileDetailsResponseDTO;
import com.securetransfer.SFTG.dto.FileResponse;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<FileResponse> uploadFile(@RequestParam("file") MultipartFile file, Principal principal) {
        FileEntity fileEntity = fileService.storeFile(file, principal.getName()); // Get username from authenticated principal

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/files/download/")
                .path(fileEntity.getStoredFilename())
                .toUriString();

        FileResponse fileResponse = new FileResponse(
                fileEntity.getId(),
                fileEntity.getOriginalFilename(),
                fileEntity.getStoredFilename(),
                fileEntity.getMimeType(),
                fileEntity.getFileSize(),
                fileEntity.getUploadDate(),
                fileEntity.getUploadedBy().getEmail()
        );
        return ResponseEntity.ok(fileResponse);
    }

    @GetMapping("/download/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String storedFilename,
            Authentication authentication) {

        Resource resource = fileService.loadFileAsResource(
                storedFilename,
                authentication.getName()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
    @GetMapping("/my-files")
    public ResponseEntity<List<FileDetailsResponseDTO>> getMyFiles(
            Authentication authentication) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                fileService.getMyFilesWithDetails(email)
        );

    }
}