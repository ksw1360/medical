package com.dicom.medical.controller;

import com.dicom.medical.repository.DicomImageRepository;
import com.dicom.medical.service.DicomStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 업로드된 DicomImage id → 추론에 쓸 실제 파일 경로 조회.
 * 프론트 흐름: 업로드(id) → 이 API(경로) → /api/ai/infer(경로)
 */
@RestController
@RequestMapping("/api/ai")
public class DicomPathController {

    private final DicomImageRepository imageRepository;
    private final DicomStorageService storageService;

    public DicomPathController(DicomImageRepository imageRepository,
                               DicomStorageService storageService) {
        this.imageRepository = imageRepository;
        this.storageService = storageService;
    }

    /** GET /api/ai/path/{id} → {"path": "dicom-store/.../xxx.dcm"} */
    @GetMapping("/path/{id}")
    public ResponseEntity<?> path(@PathVariable Long id) {
        return imageRepository.findById(id)
                .map(img -> {
                    String fullPath = storageService.resolve(img.getS3Key()).toString();
                    return ResponseEntity.ok(Map.of(
                            "id", id,
                            "sopUid", img.getSopInstanceUid(),
                            "path", fullPath
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}