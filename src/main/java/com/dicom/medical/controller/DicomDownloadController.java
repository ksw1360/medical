package com.dicom.medical.controller;

import com.dicom.medical.service.DicomStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DICOM 파일 다운로드 — 원본 / AI 결과(SC) 둘 다 (path 로 구분).
 *   배치 위치: src/main/java/com/dicom/medical/controller/DicomDownloadController.java
 *
 *   - 원본 다운로드   : path = DicomImage.s3Key
 *   - AI 결과(SC) 다운: path = 추론 응답의 scFile (예: ai-sc/2.25....dcm)
 *
 *   /api/dicom/** 는 이미 SecurityConfig 에서 permitAll 이라 별도 설정 불필요.
 */
@RestController
@RequestMapping("/api/dicom")
@RequiredArgsConstructor
@Tag(name = "12. DICOM 다운로드", description = "원본/AI결과 .dcm 파일 다운로드")
public class DicomDownloadController {

    private final DicomStorageService storageService;

    /**
     * S3 프리사인드 GET URL 발급 — 큰 .dcm은 이 URL로 S3에서 직접 다운로드.
     * (Amplify/Lambda 프록시의 6MB 응답 한도 우회)
     * 예: GET /api/dicom/presign?path=ai-sc/2.25....dcm → {"url":"https://s3..."}
     */
    @GetMapping("/presign")
    @Operation(summary = "프리사인드 다운로드 URL 발급",
            description = "S3 key를 받아 1시간짜리 프리사인드 GET URL을 반환한다. 프론트는 이 URL로 S3에서 직접 받는다.")
    public ResponseEntity<java.util.Map<String, String>> presign(@RequestParam("path") String path) {
        String url = storageService.presignGetUrl(path, java.time.Duration.ofHours(1));
        return ResponseEntity.ok(java.util.Map.of("key", path, "url", url));
    }

    @GetMapping("/download")
    @Operation(summary = "DICOM 다운로드",
            description = "S3 key(원본 s3Key 또는 결과 scFile)를 받아 .dcm 파일을 첨부파일로 내려준다.")
    public ResponseEntity<byte[]> download(@RequestParam("path") String path) throws Exception {
        Path tmp = storageService.downloadToTemp(path);
        try {
            byte[] data = Files.readAllBytes(tmp);
            String filename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (!filename.toLowerCase().endsWith(".dcm")) filename += ".dcm";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/dicom"))
                    .body(data);
        }  catch (Exception e)
        {
            return ResponseEntity.notFound().build(); // Key 없음 404
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
