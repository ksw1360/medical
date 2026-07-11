package com.dicom.medical.controller;

import com.dicom.medical.service.DicomIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP DICOM 수신 — 압축 파일을 받아 임시 폴더에 풀고, 내부의 모든 .dcm 을 찾아 일괄 ingest.
 *   배치 위치: src/main/java/com/dicom/medical/controller/DicomZipUploadController.java
 *
 *   흐름: ZIP 업로드 → 임시폴더에 압축 해제(중첩 폴더 그대로) → .dcm 재귀 탐색 → 각각 비식별·저장 → 임시폴더 삭제
 *   보안: zip-slip(경로 탈출) 방어, 맥 쓰레기파일(__MACOSX/.DS_Store) 스킵.
 *
 *   ⚠️ /dicomweb/** 는 이미 permitAll 일 것(기존 /dicomweb/upload 동작함). 아니면 SecurityConfig 에 추가.
 *   ⚠️ 큰 ZIP 대비: application.yaml 의 multipart.max-file-size / max-request-size, nginx client_max_body_size 확인.
 */
@RestController
@RequestMapping("/dicomweb")
@RequiredArgsConstructor
@Tag(name = "01. DICOM 업로드·수신", description = "ZIP 업로드 → 압축 해제 → .dcm 일괄 ingest")
public class DicomZipUploadController {

    private final DicomIngestService ingestService;

    @PostMapping(value = "/upload-zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "ZIP DICOM 업로드",
            description = "ZIP 파일(form-data key=file)을 받아 임시 폴더에 압축 해제 후, 내부의 모든 .dcm 을 찾아 "
                    + "비식별·저장. 중첩 폴더 구조도 재귀 처리. 저장된 DicomImage id 목록을 반환.")
    public ResponseEntity<ZipUploadResult> uploadZip(
            @RequestParam("file") MultipartFile file) throws IOException {

        // 1) 특정(임시) 폴더에 압축 해제
        Path baseDir = Files.createTempDirectory("dicom-zip-");
        int skipped = 0;

        try {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
                ZipEntry entry;
                byte[] buf = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();

                    // 맥/숨김 쓰레기 스킵
                    if (name.startsWith("__MACOSX") || name.endsWith(".DS_Store") || name.contains("/.")) {
                        skipped++;
                        zis.closeEntry();
                        continue;
                    }

                    // zip-slip 방어: 압축 항목이 baseDir 밖으로 못 나가게
                    Path target = baseDir.resolve(name).normalize();
                    if (!target.startsWith(baseDir)) {
                        throw new IOException("잘못된 압축 경로(zip slip 의심): " + name);
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        try (OutputStream os = Files.newOutputStream(target)) {
                            int n;
                            while ((n = zis.read(buf)) != -1) os.write(buf, 0, n);
                        }
                    }
                    zis.closeEntry();
                }
            }

            // 2) 압축 해제된 폴더에서 .dcm 재귀 탐색 → ingest
            List<Long> ids = new ArrayList<>();
            int failed = 0;
            try (Stream<Path> walk = Files.walk(baseDir)) {
                List<Path> dcms = walk
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".dcm"))
                        .sorted()
                        .toList();

                for (Path dcm : dcms) {
                    try (InputStream in = Files.newInputStream(dcm)) {
                        ids.add(ingestService.ingest(in));      // 비식별 → S3/DB 저장
                    } catch (Exception e) {
                        failed++;
                        System.err.println("ingest 실패: " + dcm.getFileName() + " → " + e.getMessage());
                    }
                }
            }

            return ResponseEntity.ok(new ZipUploadResult(ids.size(), failed, skipped, ids));

        } finally {
            // 3) 임시 폴더 정리
            deleteRecursively(baseDir);
        }
    }

    /** 폴더 통째 삭제 (하위부터) */
    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    /** ingested=저장 성공 수, failed=ingest 실패 수, skipped=쓰레기파일 스킵 수, ids=저장된 DicomImage id */
    public record ZipUploadResult(int ingested, int failed, int skipped, List<Long> ids) {}
}
