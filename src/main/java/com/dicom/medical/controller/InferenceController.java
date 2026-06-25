package com.dicom.medical.controller;

import com.dicom.medical.service.InferenceService;
import com.dicom.medical.service.ScWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * ③추론 + ④후처리 + ⑤회신 API.
 * 추론 → 소견 도출 → 소견 번인한 SC 이미지를 새 UID로 저장.
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private final ScWriter scWriter;
    private static final Path MODEL  = Path.of("models/chest_classifier.onnx");
    private static final Path SC_DIR = Path.of("dicom-store/ai-sc");  // SC 저장 폴더

    InferenceController(InferenceService s, ScWriter w) { this.service = s; this.scWriter = w; }

    @PostMapping("/infer")
    @Tag(name = "AI 추론", description = "ONNX 모델 기반 흉부 영상 정상/비정상 분류")
    @Operation(summary = "AI 추론 실행",
            description = "전처리 → ONNX 추론 → 후처리 파이프라인 실행 후 정상/비정상 확률, 판정 라벨, 신뢰도를 반환.")
    public Response infer(@RequestBody InferRequest req) throws Exception {
        Path src = Path.of(req.dicomPath());

        // ③④ 추론 + 후처리
        InferenceService.InferenceResult r = service.infer(src, MODEL);
        //Result r = service.infer(src, MODEL);

        // ⑤ 회신: 소견(영문) 번인한 SC 저장 — 새 SOP UID, 같은 Study UID
        String findingEn = (r.abnormal() >= 0.5f ? "AI: Abnormal suspected" : "AI: Normal range")
                //+ String.format(" (%d%%)", r.confidence());
                + String.format(" (%.0f%%)", r.confidence() * 100);
        Path scFile = scWriter.writeSc(src, findingEn, SC_DIR);

        return new Response(r, scFile.toString());
    }

    record InferRequest(String dicomPath) {}
    record Response(InferenceService.InferenceResult result , String scFile) {}
}
