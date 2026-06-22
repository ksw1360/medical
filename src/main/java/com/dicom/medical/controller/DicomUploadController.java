package com.dicom.medical.controller;

import com.dicom.medical.service.DicomIngestService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.dcm4che3.mime.MultipartParser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/dicomweb")
@RequiredArgsConstructor
public class DicomUploadController {

    private final DicomIngestService ingestService;

    /* ── 1) 내부/브라우저 업로드: multipart/form-data ──
       form-data 의 key 는 "files" (단건도 리스트로 받힘) */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<Long>> upload(
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        List<Long> ids = new ArrayList<>();
        for (MultipartFile file : files) {
            try (InputStream in = file.getInputStream()) {
                ids.add(ingestService.ingest(in));      // 저장 → DicomImage id
            }
        }
        return ResponseEntity.ok(ids);                  // [1, 2, 3 ...]
    }

    /* ── 2) DICOMweb STOW-RS: multipart/related; type="application/dicom" ──
       Spring 기본 MultipartResolver 가 multipart/related 를 안 풀어줘서
       dcm4che MultipartParser 로 직접 파싱 */
    @PostMapping(value = "/studies",
            consumes = "multipart/related",
            produces = "application/dicom+json")
    public ResponseEntity<List<Long>> stow(HttpServletRequest request) throws Exception {

        String boundary = boundaryOf(request.getContentType());
        List<Long> ids = new ArrayList<>();

        new MultipartParser(boundary).parse(request.getInputStream(),
                (partNumber, part) -> {
                    part.readHeaderParams();                // ⚠ 파트 헤더 먼저 소비 (안 하면 DICOM에 섞임)
                    ids.add(ingestService.ingest(part));    // MultipartInputStream 그대로 ingest
                });

        // MVP: 저장된 id 리스트. 표준 응답(DICOM JSON 성공/실패 시퀀스)은 나중에 교체
        return ResponseEntity.ok(ids);
    }

    /* Content-Type 헤더에서 boundary=xxxx 추출 */
    private static String boundaryOf(String contentType) {
        for (String p : contentType.split(";")) {
            p = p.trim();
            if (p.startsWith("boundary="))
                return p.substring("boundary=".length()).replace("\"", "");
        }
        throw new IllegalArgumentException("boundary 없음: " + contentType);
    }
}