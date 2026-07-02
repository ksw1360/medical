package com.dicom.medical.controller;

import com.dicom.medical.dto.respond.AiResultResponse;
import com.dicom.medical.service.DicomStorageService;
import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.ScWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * SC(이미지)+SR(추론 텍스트) 결과창 통합 API.
 * 원본 DICOM(S3 key)을 받아 추론 → SC 생성/업로드 후,
 * SR 텍스트 + SC/원본 이미지 URL 을 한 응답으로 반환한다.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI 결과창", description = "SC(이미지)+SR(추론내용) 통합 결과 응답")
public class AiResultController {

    private final InferenceService service;
    private final ScWriter scWriter;
    private final DicomStorageService storageService;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");

    public AiResultController(InferenceService service, ScWriter scWriter,
                             DicomStorageService storageService) {
        this.service = service;
        this.scWriter = scWriter;
        this.storageService = storageService;
    }

    @PostMapping("/result")
    @Operation(summary = "SC+SR 통합 결과",
            description = "원본 DICOM(S3 key)을 추론하고 SC를 생성한 뒤, 추론 텍스트(SR)와 "
                    + "SC/원본 이미지 URL을 한 번에 반환. 프론트 결과창이 단일 호출로 렌더 가능.")
    public AiResultResponse result(@RequestBody ResultRequest req) throws Exception {
        String origKey = req.dicomPath();
        Path src = storageService.downloadToTemp(origKey);
        Path scDir = Files.createTempDirectory("ai-sc-");
        Path scLocal = null;
        try {
            InferenceService.InferenceResult r = service.infer(src, MODEL);

            boolean abnormal = r.abnormal() >= 0.5f;
            String findingEn = (abnormal ? "AI: Abnormal suspected" : "AI: Normal range")
                    + String.format(" (%.0f%%)", r.confidence() * 100);
            scLocal = scWriter.writeSc(src, findingEn, scDir);

            String scKey = "ai-sc/" + scLocal.getFileName();
            storageService.upload(scKey, scLocal, "application/dicom");

            AiResultResponse.Sr sr = new AiResultResponse.Sr(
                    r.label(), round(r.abnormal()), round(r.normal()),
                    round(r.confidence()), r.confidencePercent(),
                    abnormal ? "이상 의심" : "정상");
            AiResultResponse.Sc sc = new AiResultResponse.Sc(scKey, previewUrl(scKey));
            AiResultResponse.Original orig = new AiResultResponse.Original(origKey, previewUrl(origKey));

            return new AiResultResponse(sr, sc, orig);
        } finally {
            Files.deleteIfExists(src);
            if (scLocal != null) Files.deleteIfExists(scLocal);
            Files.deleteIfExists(scDir);
        }
    }

    private static String previewUrl(String key) {
        return "/api/ai/preview?path=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }
    private static float round(float v) { return Math.round(v * 1000) / 1000f; }

    public record ResultRequest(String dicomPath) {}
}
