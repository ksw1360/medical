package com.dicom.medical.controller;

import com.dicom.medical.repository.DicomImageRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 업로드된 DicomImage id → 추론에 쓸 S3 key 조회.
 * 프론트 흐름: 업로드(id) → 이 API(s3 key) → /api/ai/infer(s3 key)
 *
 * S3 전환: 더 이상 로컬 절대경로를 만들지 않는다. "path" 필드는 이제 S3 key 이고,
 *          추론/미리보기 컨트롤러가 이 key로 S3에서 직접 내려받는다.
 */
@RestController
@RequestMapping("/api/ai")
public class DicomPathController {

    private final DicomImageRepository imageRepository;

    public DicomPathController(DicomImageRepository imageRepository) {
        this.imageRepository = imageRepository;
    }

    /** GET /api/ai/path/{id} -> {"path": "<study>/<series>/<sop>.dcm"} (S3 key) */
    @GetMapping("/path/{id}")
    @Tag(name = "AI 파이프라인 · 경로", description = "업로드 영상 ID → S3 key 변환 (추론 입력용)")
    public ResponseEntity<?> path(@PathVariable Long id) {
        return imageRepository.findById(id)
                .map(img -> ResponseEntity.ok(Map.of(
                        "id", id,
                        "sopUid", img.getSopInstanceUid(),
                        "path", img.getS3Key()   // 이제 S3 key 그대로
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
