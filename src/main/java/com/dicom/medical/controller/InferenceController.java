package com.dicom.medical.controller;

import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.ScWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ③추론 + ④후처리 + ⑤회신 API.
 * 추론 → 소견 도출 → 소견 번인한 SC 이미지를 새 UID로 저장.
 *
 * S3 전환:
 *   - dicomPath 는 이제 S3 key. 추론 전에 임시파일로 다운로드.
 *   - SC 결과도 로컬이 아니라 S3(ai-sc/...)에 업로드하고, 응답엔 SC의 S3 key 를 준다.
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");

    InferenceController(InferenceService s, ScWriter w, DicomStorageService storage) {
        this.service = s;
        this.scWriter = w;
        this.storageService = storage;
    }

    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 정상/비정상 분류")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 → ONNX 추론 → 후처리 파이프라인 실행 후 정상/비정상 확률, 판정 라벨, 신뢰도를 반환.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        // S3 key → 임시 파일
        Path src = storageService.downloadToTemp(req.dicomPath());
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            // ③④ 추론 + 후처리
            InferenceService.InferenceResult r = service.infer(src, MODEL);

            // ⑤ 회신: 소견(영문) 번인한 SC 저장 — 새 SOP UID, 같은 Study UID
            String findingEn = (r.abnormal() >= 0.5f ? "AI: Abnormal suspected" : "AI: Normal range")
                    + String.format(" (%.0f%%)", r.confidence() * 100);
            scLocal = scWriter.writeSc(src, findingEn, scDir);

            // SC 를 S3 에 업로드 → key 는 ai-sc/<newSop>.dcm
            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");

            return new Response(r, scKey);   // 프론트엔 SC 의 S3 key 반환
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    record InferRequest(String dicomPath) {}   // dicomPath = S3 key
    record Response(InferenceService.InferenceResult result, String scFile) {}  // scFile = S3 key
}
