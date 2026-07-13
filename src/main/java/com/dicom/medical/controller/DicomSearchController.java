package com.dicom.medical.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S3 폴더(경로) 기반 검색 — 강사 mock 데이터 구조 활용.
 * 경로 예: STS01/201702/02/0041698/CT/SC.1.2.410....dcm
 *          데이터셋 / 연월 / .. / 환자 / 모달리티 / 파일
 * DB 검색(/api/studies)과 별개로, 폴더명만으로 빠르게 거르는 데모용.
 */
@Tag(name = "07. 검사 조회·검색", description = "S3 폴더(연월/모달리티) 경로 기반 검색")
@RestController
@RequestMapping("/api/dicom")
public class DicomSearchController {

    private final S3Client s3;
    private final String bucket;

    public DicomSearchController(
            S3Client s3,
            @Value("${dicom.storage.s3-bucket:medical-dicom-store}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @GetMapping("/search")
    @Operation(summary = "연월·모달리티 폴더 검색",
            description = "S3 key 경로에 연월(yearMonth)·모달리티가 포함된 .dcm을 찾아 목록 반환. "
                    + "previewUrl로 바로 미리보기, key로 추론(/api/ai/infer) 호출 가능.")
    public List<Map<String, String>> search(
            @Parameter(description = "연월 (예: 201702)", example = "201702")
            @RequestParam String yearMonth,

            @Parameter(description = "모달리티 폴더 (CT/SC/PR 등, 선택)", example = "CT")
            @RequestParam(required = false) String modality,

            @Parameter(description = "탐색 시작 prefix (선택, 예: STS01/)", example = "STS01/")
            @RequestParam(required = false, defaultValue = "") String prefix) {

        List<Map<String, String>> result = new ArrayList<>();
        String token = null;

        do {
            ListObjectsV2Request.Builder req = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)          // 비우면 버킷 전체
                    .continuationToken(token);

            var resp = s3.listObjectsV2(req.build());
            resp.contents().forEach(o -> {
                String key = o.key();
                if (!key.endsWith(".dcm")) return;
                // 경로 세그먼트로 연월·모달리티 매칭
                boolean dateOk = key.contains("/" + yearMonth + "/") || key.startsWith(yearMonth + "/");
                boolean modOk  = (modality == null || modality.isBlank())
                        || key.contains("/" + modality + "/");
                if (dateOk && modOk) {
                    result.add(Map.of(
                            "key", key,
                            "previewUrl", "/api/ai/preview?path=" + key));
                }
            });
            token = Boolean.TRUE.equals(resp.isTruncated()) ? resp.nextContinuationToken() : null;
        } while (token != null);

        return result;
    }
}
