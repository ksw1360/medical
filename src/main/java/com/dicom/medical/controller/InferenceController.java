package com.dicom.medical.controller;

import com.dicom.medical.service.InferenceService;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

/**
 * ③ 추론 API — 강의 04절 InferenceController.
 * 지금은 테스트하기 쉽게 .dcm 파일 경로를 직접 받는다.
 * (다음 단계 ⑤회신에서 sopInstanceUid → 저장경로 조회로 바꿀 예정)
 */
@RestController
@RequestMapping("/api/ai")
public class InferenceController {

    private final InferenceService service;
    private static final Path MODEL = Path.of("models/chest_classifier.onnx");

    InferenceController(InferenceService s) { this.service = s; }

    @PostMapping("/infer")
    public InferenceService.Result infer(@RequestBody InferRequest req) throws Exception {
        return service.infer(Path.of(req.dicomPath()), MODEL);
    }

    record InferRequest(String dicomPath) {}
}
